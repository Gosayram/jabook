<script>
    import { onMount } from 'svelte';
    
    // Props from main.js
    export let isWebView = false;
    export let api = null;
    export let utils = null;
    
    // App state
    let currentView = 'home';
    let user = null;
    let online = navigator.onLine;
    let visible = !document.hidden;
    let loading = false;
    let error = null;
    
    // Navigation
    const navigate = (view) => {
        currentView = view;
    };
    
    // Check user status
    const checkUserStatus = async () => {
        if (!isWebView || !api) return;
        
        try {
            loading = true;
            user = await api.me();
        } catch (err) {
            console.error('Failed to check user status:', err);
            user = null;
        } finally {
            loading = false;
        }
    };
    
    // Initialize app
    onMount(async () => {
        if (isWebView && api) {
            await checkUserStatus();
        }
        
        // Set up event listeners
        window.addEventListener('online', () => {
            online = true;
        });
        
        window.addEventListener('offline', () => {
            online = false;
        });
        
        document.addEventListener('visibilitychange', () => {
            visible = !document.hidden;
        });
    });
    
    // Navigation items
    const navItems = [
        { id: 'home', label: 'Home', icon: '🏠' },
        { id: 'search', label: 'Search', icon: '🔍' },
        { id: 'library', label: 'Library', icon: '📚' },
        { id: 'settings', label: 'Settings', icon: '⚙️' },
        { id: 'debug', label: 'Debug', icon: '🐛' }
    ];
    
    // Render current view
    const renderView = () => {
        if (loading) {
            return `
                <div class="loading-container">
                    <div class="loading"></div>
                    <p>Loading...</p>
                </div>
            `;
        }
        
        if (error) {
            return `
                <div class="error-container">
                    <h2>Error</h2>
                    <p>${error}</p>
                    <button class="btn btn-primary" on:click={() => error = null}>Dismiss</button>
                </div>
            `;
        }
        
        switch (currentView) {
            case 'home':
                return HomeView();
            case 'search':
                return SearchView();
            case 'library':
                return LibraryView();
            case 'settings':
                return SettingsView();
            case 'debug':
                return DebugView();
            default:
                return HomeView();
        }
    };
    
    // Home View
    function HomeView() {
        return `
            <div class="home-view">
                <div class="welcome-card">
                    <h1>Welcome to JaBook</h1>
                    <p>Your offline-first audiobook player</p>
                </div>
                
                <div class="quick-actions">
                    <h2>Quick Actions</h2>
                    <div class="action-grid">
                        <div class="action-card" onclick={() => navigate('search')}>
                            <div class="action-icon">🔍</div>
                            <h3>Search</h3>
                            <p>Find audiobooks</p>
                        </div>
                        <div class="action-card" onclick={() => navigate('library')}>
                            <div class="action-icon">📚</div>
                            <h3>Library</h3>
                            <p>Your collection</p>
                        </div>
                        <div class="action-card" onclick={() => navigate('settings')}>
                            <div class="action-icon">⚙️</div>
                            <h3>Settings</h3>
                            <p>Configure app</p>
                        </div>
                        <div class="action-card" onclick={() => navigate('debug')}>
                            <div class="action-icon">🐛</div>
                            <h3>Debug</h3>
                            <p>View logs</p>
                        </div>
                    </div>
                </div>
                
                ${user ? `
                    <div class="user-info">
                        <h2>Welcome back, ${user.username || 'User'}!</h2>
                        <p>You are ${user.loggedIn ? 'logged in' : 'not logged in'}</p>
                    </div>
                ` : `
                    <div class="auth-prompt">
                        <h2>Get Started</h2>
                        <p>Login to access online search and more features</p>
                        <button class="btn btn-primary" onclick="handleLogin()">Login</button>
                    </div>
                `}
            </div>
        `;
    }
    
    // Search View
    function SearchView() {
        if (!user || !user.loggedIn) {
            return `
                <div class="auth-required">
                    <h2>Login Required</h2>
                    <p>You need to be logged in to search for audiobooks</p>
                    <button class="btn btn-primary" onclick="handleLogin()">Login</button>
                </div>
            `;
        }
        
        return `
            <div class="search-view">
                <div class="search-bar">
                    <input type="text" placeholder="Search audiobooks..." on:keyup={handleSearch}>
                    <button class="btn btn-primary" onclick="performSearch()">Search</button>
                </div>
                
                <div class="search-results">
                    <h2>Search Results</h2>
                    <div class="results-grid">
                        <!-- Search results will be populated here -->
                        <div class="no-results">
                            <p>Enter a search term to find audiobooks</p>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }
    
    // Library View
    function LibraryView() {
        return `
            <div class="library-view">
                <h2>Your Library</h2>
                <div class="library-stats">
                    <div class="stat-card">
                        <h3>Total Items</h3>
                        <p>0</p>
                    </div>
                    <div class="stat-card">
                        <h3>Storage Used</h3>
                        <p>0 MB</p>
                    </div>
                </div>
                
                <div class="library-content">
                    <h3>Recently Added</h3>
                    <div class="empty-state">
                        <p>Your library is empty</p>
                        <button class="btn btn-primary" onclick={() => navigate('search')">Add Audiobooks</button>
                    </div>
                </div>
            </div>
        `;
    }
    
    // Settings View
    function SettingsView() {
        return `
            <div class="settings-view">
                <h2>Settings</h2>
                
                <div class="settings-section">
                    <h3>Appearance</h3>
                    <div class="setting-item">
                        <label>Theme</label>
                        <select class="form-input">
                            <option>Light</option>
                            <option>Dark</option>
                            <option>System</option>
                        </select>
                    </div>
                </div>
                
                <div class="settings-section">
                    <h3>Advanced</h3>
                    <div class="setting-item">
                        <label>User Agent</label>
                        <input type="text" class="form-input" value="${isWebView ? api?.getUserAgent?.() : 'N/A'}" readonly>
                    </div>
                    <div class="setting-item">
                        <label>Log Level</label>
                        <select class="form-input">
                            <option>Error</option>
                            <option>Warning</option>
                            <option>Info</option>
                            <option>Debug</option>
                        </select>
                    </div>
                </div>
                
                <div class="settings-section">
                    <h3>Actions</h3>
                    <button class="btn btn-secondary" onclick="clearCache()">Clear Cache</button>
                    <button class="btn btn-danger" onclick="clearData()">Clear All Data</button>
                </div>
            </div>
        `;
    }
    
    // Debug View
    function DebugView() {
        return `
            <div class="debug-view">
                <h2>Debug Information</h2>
                
                <div class="debug-info">
                    <div class="info-item">
                        <strong>WebView:</strong> ${isWebView ? 'Yes' : 'No'}
                    </div>
                    <div class="info-item">
                        <strong>Online:</strong> ${online ? 'Yes' : 'No'}
                    </div>
                    <div class="info-item">
                        <strong>Visible:</strong> ${visible ? 'Yes' : 'No'}
                    </div>
                    <div class="info-item">
                        <strong>User Agent:</strong> ${isWebView ? api?.getUserAgent?.() : 'N/A'}
                    </div>
                </div>
                
                <div class="debug-actions">
                    <button class="btn btn-primary" onclick="shareLogs()">Share Logs</button>
                    <button class="btn btn-secondary" onclick="clearLogs()">Clear Logs</button>
                </div>
                
                <div class="log-viewer">
                    <h3>Logs</h3>
                    <div class="log-container">
                        <div class="log-entry">Debug: App initialized</div>
                    </div>
                </div>
            </div>
        `;
    }
    
    // Event handlers
    function handleLogin() {
        if (isWebView && api) {
            // Open login WebView or navigate to login page
            console.log('Opening login...');
        }
    }
    
    function handleSearch(event) {
        // Handle search input
        if (event.key === 'Enter') {
            performSearch();
        }
    }
    
    async function performSearch() {
        if (!isWebView || !api) return;
        
        const searchTerm = document.querySelector('.search-bar input')?.value;
        if (!searchTerm) return;
        
        try {
            loading = true;
            const results = await api.search(searchTerm);
            // Handle search results
            console.log('Search results:', results);
        } catch (err) {
            console.error('Search failed:', err);
            error = 'Search failed. Please try again.';
        } finally {
            loading = false;
        }
    }
    
    function clearCache() {
        if (isWebView && api) {
            // Implement cache clearing
            console.log('Clearing cache...');
        }
    }
    
    function clearData() {
        if (confirm('Are you sure you want to clear all data? This cannot be undone.')) {
            if (isWebView && api) {
                // Implement data clearing
                console.log('Clearing all data...');
            }
        }
    }
    
    function shareLogs() {
        if (isWebView && api) {
            // Implement log sharing
            console.log('Sharing logs...');
        }
    }
    
    function clearLogs() {
        // Implement log clearing
        console.log('Clearing logs...');
    }
</script>

<style>
    .home-view {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-lg);
    }
    
    .welcome-card {
        text-align: center;
        padding: var(--spacing-xl);
        background: linear-gradient(135deg, var(--color-primary), var(--color-primary-light));
        color: white;
        border-radius: var(--radius-lg);
    }
    
    .welcome-card h1 {
        font-size: 2rem;
        margin-bottom: var(--spacing-sm);
    }
    
    .quick-actions {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-md);
    }
    
    .action-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
        gap: var(--spacing-md);
    }
    
    .action-card {
        background: var(--color-surface);
        border-radius: var(--radius-lg);
        padding: var(--spacing-lg);
        text-align: center;
        cursor: pointer;
        transition: all var(--transition-normal);
        border: 1px solid rgba(0, 0, 0, 0.1);
    }
    
    .action-card:hover {
        transform: translateY(-4px);
        box-shadow: var(--shadow-md);
    }
    
    .action-icon {
        font-size: 2rem;
        margin-bottom: var(--spacing-sm);
    }
    
    .action-card h3 {
        margin-bottom: var(--spacing-xs);
        color: var(--color-text-primary);
    }
    
    .action-card p {
        color: var(--color-text-secondary);
        font-size: 0.875rem;
    }
    
    .auth-prompt {
        text-align: center;
        padding: var(--spacing-xl);
        background: var(--color-surface-light);
        border-radius: var(--radius-lg);
    }
    
    .user-info {
        text-align: center;
        padding: var(--spacing-lg);
        background: var(--color-surface-light);
        border-radius: var(--radius-lg);
    }
    
    .loading-container {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        min-height: 200px;
        gap: var(--spacing-md);
    }
    
    .error-container {
        text-align: center;
        padding: var(--spacing-xl);
        background: var(--color-error);
        color: white;
        border-radius: var(--radius-lg);
    }
    
    .auth-required {
        text-align: center;
        padding: var(--spacing-xl);
        background: var(--color-warning);
        color: var(--color-text-primary);
        border-radius: var(--radius-lg);
    }
    
    .search-bar {
        display: flex;
        gap: var(--spacing-sm);
        margin-bottom: var(--spacing-lg);
    }
    
    .search-bar input {
        flex: 1;
    }
    
    .search-results {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-md);
    }
    
    .results-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
        gap: var(--spacing-md);
    }
    
    .no-results {
        text-align: center;
        padding: var(--spacing-xl);
        color: var(--color-text-secondary);
    }
    
    .library-stats {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
        gap: var(--spacing-md);
        margin-bottom: var(--spacing-lg);
    }
    
    .stat-card {
        background: var(--color-surface-light);
        padding: var(--spacing-lg);
        border-radius: var(--radius-lg);
        text-align: center;
    }
    
    .stat-card h3 {
        margin-bottom: var(--spacing-sm);
        color: var(--color-text-secondary);
    }
    
    .stat-card p {
        font-size: 1.5rem;
        font-weight: 600;
        color: var(--color-primary);
    }
    
    .empty-state {
        text-align: center;
        padding: var(--spacing-xl);
        color: var(--color-text-secondary);
    }
    
    .settings-section {
        margin-bottom: var(--spacing-lg);
    }
    
    .settings-section h3 {
        margin-bottom: var(--spacing-md);
        color: var(--color-text-primary);
    }
    
    .setting-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: var(--spacing-md);
    }
    
    .setting-item label {
        font-weight: 500;
        color: var(--color-text-primary);
    }
    
    .debug-info {
        background: var(--color-surface-light);
        padding: var(--spacing-lg);
        border-radius: var(--radius-lg);
        margin-bottom: var(--spacing-lg);
    }
    
    .info-item {
        margin-bottom: var(--spacing-sm);
    }
    
    .info-item strong {
        color: var(--color-primary);
    }
    
    .debug-actions {
        display: flex;
        gap: var(--spacing-sm);
        margin-bottom: var(--spacing-lg);
    }
    
    .log-viewer {
        background: var(--color-surface-light);
        border-radius: var(--radius-lg);
        padding: var(--spacing-lg);
    }
    
    .log-container {
        background: #000;
        color: #0f0;
        padding: var(--spacing-md);
        border-radius: var(--radius-md);
        font-family: 'Courier New', monospace;
        font-size: 0.875rem;
        max-height: 300px;
        overflow-y: auto;
    }
    
    .log-entry {
        margin-bottom: var(--spacing-xs);
        padding: var(--spacing-xs);
        border-bottom: 1px solid rgba(0, 255, 0, 0.1);
    }
</style>

<svelte:window on:online={() => online = true} on:offline={() => online = false} />

<div class="app-container">
    <!-- Navigation -->
    <nav class="nav-bar">
        <div class="nav-container">
            <div class="nav-title">📚 JaBook</div>
            <div class="nav-menu">
                {#each navItems as item}
                    <div 
                        class="nav-item {currentView === item.id ? 'active' : ''}" 
                        onclick={() => navigate(item.id)}
                    >
                        <span>{item.icon}</span>
                        <span>{item.label}</span>
                    </div>
                {/each}
            </div>
        </div>
    </nav>
    
    <!-- Main Content -->
    <main class="main-content">
        {@html renderView()}
    </main>
</div>