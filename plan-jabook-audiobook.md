# JaBook (Offline) — Audiobook App Plan (WebView + Native Core)
**Version:** 2025‑08‑27

> Offline-first app for streaming/playing audiobooks from RuTracker torrents. Works **without login**; RuTracker login is **optional** (required for online search). Supports Android **7.0 (API 24)** through **15 (API 35)**. Lightweight SPA front-end: **Svelte + Vite**.

---

## 1) Goals & Constraints
- **No external backend** — all logic on-device.
- **Works without authorization** (local library, playback, history, exports).
- **Optional login** only when user wants to perform **online search / fetch details** (RuTracker search requires auth).
- **Mirror/endpoint manager** for RuTracker (domain changes, regional blocks).
- **Unified User‑Agent** between WebView and OkHttp to look like a single browser.
- **Robust debug logging**: in‑app viewer + file rotation + one‑tap Share (email/messengers).
- **Graceful UX in WebView**; minimal Kotlin; small APK size; secure defaults.

---

## 2) Tech Stack (current & lightweight)
- **Frontend (SPA):** **Svelte + Vite** (compiled, tiny runtime).
- **Container UI:** Android **WebView** loading SPA from `assets/` via **WebViewAssetLoader** (`https://appassets.androidplatform.net/...`) for secure local loading.
- **Local REST + stream:** In‑app HTTP server on `127.0.0.1:17171` (REST `/api/*`, audio `/stream/*`, supports Range/206).
- **HTTP client:** **OkHttp 5.1.0** + CookieJar for requests to RuTracker mirrors.
- **HTML parsing:** **jsoup 1.21.2** (fast, robust HTML → DOM parsing).
- **Torrents:** **libtorrent4j 2.1.0‑37** (sequential download for streaming).
- **Player:** **AndroidX Media3 1.6.1** (ExoPlayer + MediaSession) for background, lock screen, headset controls.
- **WebView utils:** **androidx.webkit 1.13.0** for compatibility helpers & asset loader.
- **SDKs:** `minSdk = 24`, `compileSdk = 35`, `targetSdk = 35`.
- **ABI:** only **arm64‑v8a** and **x86_64** for libtorrent4j to keep APK lean.

---

## 3) Project Structure (best‑practice)
```
/app
  /src/main/java/com/jabook/app/
    MainActivity.kt                 // WebView setup, AssetLoader, UA bootstrap
    PlayerService.kt                // Foreground service + MediaSession (Media3)
    DebugShareProvider.kt           // FileProvider subclass (optional helper)
    di/                             // lightweight DI singletons (providers)
    ui/                             // any native UI stubs (optional)
  /src/main/assets/                 // Svelte build artifacts (index.html, *.js, *.css)
  /src/main/res/xml/file_paths.xml  // FileProvider paths for log sharing
  AndroidManifest.xml
  build.gradle.kts

/core-net
  src/main/java/.../Http.kt         // OkHttp client, Interceptors (UA, retries), CookieJar
  build.gradle.kts

/core-endpoints
  src/main/java/.../EndpointResolver.kt   // mirrors, health-check, failover, storage
  build.gradle.kts

/core-auth
  src/main/java/.../AuthService.kt        // WebView login (primary), programmatic login (fallback)
  build.gradle.kts

/core-parse
  src/main/java/.../Parser.kt             // Jsoup mappers: search, topic details
  build.gradle.kts

/core-torrent
  src/main/java/.../TorrentService.kt     // libtorrent4j wrapper (add/remove, sequential, status)
  build.gradle.kts

/core-stream
  src/main/java/.../LocalHttp.kt          // NanoHTTPD router: /api/*, /stream/* with Range/206
  build.gradle.kts

/core-player
  src/main/java/.../Player.kt             // ExoPlayer + MediaSession integration
  build.gradle.kts

/core-logging
  src/main/java/.../AppLog.kt             // structured logs (NDJSON), rotation, sinks
  build.gradle.kts

/spa                                         // Svelte source (kept for reference; outputs copied to assets)
  src/...
  vite.config.ts
  package.json
  README.md

gradle/libs.versions.toml                    // versions catalog
build.gradle.kts                             // root (BOMs, common config)
settings.gradle.kts
Makefile                                     // build & release targets (no extra shell scripts)
keystore.properties.example                  // template (exclude real secrets from VCS)
```

---

## 4) Unified User‑Agent (WebView ⇄ OkHttp)
**Why:** Cloudflare and RuTracker should see one consistent browser fingerprint.

**Source of truth (boot):**
- Prefer `WebSettings.getDefaultUserAgent(context)`; if unavailable, use `webView.settings.userAgentString`.
- Persist to `UserAgentRepository` (SharedPreferences).

**Keep in sync:**
- On every `MainActivity` WebView init, refresh UA and update repository if changed (Chromium updates can change UA).
- OkHttp **adds UA via Interceptor** reading from repository.

