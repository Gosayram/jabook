# JaBook Fix Implementation Plan

## Phase 1: Critical Network Layer Fixes

### 1.1 Fix HTTP Client Configuration

**File**: `app/src/main/kotlin/com/jabook/app/core/di/NetworkModule.kt`

**Changes Needed**:
- Add cookie jar for session persistence
- Add proper browser-like headers
- Implement user agent rotation
- Add connection pooling optimization

### 1.2 Fix RuTracker Authentication

**File**: `app/src/main/kotlin/com/jabook/app/core/network/RuTrackerApiService.kt`

**Changes Needed**:
- Implement CSRF token extraction
- Fix login form parameters
- Add proper session validation
- Implement cookie-based authentication

### 1.3 Update HTML Parsing

**File**: `app/src/main/kotlin/com/jabook/app/core/network/RuTrackerParser.kt`

**Changes Needed**:
- Update CSS selectors for current RuTracker structure
- Add anti-bot detection
- Implement fallback parsing strategies
- Add proper error handling

## Phase 2: Architecture Improvements

### 2.1 Consolidate API Classes

**Files**: 
- `RuTrackerApiClient.kt`
- `RuTrackerApiService.kt`

**Changes Needed**:
- Merge duplicate functionality
- Standardize error handling
- Implement consistent return types

### 2.2 Add Torrent Support

**File**: `app/build.gradle.kts`

**Changes Needed**:
- Uncomment libtorrent4j dependency
- Add torrent manager implementation
- Implement magnet link processing

## Phase 3: Security & Performance

### 3.1 Improve Credential Storage

**File**: `app/src/main/kotlin/com/jabook/app/core/network/RuTrackerPreferences.kt`

**Changes Needed**:
- Implement proper encryption
- Add key management
- Secure credential storage

### 3.2 Add Rate Limiting

**New File**: `app/src/main/kotlin/com/jabook/app/core/network/RateLimiter.kt`

**Implementation Needed**:
- Request throttling
- Backoff strategies
- Queue management

## Implementation Order

1. **Day 1**: Fix NetworkModule and HTTP client
2. **Day 2**: Fix authentication and CSRF handling
3. **Day 3**: Update HTML parsing with current selectors
4. **Day 4**: Add torrent support and magnet link handling
5. **Day 5**: Security improvements and testing

## Testing Strategy

1. **Unit Tests**: Mock HTTP responses
2. **Integration Tests**: Real RuTracker endpoints
3. **Manual Testing**: Full authentication flow
4. **Performance Tests**: Memory and network usage

## Risk Mitigation

1. **Backup Current Code**: Create feature branch
2. **Incremental Changes**: Small, testable commits
3. **Fallback Mechanisms**: Keep guest mode working
4. **Error Handling**: Graceful degradation

## Success Criteria

1. ✅ Authentication works with real RuTracker credentials
2. ✅ Search returns actual audiobook results
3. ✅ Magnet links are extractable and functional
4. ✅ No memory leaks or performance issues
5. ✅ Proper error handling for all edge cases
