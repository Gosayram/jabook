// JaBook Svelte SPA Main Entry Point
import { mount } from 'svelte';
import App from './App.svelte';

// Check if we're in a WebView environment
const isWebView = typeof window.JaBook !== 'undefined';

// Initialize the app
const app = mount(App, {
    target: document.getElementById('app'),
    props: {
        isWebView: isWebView,
        api: isWebView ? window.JaBook.api : null,
        utils: isWebView ? window.JaBook.utils : null
    }
});

// Handle app lifecycle
if (isWebView) {
    // Listen for messages from native code
    window.addEventListener('message', (event) => {
        // Handle messages from native code
        console.log('Received message from native:', event.data);
        
        // Example: Handle navigation
        if (event.data.type === 'navigate') {
            app.$set({ currentView: event.data.view });
        }
        
        // Example: Handle user authentication
        if (event.data.type === 'auth') {
            app.$set({ user: event.data.user });
        }
    });
    
    // Send initialization message to native code
    window.ReactNativeWebView?.postMessage(JSON.stringify({
        type: 'initialized',
        timestamp: Date.now()
    }));
}

// Handle offline/online events
window.addEventListener('online', () => {
    console.log('App came online');
    app.$set({ online: true });
});

window.addEventListener('offline', () => {
    console.log('App went offline');
    app.$set({ online: false });
});

// Handle visibility changes for background/foreground
document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
        console.log('App went to background');
        app.$set({ visible: false });
    } else {
        console.log('App came to foreground');
        app.$set({ visible: true });
    }
});

// Export app for debugging
window.JaBookApp = app;

console.log('JaBook SPA initialized');