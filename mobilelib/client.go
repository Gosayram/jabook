
package mobilelib

import (
	"bufio"
	"bytes"
	"compress/gzip"
	"context"
	"crypto/tls"
	"errors"
	"io"
	"net"
	"net/http"
	"net/http/cookiejar"
	"net/textproto"
	"net/url"
	"strings"
	"time"

	"golang.org/x/net/http2"
)

type Client struct {
	http    *http.Client
	jar     *cookiejar.Jar
	ua      string
	host    string // rutracker.me
	mirrors []string // list of mirror URLs
	currentMirror int // current mirror index for rotation
}

// MirrorConfig holds configuration for mirror selection
type MirrorConfig struct {
	PrimaryHost   string
	MirrorHosts   []string
	MaxRetries    int
	Timeout       time.Duration
}

// NewClientWithMirrors creates a new HTTP client with mirror support
func NewClientWithMirrors(config MirrorConfig, userAgent string) (*Client, error) {
	jar, _ := cookiejar.New(nil)

	// Transport "like a browser": HTTP/2, timeouts, TLS by default
	transport := &http.Transport{
		Proxy: http.ProxyFromEnvironment,
		DialContext: (&net.Dialer{
			Timeout:   10 * time.Second,
			KeepAlive: 60 * time.Second,
		}).DialContext,
		ForceAttemptHTTP2: true,
		TLSClientConfig:   &tls.Config{MinVersion: tls.VersionTLS12},
		IdleConnTimeout:   90 * time.Second,
	}

	// Enable HTTP/2 if available
	_ = http2.ConfigureTransport(transport)

	// Prepare mirror list (primary host first)
	mirrors := []string{config.PrimaryHost}
	mirrors = append(mirrors, config.MirrorHosts...)

	c := &Client{
		http: &http.Client{
			Transport: transport,
			Jar:       jar,
			Timeout:   config.Timeout,
		},
		jar:     jar,
		ua:      userAgent,
		host:    config.PrimaryHost,
		mirrors: mirrors,
		currentMirror: 0,
	}
	return c, nil
}

// NewClient creates a simple client without mirror support (backward compatibility)
func NewClient(host string, userAgent string) (*Client, error) {
	config := MirrorConfig{
		PrimaryHost: host,
		MirrorHosts: []string{},
		MaxRetries:  3,
		Timeout:     30 * time.Second,
	}
	return NewClientWithMirrors(config, userAgent)
}

// rotateMirror switches to the next available mirror
func (c *Client) rotateMirror() {
	if len(c.mirrors) <= 1 {
		return // no mirrors to rotate to
	}
	c.currentMirror = (c.currentMirror + 1) % len(c.mirrors)
	c.host = c.mirrors[c.currentMirror]
}

// getCurrentHost returns the current host being used
func (c *Client) getCurrentHost() string {
	return c.host
}

// getMirrorCount returns the total number of available mirrors
func (c *Client) getMirrorCount() int {
	return len(c.mirrors)
}

// SetCookieString sets cookies from Android CookieManager string: "k1=v1; k2=v2"
func (c *Client) SetCookieString(cookieStr string, scheme string) error {
	if c == nil || c.jar == nil {
		return errors.New("jar is nil")
	}
	u := &url.URL{Scheme: scheme, Host: c.host}
	// Convert string to http.Cookie[]
	cookies := parseCookieHeader(cookieStr)
	if len(cookies) == 0 {
		return errors.New("no cookies parsed")
	}
	c.jar.SetCookies(u, cookies)
	return nil
}