**Pseudocode (Kotlin):**
```kotlin
// UserAgentRepository.kt
class UserAgentRepository(ctx: Context) {
  private val prefs = ctx.getSharedPreferences("ua", Context.MODE_PRIVATE)
  fun get(): String = prefs.getString("ua", default(ctx))!!
  fun set(ua: String) = prefs.edit().putString("ua", ua).apply()
  private fun default(ctx: Context): String =
    try { WebSettings.getDefaultUserAgent(ctx) } catch (e: Throwable) {
      System.getProperty("http.agent") ?: "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36"
    }
}

// in MainActivity.onCreate:
val uaRepo = UserAgentRepository(this)
val current = try { WebSettings.getDefaultUserAgent(this) } catch (_: Throwable) { webView.settings.userAgentString }
if (current.isNotBlank() && current != uaRepo.get()) uaRepo.set(current)

// OkHttp Interceptor:
class UaInterceptor(private val repo: UserAgentRepository) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val ua = repo.get()
    val req = chain.request().newBuilder()
      .header("User-Agent", ua)
      .build()
    return chain.proceed(req)
  }
}
```

> Note: Android 16+ may reduce UA fields (privacy). That’s fine; both WebView and OkHttp will match.

---

## 5) Endpoint (Mirror) Manager
**Features**
- Default mirror list + **user-defined mirrors**.
- **Health‑check** (parallel): HEAD/GET `index.php`, `login.php`, probe `tracker.php?nm=ping`.
- Validate status (200/3xx), `Content-Type`, key HTML signature (selectors/phrases), **RTT**.
- Persist health & ordering; pick **best healthy**; **failover** on 4xx/5xx/timeout once.
- UI: list with ✅/⚠️/⛔ status, **Check all**, **Add mirror** (validate before add), **Reset to defaults**.

**REST**
- `GET  /api/endpoints` → mirrors + health.
- `GET  /api/endpoints/active` → current active.
- `POST /api/endpoints { url }` → validate & add.
- `POST /api/endpoints/rehash` → re‑check & re‑pick.

---

## 6) Authorization (Optional, Safe)
**Primary:** **Login via in‑app WebView** (credentials posted directly to RuTracker; cookies stay in app sandbox).  
**Fallback:** Programmatic login with OkHttp (extract hidden fields, post form, verify cookies; on captcha/blocks → prompt WebView login).

**Logout:** Clear CookieManager + OkHttp CookieJar.

**Search rule:** Online search locked until `loggedIn = true`. SPA shows a banner “Login required for search”.

---

## 7) Local HTTP API (Minimum Surface)
- `POST /api/login { username, password }` → 200/401 (fallback path, optional).
- `GET  /api/me` → `{ loggedIn: boolean, user?: {...} }`.
- `GET  /api/search?q=...&page=1` → requires `loggedIn`; else 403 with hint.
- `GET  /api/topic/{id}` → details & magnet/`.torrent` (auth required).
- `POST /api/torrents { magnet | torrentUrl }` → `{ id }`.
- `GET  /api/torrents/{id}` → `{ progress, rateDown, rateUp, eta, file, state }`.
- `DELETE /api/torrents/{id}` → 200.
- `GET  /stream/{id}` → audio bytes with **Range/206** & `Accept-Ranges`.

---

## 8) Torrent & Streaming
- Add torrent by magnet or .torrent URL from topic page.
- Enable **sequential download**; pick the primary audio file (by extension/size).
- Serve bytes via `/stream/{id}` with **Range** for `<audio>`/ExoPlayer seeking.
- Persist session, throttle speeds, auto‑resume after network changes.
- Storage: internal app storage; **Export** via SAF (user‑selected folder).

---

## 9) Player (Background UX)
- **Media3 ExoPlayer** + **MediaSession** in a **Foreground Service**.
- Lock‑screen controls, headset buttons, notifications with title/cover/progress.
- SPA can control native player via REST (`/api/player/...`) **or** use `<audio>` directly.
  - Recommended for best UX: native ExoPlayer controlled by SPA.

---

## 10) WebView Configuration
- Load SPA via **WebViewAssetLoader** (`https://appassets.androidplatform.net/assets/index.html`).
- Enable JS; `mediaPlaybackRequiresUserGesture = false`.
- Enable cookies; **third‑party cookies** if RuTracker flow needs cross‑site.
- Block mixed content; only allow HTTP to `127.0.0.1:17171` (CSP in SPA as well).
- Keep `User‑Agent` untouched in WebView (source of truth).

---

## 11) Debug Logging & Share
**Format:** **NDJSON** (one JSON per line), UTC timestamps.  
Fields: `ts`, `level`, `subsystem` (`auth`, `torrent`, `net`, `endpoints`, `ui`...), `msg`, `thread`, optional `cause` (`type`, `message`).  
Example:
```json
{"ts":"2025-08-27T11:03:17.231Z","level":"INFO","subsystem":"endpoints","msg":"Active mirror set","thread":"main","url":"https://rutracker.org"}
```
**Levels:** TRACE, DEBUG, INFO, WARN, ERROR (gate DEBUG+TRACE by `BuildConfig.DEBUG`).

