package com.jabook.core.net.cookie

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent CookieJar for OkHttp.
 * - Uses a single JSON blob in SharedPreferences to persist all cookies.
 * - Stores full cookie attributes (domain, path, expiresAt, secure, httpOnly, hostOnly).
 * - Purges expired cookies on load/save and before requests.
 * - Avoids deprecated HttpUrl.parse() by using toHttpUrlOrNull().
 */
class PersistentCookieJar(
    context: Context
) : CookieJar {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Keyed by domain to reduce duplication and allow subdomain matching
    private val store = ConcurrentHashMap<String, MutableSet<Cookie>>()

    init {
        loadFromPrefs()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val domain = url.host
        val bucket = store.getOrPut(domain) { mutableSetOf() }

        // Replace cookies with same (name, domain, path)
        for (c in cookies) {
            // Remove any existing cookie with the same identity
            bucket.removeIf { sameIdentity(it, c) }
            // Add only if not expired
            if (!c.hasExpired()) bucket.add(c)
        }

        purgeExpired()
        saveToPrefs()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        purgeExpired()

        val nowHost = url.host
        val result = mutableListOf<Cookie>()
        // Match exact domain and parent domains (per Cookie.matches)
        for ((domain, cookies) in store) {
            if (domainMatches(nowHost, domain)) {
                result += cookies.filter { it.matches(url) && !it.hasExpired() }
            }
        }
        return result
    }

    /** Clears all cookies (memory + disk). */
    fun clearCookies() {
        store.clear()
        prefs.edit().remove(KEY_STORE).apply()
    }

    /** Returns all cookies matching the given domain (exact or subdomain). */
    fun getCookiesForDomain(domain: String): List<Cookie> {
        val result = mutableListOf<Cookie>()
        for ((d, cookies) in store) {
            if (domainMatches(domain, d)) {
                result += cookies.filterNot { it.hasExpired() }
            }
        }
        return result
    }

    /** Heuristic: checks if there is any session/auth cookie. */
    fun isLoggedIn(): Boolean {
        return store.values
            .flatten()
            .any {
                val n = it.name.lowercase()
                (("session" in n) || ("auth" in n) || ("login" in n)) && !it.hasExpired()
            }
    }

    // --- Internal helpers ---

    private fun sameIdentity(a: Cookie, b: Cookie): Boolean {
        // Identity of a cookie is (name, domain, path)
        return a.name == b.name &&
                a.domain == b.domain &&
                a.path == b.path
    }

    private fun Cookie.hasExpired(nowMs: Long = System.currentTimeMillis()): Boolean {
        // Non-persistent cookies (no expiresAt) are session cookies; keep them in memory only.
        // OkHttp sets persistent=true when it has an expiresAt value.
        return this.persistent && this.expiresAt <= nowMs
    }

    private fun purgeExpired() {
        val now = System.currentTimeMillis()
        val toRemoveDomains = mutableListOf<String>()
        for ((domain, cookies) in store) {
            cookies.removeIf { it.hasExpired(now) }
            if (cookies.isEmpty()) toRemoveDomains += domain
        }
        toRemoveDomains.forEach { store.remove(it) }
    }

    private fun domainMatches(host: String, domain: String): Boolean {
        // Exact or suffix match with dot boundary (e.g., host=foo.bar.com matches domain=bar.com)
        return host == domain || host.endsWith(".$domain")
    }

    private fun saveToPrefs() {
        // Serialize as: { "domains": { "example.com": [ {cookie...}, ... ] } }
        val root = JSONObject()
        val domainsObj = JSONObject()
        for ((domain, cookies) in store) {
            if (cookies.isEmpty()) continue
            val arr = JSONArray()
            cookies.forEach { arr.put(serializeCookie(it)) }
            if (arr.length() > 0) domainsObj.put(domain, arr)
        }
        root.put("domains", domainsObj)
        prefs.edit().putString(KEY_STORE, root.toString()).apply()
    }

    private fun loadFromPrefs() {
        store.clear()
        val raw = prefs.getString(KEY_STORE, null) ?: return
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val domainsObj = root.optJSONObject("domains") ?: return

        val now = System.currentTimeMillis()
        for (key in domainsObj.keys()) {
            val arr = domainsObj.optJSONArray(key) ?: continue
            val bucket = mutableSetOf<Cookie>()
            for (i in 0 until arr.length()) {
                val cookieJson = arr.optJSONObject(i) ?: continue
                val cookie = deserializeCookie(cookieJson) ?: continue
                if (!cookie.hasExpired(now)) bucket += cookie
            }
            if (bucket.isNotEmpty()) store[key] = bucket
        }
        // In case old json had expired ones
        saveToPrefs()
    }

    private fun serializeCookie(c: Cookie): JSONObject {
        val o = JSONObject()
        o.put("name", c.name)
        o.put("value", c.value)
        o.put("expiresAt", c.expiresAt)            // 0 if non-persistent in OkHttp internals, but we use c.persistent
        o.put("domain", c.domain)
        o.put("path", c.path)
        o.put("secure", c.secure)
        o.put("httpOnly", c.httpOnly)
        o.put("hostOnly", c.hostOnly)
        o.put("persistent", c.persistent)
        return o
    }

    // Don't pass null as fallback to optString: use "" and validate
    private fun deserializeCookie(o: JSONObject): Cookie? {
        val name = o.optString("name", "")
        if (name.isEmpty()) return null

        val value = o.optString("value", "")
        if (value.isEmpty()) return null

        val domain = o.optString("domain", "")
        if (domain.isEmpty()) return null

        val path = o.optString("path", "/")
        val expiresAt = o.optLong("expiresAt", 0L)
        val secure = o.optBoolean("secure", false)
        val httpOnly = o.optBoolean("httpOnly", false)
        val hostOnly = o.optBoolean("hostOnly", false)
        val persistent = o.optBoolean("persistent", false)

        val builder = Cookie.Builder()
            .name(name)
            .value(value)
            .path(path)

        if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
        if (persistent && expiresAt > 0L) builder.expiresAt(expiresAt)
        if (secure) builder.secure()
        if (httpOnly) builder.httpOnly()

        return runCatching { builder.build() }.getOrNull()
    }

    companion object {
        private const val PREFS_NAME = "cookie_store_prefs"
        private const val KEY_STORE = "cookie_store_json"
    }
}