// GetText performs HTTP GET with automatic mirror rotation and retry logic
func (c *Client) GetText(ctx context.Context, path string) (string, error) {
	if !strings.HasPrefix(path, "/") {
		path = "/" + path
	}

	var lastErr error
	maxAttempts := 3 // attempts per mirror + mirror rotations

	for attempt := 0; attempt < maxAttempts; attempt++ {
		// If we've tried all mirrors, reset to primary
		if attempt > 0 && attempt%len(c.mirrors) == 0 {
			c.rotateMirror()
		}

		// Create request for current host
		url := "https://" + c.host + path
		req, _ := http.NewRequestWithContext(ctx, "GET", url, nil)
		req.Header.Set("User-Agent", c.ua)
		req.Header.Set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
		req.Header.Set("Accept-Encoding", "gzip")
		req.Header.Set("Accept-Language", "en-US,en;q=0.9")

		resp, err := c.http.Do(req)
		if err != nil {
			lastErr = err
			// If connection failed, try next mirror immediately
			if strings.Contains(err.Error(), "connection refused") ||
			   strings.Contains(err.Error(), "timeout") ||
			   strings.Contains(err.Error(), "no such host") {
				c.rotateMirror()
				continue
			}
			time.Sleep(time.Duration(attempt+1) * 500 * time.Millisecond)
			continue
		}
		defer resp.Body.Close()

		// Handle HTTP errors
		if resp.StatusCode < 200 || resp.StatusCode >= 300 {
			body, _ := io.ReadAll(io.LimitReader(resp.Body, 64<<10))
			errMsg := resp.Status + " body: " + string(body)
			
			// If we get a server error, try next mirror
			if resp.StatusCode >= 500 {
				c.rotateMirror()
				lastErr = errors.New(errMsg)
				continue
			}
			
			// For client errors (4xx), don't retry with different mirror
			return "", errors.New(errMsg)
		}
		
		// Process successful response
		var reader io.Reader = resp.Body
		if strings.EqualFold(resp.Header.Get("Content-Encoding"), "gzip") {
			gz, err := gzip.NewReader(resp.Body)
			if err != nil {
				return "", err
			}
			defer gz.Close()
			reader = gz
		}
		
		b, err := io.ReadAll(reader)
		if err != nil {
			return "", err
		}
		
		return string(b), nil
	}
	
	return "", errors.New("all mirrors failed, last error: " + lastErr.Error())
}

// parseCookieHeader parses cookie header string into http.Cookie array
func parseCookieHeader(h string) []*http.Cookie {
	h = strings.TrimSpace(h)
	if h == "" {
		return nil
	}
	// Try to parse "k=v; k2=v2" → line by line as Set-Cookie.
	// For simplicity: split by ';', then collect k=v pairs.
	var cookies []*http.Cookie
	parts := strings.Split(h, ";")
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p == "" || !strings.Contains(p, "=") {
			continue
		}
		kv := strings.SplitN(p, "=", 2)
		name := strings.TrimSpace(kv[0])
		val := strings.TrimSpace(kv[1])
		if name == "" {
			continue
		}
		cookies = append(cookies, &http.Cookie{
			Name:  name,
			Value: val,
		})
	}
	return cookies
}

// parseRawHeaders converts raw headers to map (if needed)
func parseRawHeaders(raw string) (http.Header, error) {
	tp := textproto.NewReader(bufio.NewReader(bytes.NewBufferString(raw)))
	mimeHeader, err := tp.ReadMIMEHeader()
	if err != nil {
		return nil, err
	}
	h := http.Header{}
	for k, v := range mimeHeader {
		for _, vv := range v {
			h.Add(k, vv)
		}
	}
	return h, nil
}

// Exported functions convenient for Flutter (if needed statically, not through object):
var defaultClient *Client

// InitWithMirrors initializes the default client with mirror support
func InitWithMirrors(primaryHost string, mirrorHosts []string, ua string) error {
	config := MirrorConfig{
		PrimaryHost:   primaryHost,
		MirrorHosts:   mirrorHosts,
		MaxRetries:    3,
		Timeout:       30 * time.Second,
	}
	c, err := NewClientWithMirrors(config, ua)
	if err != nil {
		return err
	}
	defaultClient = c
	return nil
}

// Init initializes the default client (backward compatibility)
func Init(host, ua string) error {
	return InitWithMirrors(host, []string{}, ua)
}

// SetCookies sets cookies from Android CookieManager
func SetCookies(cookies, scheme string) error {
	if defaultClient == nil {
		return ErrNotInit
	}
	return defaultClient.SetCookieString(cookies, scheme)
}

// GetHTML performs HTTP GET with automatic mirror rotation
func GetHTML(path string) (string, error) {
	if defaultClient == nil {
		return "", ErrNotInit
	}
	return defaultClient.GetText(context.Background(), path)
}

// GetCurrentHost returns the current host being used
func GetCurrentHost() string {
	if defaultClient == nil {
		return ""
	}
	return defaultClient.getCurrentHost()
}

// GetMirrorCount returns the total number of available mirrors
func GetMirrorCount() int {
	if defaultClient == nil {
		return 0
	}
	return defaultClient.getMirrorCount()
}

var ErrNotInit = errors.New("client not initialized")