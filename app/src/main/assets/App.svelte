<script>
    import { onMount } from 'svelte';
    
    // App state
    let currentView = 'home';
    let user = null;
    let online = navigator.onLine;
    let visible = !document.hidden;
    let mirrors = [];
    let loading = true;
    let isLoggedIn = false;
    
    // Login form state
    let loginForm = {
        username: '',
        password: '',
        error: null,
        loading: false
    };
    
    // Search state
    let searchQuery = '';
    let searchResults = [];
    let searchLoading = false;
    let searchPage = 1;
    
    // Mock data for development
    const mockLibrary = [
        { id: 1, title: 'The Great Gatsby', author: 'F. Scott Fitzgerald', progress: 65, duration: '8h 32m' },
        { id: 2, title: 'To Kill a Mockingbird', author: 'Harper Lee', progress: 30, duration: '6h 45m' },
        { id: 3, title: '1984', author: 'George Orwell', progress: 100, duration: '9h 15m' },
    ];
    
    const mockMirrors = [
        { url: 'https://rutracker.org', healthy: true, status: '✅ Healthy', responseTime: 150 },
        { url: 'https://rutracker.net', healthy: false, status: '❌ Unhealthy', responseTime: 5000 },
        { url: 'https://rutracker.online', healthy: true, status: '✅ Healthy', responseTime: 200 },
    ];
    
    // Navigation
    function navigate(view) {
        if (view === 'search' && !isLoggedIn) {
            currentView = 'login';
            return;
        }
        currentView = view;
    }
    
    // Login modal
    function renderLoginModal() {
        if (currentView !== 'login') return '';
        
        return `
            <div id="loginModal" class="modal-overlay">
                <div class="modal">
                    <div class="modal-header">
                        <h2>Login to RuTracker</h2>
                        <button class="close-btn" on:click={() => currentView = 'home'}>✕</button>
                    </div>
                    <div class="modal-body">
                        <form on:submit|preventDefault={handleLogin}>
                            <div class="form-group">
                                <label class="form-label">Username</label>
                                <input
                                    type="text"
                                    class="form-input"
                                    value={loginForm.username}
                                    on:input={(e) => loginForm.username = e.target.value}
                                    placeholder="Enter your RuTracker username"
                                />
                            </div>
                            <div class="form-group">
                                <label class="form-label">Password</label>
                                <input
                                    type="password"
                                    class="form-input"
                                    value={loginForm.password}
                                    on:input={(e) => loginForm.password = e.target.value}
                                    placeholder="Enter your password"
                                />
                            </div>
                            ${loginForm.error ? `
                                <div class="error">${loginForm.error}</div>
                            ` : ''}
                            <button type="submit" class="btn btn-primary" style="width: 100%;" ${loginForm.loading ? 'disabled' : ''}>
                                ${loginForm.loading ? '<div class="loading"></div> Logging in...' : '🔐 Login'}
                            </button>
                        </form>
                        <div class="login-help">
                            <p>Need help logging in?</p>
                            <ul>
                                <li>Make sure you have a valid RuTracker account</li>
                                <li>Check your username and password</li>
                                <li>Ensure you're not blocked by anti-bot measures</li>
                            </ul>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }
    
    // Initialize app
    onMount(async () => {
        loading = true;
        
        // Load mirrors from API
        try {
            const response = await fetch('/api/endpoints');
            if (response.ok) {
                mirrors = await response.json();
            }
        } catch (error) {
            console.error('Failed to load mirrors:', error);
            mirrors = mockMirrors;
        }
        
        // Check login status
        await checkLoginStatus();
        
        loading = false;
        
        // Set up event listeners
        window.addEventListener('online', () => online = true);
        window.addEventListener('offline', () => online = false);
        document.addEventListener('visibilitychange', () => visible = !document.hidden);
    });
    
    // Check login status
    async function checkLoginStatus() {
        try {
            const response = await fetch('/api/me');
            if (response.ok) {
                const userData = await response.json();
                isLoggedIn = userData.loggedIn;
                user = userData;
            }
        } catch (error) {
            console.error('Failed to check login status:', error);
            isLoggedIn = false;
            user = null;
        }
    }
    
    // Handle login
    async function handleLogin() {
        if (!loginForm.username || !loginForm.password) {
            loginForm.error = 'Please enter username and password';
            return;
        }
        
        loginForm.loading = true;
        loginForm.error = null;
        
        try {
            const response = await fetch('/api/login', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: `username=${encodeURIComponent(loginForm.username)}&password=${encodeURIComponent(loginForm.password)}`
            });
            
            const result = await response.json();
            
            if (result.success) {
                isLoggedIn = true;
                user = { username: loginForm.username };
                currentView = 'home';
                // Close login modal
                document.getElementById('loginModal')?.classList.add('hidden');
            } else {
                loginForm.error = result.error || 'Login failed';
            }
        } catch (error) {
            console.error('Login failed:', error);
            loginForm.error = 'Network error. Please try again.';
        } finally {
            loginForm.loading = false;
        }
    }
    
    // Handle logout
    async function handleLogout() {
        try {
            await fetch('/api/logout', { method: 'POST' });
            isLoggedIn = false;
            user = null;
            currentView = 'home';
        } catch (error) {
            console.error('Logout failed:', error);
        }
    }
    
    // Handle search
    async function handleSearch() {
        if (!searchQuery.trim()) {
            searchResults = [];
            return;
        }
        
        searchLoading = true;
        
        try {
            const response = await fetch(`/api/search?q=${encodeURIComponent(searchQuery)}&page=${searchPage}`);
            if (response.ok) {
                const result = await response.json();
                searchResults = result.results || [];
            } else if (response.status === 401) {
                // Not authenticated
                currentView = 'login';
                return;
            }
        } catch (error) {
            console.error('Search failed:', error);
            searchResults = [];
        } finally {
            searchLoading = false;
        }
    }
    
    // Handle topic view
    async function viewTopic(topicId) {
        try {
            const response = await fetch(`/api/topic?id=${topicId}`);
            if (response.ok) {
                const result = await response.json();
                // In a real app, this would navigate to a topic detail view
                console.log('Topic details:', result.topic);
                alert(`Topic: ${result.topic.title}\n\nFiles: ${result.topic.files.length}\nSize: ${formatBytes(result.topic.totalSize)}`);
            }
        } catch (error) {
            console.error('Failed to load topic:', error);
        }
    }
    
    // Format bytes to human readable
    function formatBytes(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }
    
    // Add new mirror
    async function addMirror() {
        const url = prompt('Enter mirror URL:');
        if (url) {
            try {
                const response = await fetch('/api/endpoints', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ url })
                });
                
                if (response.ok) {
                    // Refresh mirrors
                    const refreshResponse = await fetch('/api/endpoints');
                    mirrors = await refreshResponse.json();
                }
            } catch (error) {
                console.error('Failed to add mirror:', error);
                alert('Failed to add mirror');
            }
        }
    }
    
    // Remove mirror
    async function removeMirror(mirror) {
        if (confirm(`Remove mirror: ${mirror.url}?`)) {
            try {
                // In a real app, this would call an API
                mirrors = mirrors.filter(m => m.url !== mirror.url);
            } catch (error) {
                console.error('Failed to remove mirror:', error);
                alert('Failed to remove mirror');
            }
        }
    }
    
    // Reset mirrors
    async function resetMirrors() {
        if (confirm('Reset to default mirrors?')) {
            try {
                const response = await fetch('/api/endpoints/rehash', { method: 'POST' });
                if (response.ok) {
                    mirrors = await response.json();
                }
            } catch (error) {
                console.error('Failed to reset mirrors:', error);
                mirrors = mockMirrors;
            }
        }
    }
    
    // Render current view
    function renderView() {
        if (loading) {
            return `
                <div class="loading-container">
                    <div class="loading"></div>
                    <p>Loading...</p>
                </div>
            `;
        }
        
        switch (currentView) {
            case 'home':
                return renderHome();
            case 'mirrors':
                return renderMirrors();
            case 'search':
                return renderSearch();
            case 'player':
                return renderPlayer();
            case 'settings':
                return renderSettings();
            default:
                return renderHome();
        }
    }
    
    // Home view
    function renderHome() {
        return `
            <div class="home-view">
                <div class="welcome-section">
                    <h1>Welcome to JaBook</h1>
                    <p>Your offline-first audiobook player</p>
                    ${!isLoggedIn ? `
                        <div class="auth-prompt">
                            <p>Please log in to search for audiobooks</p>
                            <button class="btn btn-primary" on:click={() => currentView = 'login'}>
                                🔐 Login to RuTracker
                            </button>
                        </div>
                    ` : `
                        <div class="user-info">
                            <p>Welcome back, ${user.username}!</p>
                            <button class="btn btn-outline" on:click={handleLogout}>
                                🚪 Logout
                            </button>
                        </div>
                    `}
                </div>
                
                <div class="quick-actions">
                    <button class="btn btn-primary" on:click={() => navigate('search')} ${!isLoggedIn ? 'disabled' : ''}>
                        🔍 Search Audiobooks
                    </button>
                    <button class="btn btn-secondary" on:click={() => navigate('mirrors')}>
                        🔄 Manage Mirrors
                    </button>
                </div>
                
                <div class="section">
                    <h2>Continue Listening</h2>
                    <div class="list">
                        ${mockLibrary.filter(item => item.progress < 100).map(item => `
                            <div class="list-item">
                                <div>
                                    <h3>${item.title}</h3>
                                    <p class="text-secondary">${item.author} • ${item.duration}</p>
                                    <div class="progress-bar">
                                        <div class="progress-fill" style="width: ${item.progress}%"></div>
                                    </div>
                                    <small>${item.progress}% complete</small>
                                </div>
                                <button class="btn btn-primary" on:click={() => navigate('player')}>▶️ Play</button>
                            </div>
                        `).join('')}
                    </div>
                </div>
                
                <div class="section">
                    <h2>Recently Completed</h2>
                    <div class="list">
                        ${mockLibrary.filter(item => item.progress === 100).map(item => `
                            <div class="list-item">
                                <div>
                                    <h3>${item.title}</h3>
                                    <p class="text-secondary">${item.author} • ${item.duration}</p>
                                    <small class="text-success">✅ Completed</small>
                                </div>
                                <button class="btn btn-outline">📖 Re-read</button>
                            </div>
                        `).join('')}
                    </div>
                </div>
            </div>
        `;
    }
    
    // Mirrors view
    function renderMirrors() {
        return `
            <div class="mirrors-view">
                <div class="section">
                    <div class="card-header">
                        <h2>Mirror Management</h2>
                        <div>
                            <button class="btn btn-secondary" on:click={addMirror}>➕ Add</button>
                            <button class="btn btn-outline" on:click={resetMirrors}>🔄 Reset</button>
                        </div>
                    </div>
                    
                    <div class="mirrors-list">
                        ${mirrors.map(mirror => `
                            <div class="mirror-item ${mirror.healthy ? 'healthy' : 'unhealthy'}">
                                <div class="mirror-info">
                                    <h3>${mirror.url}</h3>
                                    <div class="mirror-meta">
                                        <span class="status ${mirror.healthy ? 'healthy' : 'unhealthy'}">${mirror.status}</span>
                                        ${mirror.responseTime ? `<span class="response-time">${mirror.responseTime}ms</span>` : ''}
                                    </div>
                                </div>
                                <div class="mirror-actions">
                                    <button class="btn btn-danger" on:click={() => removeMirror(mirror)}>🗑️ Remove</button>
                                </div>
                            </div>
                        `).join('')}
                    </div>
                </div>
                
                <div class="section">
                    <h2>Server Status</h2>
                    <div class="card">
                        <div class="status-grid">
                            <div class="status-item">
                                <span class="status-label">Local Server</span>
                                <span class="status-value ${online ? 'text-success' : 'text-error'}">
                                    ${online ? '🟢 Online' : '🔴 Offline'}
                                </span>
                            </div>
                            <div class="status-item">
                                <span class="status-label">Healthy Mirrors</span>
                                <span class="status-value">
                                    ${mirrors.filter(m => m.healthy).length}/${mirrors.length}
                                </span>
                            </div>
                            <div class="status-item">
                                <span class="status-label">Active Mirror</span>
                                <span class="status-value">
                                    ${mirrors.find(m => m.healthy)?.url || 'None'}
                                </span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }
    
    // Search view
    function renderSearch() {
        return `
            <div class="search-view">
                <div class="section">
                    <h2>Search Audiobooks</h2>
                    <div class="search-form">
                        <input
                            type="text"
                            class="form-input"
                            placeholder="Search for audiobooks..."
                            value="${searchQuery}"
                            on:input={(e) => searchQuery = e.target.value}
                        />
                        <button class="btn btn-primary" on:click={handleSearch} ${searchLoading ? 'disabled' : ''}>
                            ${searchLoading ? '<div class="loading"></div>' : '🔍 Search'}
                        </button>
                    </div>
                </div>
                
                <div class="section">
                    <h2>Search Results</h2>
                    ${searchLoading ? `
                        <div class="loading-container">
                            <div class="loading"></div>
                            <p>Searching...</p>
                        </div>
                    ` : searchResults.length > 0 ? `
                        <div class="search-results">
                            ${searchResults.map(result => `
                                <div class="search-result">
                                    <h3>${result.title}</h3>
                                    <p class="text-secondary">by ${result.author || 'Unknown Author'}</p>
                                    <div class="result-meta">
                                        <span>📦 ${formatBytes(result.size)}</span>
                                        <span>🌱 ${result.seeds} seeds</span>
                                        <span>👥 ${result.leeches} leeches</span>
                                    </div>
                                    <div class="result-actions">
                                        <button class="btn btn-primary" on:click={() => viewTopic(result.id)}>
                                            📖 View Details
                                        </button>
                                        ${result.magnetUrl ? `
                                            <a href="${result.magnetUrl}" class="btn btn-secondary" download>
                                                💾 Download
                                            </a>
                                        ` : ''}
                                    </div>
                                </div>
                            `).join('')}
                        </div>
                    ` : searchQuery ? `
                        <div class="no-results">
                            <p>No results found for "${searchQuery}"</p>
                        </div>
                    ` : `
                        <div class="search-placeholder">
                            <p>Enter a search term above to find audiobooks</p>
                        </div>
                    `}
                </div>
            </div>
        `;
    }
    
    // Player view
    function renderPlayer() {
        return `
            <div class="player-view">
                <div class="player-container">
                    <div class="now-playing">
                        <h2>The Great Gatsby</h2>
                        <p class="text-secondary">F. Scott Fitzgerald</p>
                    </div>
                    
                    <div class="progress-section">
                        <div class="progress-bar">
                            <div class="progress-fill" style="width: 65%"></div>
                        </div>
                        <div class="time-info">
                            <span>5h 35m</span>
                            <span>8h 32m</span>
                        </div>
                    </div>
                    
                    <div class="player-controls">
                        <button class="btn btn-secondary">⏮️</button>
                        <button class="btn btn-primary" style="padding: 1rem 2rem;">▶️</button>
                        <button class="btn btn-secondary">⏭️</button>
                    </div>
                    
                    <div class="player-actions">
                        <button class="btn btn-outline">📁 Download</button>
                        <button class="btn btn-outline">📋 Share</button>
                        <button class="btn btn-outline">⚙️ Settings</button>
                    </div>
                </div>
            </div>
        `;
    }
    
    // Settings view
    function renderSettings() {
        return `
            <div class="settings-view">
                <div class="section">
                    <h2>Settings</h2>
                    
                    <div class="settings-group">
                        <h3>Appearance</h3>
                        <div class="setting-item">
                            <span>Theme</span>
                            <select class="form-input">
                                <option>Light</option>
                                <option>Dark</option>
                                <option>System</option>
                            </select>
                        </div>
                    </div>
                    
                    <div class="settings-group">
                        <h3>Playback</h3>
                        <div class="setting-item">
                            <span>Autoplay next episode</span>
                            <input type="checkbox" checked />
                        </div>
                        <div class="setting-item">
                            <span>Sleep timer</span>
                            <input type="number" class="form-input" value="30" min="1" max="180" />
                        </div>
                    </div>
                    
                    <div class="settings-group">
                        <h3>Storage</h3>
                        <div class="setting-item">
                            <span>Clear cache</span>
                            <button class="btn btn-outline">Clear</button>
                        </div>
                    </div>
                    
                    <div class="settings-group">
                        <h3>About</h3>
                        <div class="setting-item">
                            <span>Version</span>
                            <span>1.0.0</span>
                        </div>
                        <div class="setting-item">
                            <span>Build</span>
                            <span>DEBUG</span>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }
</script>

<style>
    /* Loading container */
    .loading-container {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 3rem;
        text-align: center;
        color: var(--color-text-secondary);
    }
    
    .loading {
        width: 40px;
        height: 40px;
        border: 3px solid rgba(230, 217, 198, 0.3);
        border-radius: 50%;
        border-top-color: var(--color-accent);
        animation: spin 1s ease-in-out infinite;
        margin-bottom: 1rem;
    }
    
    @keyframes spin {
        to { transform: rotate(360deg); }
    }
    
    /* Home view */
    .home-view {
        display: flex;
        flex-direction: column;
        gap: 2rem;
    }
    
    .welcome-section {
        text-align: center;
        padding: 2rem 0;
    }
    
    .welcome-section h1 {
        color: var(--color-text-primary);
        margin-bottom: 0.5rem;
        font-size: 2rem;
    }
    
    .welcome-section p {
        color: var(--color-text-secondary);
        font-size: 1.1rem;
    }
    
    .quick-actions {
        display: flex;
        gap: 1rem;
        justify-content: center;
        flex-wrap: wrap;
    }
    
    .section {
        display: flex;
        flex-direction: column;
        gap: 1rem;
    }
    
    .section h2 {
        color: var(--color-text-primary);
        font-size: 1.5rem;
        margin-bottom: 0.5rem;
    }
    
    /* Progress bar */
    .progress-bar {
        width: 100%;
        height: 6px;
        background-color: var(--color-surface-lighter);
        border-radius: 3px;
        overflow: hidden;
        margin: 0.5rem 0;
    }
    
    .progress-fill {
        height: 100%;
        background-color: var(--color-accent);
        border-radius: 3px;
        transition: width 0.3s ease;
    }
    
    /* Mirrors view */
    .mirrors-view {
        display: flex;
        flex-direction: column;
        gap: 2rem;
    }
    
    .mirrors-list {
        display: flex;
        flex-direction: column;
        gap: 1rem;
    }
    
    .mirror-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 1rem;
        border-radius: var(--radius-lg);
        background-color: var(--color-surface);
        border: 1px solid var(--color-surface-light);
        transition: all var(--transition-normal);
    }
    
    .mirror-item:hover {
        transform: translateY(-2px);
        box-shadow: var(--shadow-md);
    }
    
    .mirror-item.healthy {
        border-left: 4px solid var(--color-success);
    }
    
    .mirror-item.unhealthy {
        border-left: 4px solid var(--color-error);
    }
    
    .mirror-info h3 {
        color: var(--color-text-primary);
        margin-bottom: 0.5rem;
    }
    
    .mirror-meta {
        display: flex;
        gap: 1rem;
        align-items: center;
    }
    
    .status {
        padding: 0.25rem 0.5rem;
        border-radius: var(--radius-sm);
        font-size: 0.75rem;
        font-weight: 500;
    }
    
    .status.healthy {
        background-color: var(--color-success);
        color: white;
    }
    
    .status.unhealthy {
        background-color: var(--color-error);
        color: white;
    }
    
    .response-time {
        color: var(--color-text-secondary);
        font-size: 0.875rem;
    }
    
    .mirror-actions {
        display: flex;
        gap: 0.5rem;
    }
    
    .status-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
        gap: 1rem;
    }
    
    .status-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 1rem;
        background-color: var(--color-surface-lighter);
        border-radius: var(--radius-md);
    }
    
    .status-label {
        color: var(--color-text-secondary);
        font-size: 0.875rem;
    }
    
    .status-value {
        font-weight: 600;
        color: var(--color-text-primary);
    }
    
    /* Search view */
    .search-view {
        display: flex;
        flex-direction: column;
        gap: 2rem;
    }
    
    .search-form {
        display: flex;
        gap: 1rem;
    }
    
    .search-form .form-input {
        flex: 1;
    }
    
    /* Player view */
    .player-view {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 2rem;
        padding: 2rem 0;
    }
    
    .player-container {
        width: 100%;
        max-width: 500px;
        display: flex;
        flex-direction: column;
        gap: 2rem;
    }
    
    .now-playing {
        text-align: center;
    }
    
    .now-playing h2 {
        color: var(--color-text-primary);
        font-size: 1.5rem;
        margin-bottom: 0.5rem;
    }
    
    .progress-section {
        display: flex;
        flex-direction: column;
        gap: 1rem;
    }
    
    .time-info {
        display: flex;
        justify-content: space-between;
        color: var(--color-text-secondary);
        font-size: 0.875rem;
    }
    
    .player-controls {
        display: flex;
        justify-content: center;
        align-items: center;
        gap: 2rem;
    }
    
    .player-actions {
        display: flex;
        justify-content: center;
        gap: 1rem;
        flex-wrap: wrap;
    }
    
    /* Settings view */
    .settings-view {
        display: flex;
        flex-direction: column;
        gap: 2rem;
    }
    
    .settings-group {
        display: flex;
        flex-direction: column;
        gap: 1rem;
    }
    
    .settings-group h3 {
        color: var(--color-text-primary);
        font-size: 1.25rem;
        margin-bottom: 0.5rem;
    }
    
    .setting-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 1rem;
        background-color: var(--color-surface);
        border-radius: var(--radius-md);
    }
    
    .setting-item span:first-child {
        color: var(--color-text-primary);
    }
    
    /* Responsive */
    @media (max-width: 768px) {
        .quick-actions {
            flex-direction: column;
        }
        
        .player-controls {
            gap: 1rem;
        }
        
        .status-grid {
            grid-template-columns: 1fr;
        }
        
        .search-result {
            margin-bottom: var(--spacing-md);
        }
        
        .result-meta {
            flex-direction: column;
            gap: 0.5rem;
        }
        
        .result-actions {
            flex-direction: column;
            gap: 0.5rem;
        }
    }
    
    /* Modal styles */
    .modal-overlay {
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background-color: rgba(0, 0, 0, 0.5);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 1000;
    }
    
    .modal {
        background-color: var(--color-surface);
        border-radius: var(--radius-lg);
        max-width: 500px;
        width: 90%;
        max-height: 90vh;
        overflow-y: auto;
        box-shadow: var(--shadow-lg);
    }
    
    .modal-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: var(--spacing-lg);
        border-bottom: 1px solid var(--color-surface-light);
    }
    
    .modal-header h2 {
        margin: 0;
        color: var(--color-text-primary);
    }
    
    .close-btn {
        background: none;
        border: none;
        font-size: 1.5rem;
        cursor: pointer;
        color: var(--color-text-secondary);
        padding: 0;
        width: 2rem;
        height: 2rem;
        display: flex;
        align-items: center;
        justify-content: center;
        border-radius: var(--radius-sm);
    }
    
    .close-btn:hover {
        background-color: var(--color-surface-light);
        color: var(--color-text-primary);
    }
    
    .modal-body {
        padding: var(--spacing-lg);
    }
    
    .auth-prompt {
        text-align: center;
        margin-top: var(--spacing-lg);
    }
    
    .user-info {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-top: var(--spacing-lg);
    }
    
    .login-help {
        margin-top: var(--spacing-lg);
        padding-top: var(--spacing-lg);
        border-top: 1px solid var(--color-surface-light);
    }
    
    .login-help ul {
        margin: 0.5rem 0 0 1.5rem;
        color: var(--color-text-secondary);
    }
    
    .search-results {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-md);
    }
    
    .search-result {
        background-color: var(--color-surface);
        border-radius: var(--radius-lg);
        padding: var(--spacing-lg);
        border: 1px solid var(--color-surface-light);
        transition: all var(--transition-normal);
    }
    
    .search-result:hover {
        transform: translateY(-2px);
        box-shadow: var(--shadow-md);
    }
    
    .search-result h3 {
        color: var(--color-text-primary);
        margin-bottom: 0.5rem;
    }
    
    .result-meta {
        display: flex;
        gap: 1rem;
        margin: 0.5rem 0;
        flex-wrap: wrap;
    }
    
    .result-meta span {
        color: var(--color-text-secondary);
        font-size: 0.875rem;
    }
    
    .result-actions {
        display: flex;
        gap: 0.5rem;
        margin-top: 1rem;
        flex-wrap: wrap;
    }
    
    .no-results, .search-placeholder {
        text-align: center;
        padding: 2rem;
        color: var(--color-text-secondary);
    }
</style>

<div class="app-container">
    <!-- Navigation -->
    <nav class="nav-bar">
        <div class="nav-container">
            <div class="nav-title">📚 JaBook</div>
            <div class="nav-menu">
                <button class="nav-item {currentView === 'home' ? 'active' : ''}" on:click={() => navigate('home')}>
                    🏠 Home
                </button>
                <button class="nav-item {currentView === 'search' ? 'active' : ''}" on:click={() => navigate('search')} ${!isLoggedIn ? 'disabled' : ''}>
                    🔍 Search
                </button>
                <button class="nav-item {currentView === 'mirrors' ? 'active' : ''}" on:click={() => navigate('mirrors')}>
                    🔄 Mirrors
                </button>
                <button class="nav-item {currentView === 'player' ? 'active' : ''}" on:click={() => navigate('player')}>
                    ▶️ Player
                </button>
                <button class="nav-item {currentView === 'settings' ? 'active' : ''}" on:click={() => navigate('settings')}>
                    ⚙️ Settings
                </button>
            </div>
        </div>
    </nav>
    
    <!-- Main content -->
    <main class="main-content">
        {renderView()}
    </main>
    
    <!-- Login modal -->
    {renderLoginModal()}
</div>