**Sinks:**
- **Logcat** (dev only).
- **Rolling files** in `files/logs/` (internal storage): `app-YYYYMMDD-HHmmss.ndjson` max 2 MB, keep last 10 files (FIFO).
- **In‑app Debug tab** (SPA): tail view, filter by level/subsystem, search.

**Share:**
- Button “Share logs” zips last N files and invokes Android **Sharesheet**; share via email/messengers/cloud. Implement via **FileProvider** with content URIs. No world‑readable files.

---

## 12) Build & Release (Makefile + Gradle; no extra shell scripts)
**Signing options:**
1) **App Bundle (.aab) with Play App Signing (recommended)**  
2) **Signed APK** (side‑load / distribution outside Play)

**Keystore management:**
- Create upload keystore via Android Studio wizard **or** `keytool` (documented below).  
- Store credentials in `keystore.properties` (excluded from VCS).

**`keystore.properties.example`**
```
storeFile=/absolute/path/to/upload.jks
storePassword=CHANGE_ME
keyAlias=upload
keyPassword=CHANGE_ME
```

**`build.gradle.kts` (app) — signing**
```kotlin
android {
  // ...
  signingConfigs {
    create("release") {
      val props = Properties().apply {
        load(rootProject.file("keystore.properties").inputStream())
      }
      storeFile = file(props["storeFile"] as String)
      storePassword = props["storePassword"] as String
      keyAlias = props["keyAlias"] as String
      keyPassword = props["keyPassword"] as String
      enableV3Signing = true
      enableV4Signing = true
    }
  }
  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      signingConfig = signingConfigs.getByName("release")
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
    getByName("debug") {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
    }
  }
}
```

**Makefile (excerpt)**
```
# Use Gradle Wrapper. No external shell scripts.

APP_MODULE := app

.PHONY: clean debug apk aab test lint logs

clean:
\t./gradlew clean

debug:
\t./gradlew :$(APP_MODULE):assembleDebug

apk:    ## Signed release APK
\t./gradlew :$(APP_MODULE):assembleRelease

aab:    ## App Bundle for Play
\t./gradlew :$(APP_MODULE):bundleRelease

test:
\t./gradlew testDebugUnitTest connectedDebugAndroidTest

lint:
\t./gradlew lint

logs:   ## Export zipped logs to /tmp (example path)
\t./gradlew :$(APP_MODULE):copyLogs
```

**Gradle task for logs (example)**
```kotlin
// app/build.gradle.kts
tasks.register<Copy>("copyLogs") {
  val logsDir = layout.projectDirectory.dir("build/exported-logs")
  from(layout.projectDirectory.dir("app-logs"))  // or wire to internal dir for dev builds
  into(logsDir)
}
```

**Create keystore (option A, Android Studio GUI):**
- Build → Generate Signed Bundle / APK → Create new keystore → fill fields → Next.

**Create keystore (option B, keytool CLI):**
```
keytool -genkeypair -v -keystore /path/upload.jks -storetype JKS \
 -storepass CHANGE_ME -keypass CHANGE_ME -alias upload -keyalg RSA -keysize 2048 -validity 36500
```
> Keep the keystore safe and back it up.

**Build commands:**
- Debug APK: `make debug`
- Release APK: `make apk`
- Release AAB: `make aab`

---

## 13) SPA (Svelte) Screens
- **Home/Library:** local items, history, continue listening, storage stats.
- **Search:** disabled until logged in; after login: query, list, paging.
- **Topic Details:** files, sizes, magnet button “Listen / Download”.
- **Player mini‑bar:** play/pause/seek; tap → full player.
- **Mirrors:** list with status, recheck, add/remove, set active, reset defaults.
- **Debug:** live tail, level filters, “Share logs” button.
- **Settings:** theme, logs verbosity, advanced UA (readonly view), export data.

---

## 14) Milestones (1–2–3)

**M1 — Skeleton runnable**
- WebView + AssetLoader loads Svelte index.
- UA repository + OkHttp UA interceptor.
- Local REST server: `/api/ping`, `/api/me` (stub), `/api/endpoints*` (working health‑check).
- Mirrors UI in SPA (list/check/add/reset).

**M2 — Auth + Search + Topic**
- WebView login flow; `/api/me` real.
- `/api/search` + `/api/topic/{id}` (with auth check + failover).

**M3 — Torrent + Stream + Player + Debug**
- Add torrent, sequential download, `/stream/{id}` with Range.
- ExoPlayer foreground service + MediaSession; SPA controls.
- Logging: NDJSON, rotation, Debug tab, Share logs (FileProvider).
- Export via SAF; basic library/history screens.

---

## 15) Acceptance Checklist
- ✅ Android 7–15; online search locked unless logged in.
- ✅ UA synchronized across WebView and OkHttp (single browser fingerprint).
- ✅ Mirrors manager can add, validate, switch; auto‑failover works.
- ✅ Background playback with MediaSession + headset controls.
- ✅ NDJSON logs with rotation + in‑app viewer + Share via FileProvider.
- ✅ No external backend; all data local; export available.
- ✅ APK size kept minimal (limited ABIs, lightweight SPA).
