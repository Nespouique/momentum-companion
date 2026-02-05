# Momentum Companion - Architecture Document

## 1. Introduction

### 1.1 Purpose

This document defines the comprehensive technical architecture for **Momentum Companion**, a native Android application that bridges Samsung Health data (via Health Connect) to the Momentum fitness tracker API. It serves as the authoritative reference for all development decisions.

### 1.2 Scope

This architecture covers the MVP implementation including:

- Native Android application (Kotlin + Jetpack Compose)
- Health Connect SDK integration
- Background synchronization via WorkManager
- Communication with the Momentum REST API
- Security and credential management
- Error handling and offline resilience

### 1.3 Project Type

**Greenfield** - New standalone Android application in a separate repository (`momentum-companion`), communicating with the existing Momentum API.

### 1.4 Repository Strategy

**Polyrepo** - The companion app lives in its own repository, separate from the Momentum monorepo. API changes required for health sync remain in the Momentum monorepo.

---

## 2. High-Level Architecture

### 2.1 System Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                          ANDROID DEVICE                              │
│                                                                      │
│  ┌─────────────────┐    ┌──────────────────────────────────────┐    │
│  │  Samsung Health  │    │       Momentum Companion App         │    │
│  │  (Galaxy Watch   │    │                                      │    │
│  │   + Phone)       │    │  ┌────────────┐  ┌───────────────┐  │    │
│  └────────┬─────────┘    │  │ UI Layer   │  │  Sync Layer   │  │    │
│           │               │  │ (Compose)  │  │ (WorkManager) │  │    │
│           │ auto sync     │  └─────┬──────┘  └───────┬───────┘  │    │
│           v               │        │                  │          │    │
│  ┌─────────────────┐     │  ┌─────┴──────────────────┴───────┐  │    │
│  │ Health Connect   │◄────┤  │         Data Layer             │  │    │
│  │ (System Service) │     │  │  ┌─────────────┐ ┌──────────┐ │  │    │
│  └─────────────────┘     │  │  │ HC Reader   │ │ Retrofit │ │  │    │
│                           │  │  │ HC Mapper   │ │ OkHttp   │ │  │    │
│                           │  │  └─────────────┘ └──────────┘ │  │    │
│                           │  └────────────────────────────────┘  │    │
│                           └──────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ HTTPS POST /health-sync
                                        v
                            ┌───────────────────────┐
                            │    Momentum API        │
                            │    (Express.js)        │
                            │    (Port 3001)         │
                            └───────────┬───────────┘
                                        │
                                        │ Prisma upsert
                                        v
                            ┌───────────────────────┐
                            │    PostgreSQL          │
                            └───────────┬───────────┘
                                        │
                                        │ query
                                        v
                            ┌───────────────────────┐
                            │    Momentum Web        │
                            │    (Next.js PWA)       │
                            └───────────────────────┘
```

### 2.2 Data Flow

```
Samsung Health
     │
     │ (automatic Android sync between watch/phone)
     v
Health Connect (on-device system service)
     │
     │ (Health Connect Jetpack SDK - read only)
     v
Momentum Companion App (Android)
     │
     │ 1. HealthConnectReader reads raw records
     │ 2. HealthConnectMapper aggregates per day
     │ 3. Builds HealthSyncRequest DTO
     │ 4. Retrofit POSTs to API
     │
     │ HTTP POST /health-sync
     v
Momentum API (Express.js)
     │
     │ 1. Validates payload (Zod)
     │ 2. Ensures system TrackableItems exist
     │ 3. Upserts DailyEntry per metric
     │ 4. Upserts HealthActivity per exercise
     │ 5. Updates SyncDevice timestamp
     │
     │ Prisma upsert
     v
PostgreSQL
     │
     │ (existing queries via Prisma)
     v
Momentum Web (Next.js PWA)
```

### 2.3 Architecture Style

**MVVM with Repository Pattern** - Clean separation between UI, business logic, and data access:

- **View**: Jetpack Compose screens (stateless composables)
- **ViewModel**: Holds UI state, handles user actions, coordinates data access
- **Repository / Data Sources**: Health Connect reader, Retrofit API service, local preferences

### 2.4 Key Architectural Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Architecture | MVVM + Repository | Android standard, excellent Compose integration |
| UI Framework | Jetpack Compose | Declarative, modern, less boilerplate than XML |
| DI | Hilt | Android-standard, seamless WorkManager integration |
| HTTP Client | Retrofit + OkHttp | Mature Android ecosystem, interceptor support, self-signed cert handling |
| Serialization | kotlinx.serialization | Native Kotlin, no reflection, performant |
| Background | WorkManager | Survives reboots, respects Doze, battery-aware |
| Health Data | Health Connect SDK | Only programmatic access to Samsung Health data |
| Credentials | EncryptedSharedPreferences | AES-256 via Android Keystore, zero-config |
| Min SDK | API 34 | Health Connect integrated natively (no separate app install) |

---

## 3. Architecture Patterns

### 3.1 MVVM Pattern

```
┌─────────────────────────────────────────────────────┐
│                    UI Layer                           │
│                                                      │
│  Screen Composable                                   │
│       │                                              │
│       │ collectAsStateWithLifecycle()                 │
│       │                                              │
│  ViewModel                                           │
│       │                                              │
│       │ viewModelScope.launch { }                    │
│       │                                              │
├───────┼──────────────────────────────────────────────┤
│       │          Data Layer                           │
│       v                                              │
│  Repository / Data Source                             │
│       │                                              │
│       ├── HealthConnectReader  (Health Connect SDK)   │
│       ├── MomentumApiService   (Retrofit)            │
│       └── AppPreferences       (EncryptedSharedPrefs)│
└─────────────────────────────────────────────────────┘
```

Each screen has:
- A **Screen composable** that receives the ViewModel via `hiltViewModel()`
- A **Content composable** (pure, previewable) that receives state and callbacks
- A **ViewModel** that exposes `StateFlow<UiState>` and action functions
- A **sealed interface UiState** (Loading, Success, Error)

### 3.2 Repository Pattern

The data layer abstracts data sources from the presentation layer. For MVP, repositories are lightweight since there is no local database (Health Connect is the read source, Momentum API is the write target).

```kotlin
// HealthConnectReader acts as a "repository" for health data
class HealthConnectReader @Inject constructor(
    private val client: HealthConnectClient,
) {
    suspend fun readSteps(start: LocalDate, end: LocalDate): Map<LocalDate, Long>
    suspend fun readActiveCalories(start: LocalDate, end: LocalDate): Map<LocalDate, Double>
    suspend fun readExerciseSessions(start: LocalDate, end: LocalDate): List<ExerciseSessionRecord>
    suspend fun readSleepSessions(start: LocalDate, end: LocalDate): List<SleepSessionRecord>
}

// MomentumApiService acts as a "repository" for remote operations
interface MomentumApiService {
    @POST("health-sync")
    suspend fun postHealthSync(@Body request: HealthSyncRequest): HealthSyncResponse

    @GET("health-sync/status")
    suspend fun getSyncStatus(): SyncStatusResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
}
```

### 3.3 WorkManager Pattern

WorkManager handles reliable background sync that survives app kills and device reboots.

```
┌──────────────────────────────────────────────┐
│              SyncScheduler                    │
│                                              │
│  enqueuePeriodicSync(interval)               │
│       │                                      │
│       │ PeriodicWorkRequest                  │
│       │ - repeatInterval: 15/30/60/120 min   │
│       │ - constraints: NetworkType.CONNECTED  │
│       │ - backoffPolicy: EXPONENTIAL          │
│       v                                      │
│  WorkManager                                 │
│       │                                      │
│       │ schedules                             │
│       v                                      │
│  SyncWorker (CoroutineWorker)                │
│       │                                      │
│       │ 1. Read config from preferences      │
│       │ 2. Ensure valid JWT token             │
│       │ 3. Read Health Connect data           │
│       │ 4. Aggregate per day                  │
│       │ 5. POST to Momentum API              │
│       │ 6. Update lastSyncTimestamp           │
│       │                                      │
│       ├── Result.success()  (sync OK)        │
│       ├── Result.retry()    (transient error) │
│       └── Result.failure()  (permanent error) │
└──────────────────────────────────────────────┘
```

**Manual sync** uses `OneTimeWorkRequest` with the same `SyncWorker`.

---

## 4. Component Diagram

### 4.1 Dependency Graph

```
MomentumApp (@HiltAndroidApp)
     │
     ├── MainActivity (@AndroidEntryPoint)
     │        │
     │        └── NavGraph
     │              ├── SetupScreen ←── SetupViewModel
     │              ├── PermissionsScreen
     │              ├── DashboardScreen ←── DashboardViewModel
     │              └── SettingsScreen ←── SettingsViewModel
     │
     ├── SyncWorker (@HiltWorker)
     │        ├── HealthConnectReader
     │        ├── HealthConnectMapper
     │        ├── MomentumApiService
     │        └── AppPreferences
     │
     └── Hilt Modules
              ├── AppModule (prefs, dispatchers, scheduler)
              ├── NetworkModule (OkHttp, Retrofit, API service)
              └── HealthModule (HC client, reader, mapper)
```

### 4.2 Screen Dependencies

| Screen | ViewModel | Data Dependencies |
|--------|-----------|-------------------|
| SetupScreen | SetupViewModel | MomentumApiService, AppPreferences |
| PermissionsScreen | (none) | HealthConnectClient (permission check) |
| DashboardScreen | DashboardViewModel | HealthConnectReader, MomentumApiService, AppPreferences, SyncScheduler |
| SettingsScreen | SettingsViewModel | AppPreferences, SyncScheduler, MomentumApiService |

---

## 5. Health Connect Integration

### 5.1 Permissions Required

```xml
android.permission.health.READ_STEPS
android.permission.health.READ_ACTIVE_CALORIES_BURNED
android.permission.health.READ_EXERCISE
android.permission.health.READ_SLEEP
android.permission.health.READ_HEART_RATE
android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND
android.permission.health.READ_HEALTH_DATA_HISTORY
```

The app requests **read-only** permissions. It never writes to Health Connect.

### 5.2 Data Reading Strategy

| Data Type | HC Record Type | Reading Method | Output |
|-----------|---------------|----------------|--------|
| Steps | `StepsRecord` | `aggregateGroupByPeriod` (1-day buckets) | `Map<LocalDate, Long>` |
| Active Calories | `ActiveCaloriesBurnedRecord` | `aggregateGroupByPeriod` (1-day buckets) | `Map<LocalDate, Double>` |
| Active Minutes | `ExerciseSessionRecord` | `readRecords` (individual sessions) | Computed: sum of session durations per day |
| Exercise Sessions | `ExerciseSessionRecord` | `readRecords` (individual sessions) | `List<ExerciseSessionRecord>` with metadata |
| Sleep | `SleepSessionRecord` | `readRecords` (individual sessions) | `List<SleepSessionRecord>` with stages |

### 5.3 Health Connect Data Mapping

| Source Health Connect | Extracted Data | Momentum Trackable | Unit |
|----------------------|----------------|-------------------|------|
| `StepsRecord` | Total steps per day | Pas | steps |
| `ActiveCaloriesBurnedRecord` | Total kcal per day | Calories actives | kcal |
| `ExerciseSessionRecord` | Sum of durations per day | Minutes d'activite | min |
| `SleepSessionRecord` | Total duration | Durée sommeil | min |
| `ExerciseSessionRecord` (detailed) | Type, duration, calories, distance | HealthActivity (dedicated model) | - |

### 5.4 Sleep Score Handling

Samsung Health computes a sleep score (0-100) but Health Connect does not natively store this score. Health Connect only stores `SleepSessionRecord` with stages.

**Strategy**:
1. Read `SleepSessionRecord` with their stages
2. If a score is available from Samsung Health metadata, use it
3. Otherwise, compute a simplified score client-side:
   - Base 100, penalties: duration < 7h (-15), low deep sleep (-10), low REM (-10), frequent awakenings (-5 each)
   - Fallback: store total sleep duration in minutes as the value
4. The `score` field is nullable in the API payload. If null, the API stores duration as the fallback value

---

## 6. Sync Strategy

### 6.1 Incremental Sync

The companion reads Health Connect data starting from `lastSyncTimestamp` up to the current time. On first sync (or initial import), it reads the last 30 days.

```
Timeline:
─────────────────────────────────────────────────>
     ^                              ^          ^
     │                              │          │
  lastSyncTimestamp            lastSyncTimestamp  now
  (previous cycle)              (this cycle)
     │                              │          │
     └──── read range ──────────────┘          │
                                               │
                                    (new lastSyncTimestamp)
```

### 6.2 Aggregation

The companion aggregates Health Connect records into daily summaries before sending to the API:

1. **Steps**: `aggregateGroupByPeriod` with `Period.ofDays(1)` returns daily totals
2. **Calories**: Same aggregation approach for `ActiveCaloriesBurnedRecord`
3. **Active minutes**: Sum exercise session durations per day (no HC aggregate available for this)
4. **Sleep**: One session per night, mapped to the wake-up date

### 6.3 Batch POST

A single `POST /health-sync` call sends all data for the sync period:

```kotlin
@Serializable
data class HealthSyncRequest(
    val deviceName: String,        // e.g., "Galaxy S24"
    val syncedAt: String,          // ISO 8601
    val dailyMetrics: List<DailyMetric>,
    val activities: List<ActivityRecord>,
    val sleepSessions: List<SleepRecord>,
)
```

### 6.4 Server-side Upsert

The Momentum API ensures idempotence via upsert operations:

- **DailyEntry**: Upsert on `(trackableId, date)` with `source = "health_connect"`
- **HealthActivity**: Upsert on `(userId, hcRecordId)` using Health Connect's UUID
- **SyncDevice**: Upsert on `(userId, deviceName)` updating `lastSyncAt`

Sending the same data twice produces the same result (idempotence).

### 6.5 Sync Frequency

Configurable via Settings, stored in DataStore:

| Option | WorkManager Interval | Notes |
|--------|---------------------|-------|
| 15 minutes (default) | `repeatInterval = 15, MINUTES` | Minimum supported by WorkManager |
| 30 minutes | `repeatInterval = 30, MINUTES` | |
| 1 hour | `repeatInterval = 1, HOURS` | |
| 2 hours | `repeatInterval = 2, HOURS` | Most battery-friendly |

WorkManager constraints:
- `NetworkType.CONNECTED` - requires active network
- `BatteryNotLow` - skips when battery is critically low

---

## 7. Security

### 7.1 Credential Storage

| Data | Storage | Encryption |
|------|---------|------------|
| Server URL | EncryptedSharedPreferences | AES-256-SIV (key) + AES-256-GCM (value) |
| User email | EncryptedSharedPreferences | AES-256-SIV + AES-256-GCM |
| User password | EncryptedSharedPreferences | AES-256-SIV + AES-256-GCM |
| JWT token | EncryptedSharedPreferences | AES-256-SIV + AES-256-GCM |
| Sync preferences | DataStore (Preferences) | Not encrypted (non-sensitive) |
| Last sync timestamp | DataStore (Preferences) | Not encrypted (non-sensitive) |

All encryption keys are managed by the Android Keystore (hardware-backed on most devices).

### 7.2 Network Security

- **HTTPS only**: All API calls use HTTPS
- **Self-signed certificate support**: For self-hosted Momentum instances, the app provides:
  1. An option to add a custom CA certificate
  2. A development-only toggle to disable certificate verification (with prominent warning)
- **JWT authentication**: Bearer token attached via OkHttp `AuthInterceptor`
- **Token refresh**: The app stores email/password to re-authenticate when the JWT expires. Re-login happens automatically in the interceptor

### 7.3 OkHttp Self-Signed Certificate Configuration

```kotlin
// For self-hosted servers with self-signed certificates
fun createTrustingOkHttpClient(): OkHttpClient {
    val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustManager), SecureRandom())
    }
    return OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustManager)
        .hostnameVerifier { _, _ -> true }
        .build()
}
```

This is only enabled when the user explicitly opts in via Settings. A warning badge is displayed on the Dashboard when certificate verification is disabled.

### 7.4 Data Privacy

- The app stores **no health data locally**. Data flows directly from Health Connect to the Momentum API
- Only sync logs (timestamp, success/failure, record counts) are stored locally for debugging
- Health Connect permissions are read-only; the app never writes health data

---

## 8. Error Handling Strategy

### 8.1 Error Categories

| Category | Examples | Action |
|----------|----------|--------|
| **Network** | No connectivity, timeout, DNS failure | `Result.retry()` in WorkManager; show "Offline" in UI |
| **Server** | HTTP 5xx, server down | `Result.retry()` with exponential backoff; show error in UI |
| **Auth** | HTTP 401, expired token | Attempt re-login; if fails, `Result.failure()` and prompt user |
| **Validation** | HTTP 400, invalid payload | `Result.failure()`; log details for debugging |
| **Health Connect** | Permission revoked, HC unavailable | `Result.failure()`; prompt user to re-grant permissions |
| **Unknown** | Unexpected exceptions | `Result.failure()`; log full stack trace |

### 8.2 WorkManager Retry Policy

```kotlin
val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(interval, timeUnit)
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
    )
    .setBackoffCriteria(
        BackoffPolicy.EXPONENTIAL,
        WorkRequest.MIN_BACKOFF_MILLIS,  // 10 seconds
        TimeUnit.MILLISECONDS,
    )
    .build()
```

Inside `SyncWorker.doWork()`:
```kotlin
return try {
    performSync()
    Result.success()
} catch (e: IOException) {
    if (runAttemptCount < 3) Result.retry() else Result.failure()
} catch (e: HttpException) {
    when (e.code()) {
        401 -> {
            if (tryReLogin()) Result.retry() else Result.failure()
        }
        in 500..599 -> {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
        else -> Result.failure()
    }
}
```

### 8.3 UI Error Display

- Errors in ViewModels are captured in the `UiState.Error` state
- The Dashboard shows a persistent status bar: "Connected" (green), "Syncing..." (blue), "Offline" (yellow), "Error" (red)
- The Settings "Debug Logs" screen shows the last 50 sync events with timestamps, outcomes, and error messages

---

## 9. Key Workflows

### 9.1 Initial Setup Flow

```
1. User installs app from APK
2. App opens → SetupScreen
3. User enters:
   - Momentum server URL (e.g., https://momentum.local:3001)
   - Email
   - Password
4. User taps "Test Connection"
   - App calls POST /auth/login
   - On success: stores JWT + credentials in EncryptedSharedPreferences
   - On failure: shows error message
5. User taps "Next"
   - Navigate to PermissionsScreen
6. User taps "Authorize Health Access"
   - System Health Connect permission dialog opens
   - User grants READ permissions
7. On permission granted:
   - Navigate to DashboardScreen
   - SyncScheduler enqueues PeriodicWorkRequest
   - First sync triggers immediately (OneTimeWorkRequest)
```

### 9.2 Background Sync Flow

```
1. WorkManager triggers SyncWorker (every 15/30/60/120 min)
2. SyncWorker.doWork():
   a. Read config from AppPreferences
   b. Validate/refresh JWT token
   c. Determine sync range: lastSyncTimestamp → now
   d. Read Health Connect data:
      - Steps (aggregateGroupByPeriod)
      - Active calories (aggregateGroupByPeriod)
      - Exercise sessions (readRecords)
      - Sleep sessions (readRecords)
   e. HealthConnectMapper transforms data:
      - Aggregate daily metrics
      - Map exercise sessions to ActivityRecord
      - Map sleep sessions to SleepRecord
   f. Build HealthSyncRequest
   g. POST /health-sync
   h. On success:
      - Update lastSyncTimestamp
      - Log success + counts
      - Return Result.success()
   i. On error:
      - Log error details
      - Return Result.retry() or Result.failure()
```

### 9.3 Manual Sync Flow

```
1. User taps "Synchronize Now" on DashboardScreen
2. DashboardViewModel enqueues OneTimeWorkRequest for SyncWorker
3. UI shows "Syncing..." spinner
4. Same flow as background sync (steps 2a-2i)
5. On completion, ViewModel refreshes dashboard data
```

### 9.4 Initial Import Flow

```
1. User taps "Launch Initial Import" in SettingsScreen
2. SettingsViewModel:
   a. Sets sync range: 30 days ago → now
   b. Enqueues OneTimeWorkRequest with input data (override date range)
3. UI shows progress indicator
4. SyncWorker reads 30 days of Health Connect history
   - Requires READ_HEALTH_DATA_HISTORY permission
5. Batch POST sends all 30 days of data
6. API upserts all records (no duplicates due to idempotence)
```

### 9.5 Token Refresh Flow

```
1. AuthInterceptor detects 401 response
2. Reads stored email/password from EncryptedSharedPreferences
3. Calls POST /auth/login
4. On success:
   - Stores new JWT token
   - Retries original request with new token
5. On failure:
   - Clears stored credentials
   - Sets app state to "disconnected"
   - Next app open redirects to SetupScreen
```

---

## 10. API Integration

### 10.1 Endpoints Used

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `POST` | `/auth/login` | Authenticate and obtain JWT |
| `POST` | `/health-sync` | Send batch health data |
| `GET` | `/health-sync/status` | Check sync status and get trackable goals |

### 10.2 Retrofit Service Interface

```kotlin
interface MomentumApiService {

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest,
    ): LoginResponse

    @POST("health-sync")
    suspend fun postHealthSync(
        @Body request: HealthSyncRequest,
    ): HealthSyncResponse

    @GET("health-sync/status")
    suspend fun getSyncStatus(): SyncStatusResponse
}
```

### 10.3 Request/Response Models

```kotlin
@Serializable
data class HealthSyncRequest(
    val deviceName: String,
    val syncedAt: String,
    val dailyMetrics: List<DailyMetric>,
    val activities: List<ActivityRecord>,
    val sleepSessions: List<SleepRecord>,
)

@Serializable
data class DailyMetric(
    val date: String,                // "YYYY-MM-DD"
    val steps: Long?,
    val activeCalories: Double?,
    val activeMinutes: Double?,
)

@Serializable
data class ActivityRecord(
    val hcRecordId: String,
    val date: String,
    val startTime: String,           // ISO 8601
    val endTime: String,
    val activityType: String,
    val title: String?,
    val durationMinutes: Double,
    val calories: Double?,
    val distance: Double?,           // metres
    val heartRateAvg: Int?,
    val sourceApp: String?,
)

@Serializable
data class SleepRecord(
    val date: String,
    val startTime: String,
    val endTime: String,
    val durationMinutes: Double,
    val score: Int?,
    val stages: List<SleepStage>?,
)

@Serializable
data class SleepStage(
    val stage: String,               // "awake", "light", "deep", "rem", "sleeping"
    val startTime: String,
    val endTime: String,
)

@Serializable
data class HealthSyncResponse(
    val synced: SyncCounts,
    val device: DeviceInfo,
)

@Serializable
data class SyncCounts(
    val dailyMetrics: Int,
    val activities: Int,
    val sleepSessions: Int,
)
```

---

## 11. Performance Considerations

### 11.1 Battery

- WorkManager respects Android Doze mode and App Standby buckets
- Network constraint ensures no wasted wake-ups when offline
- `BatteryNotLow` constraint prevents sync during critical battery
- Health Connect reads are efficient (aggregate queries instead of raw record iteration)

### 11.2 Network

- Single batch POST per sync cycle (not one request per metric)
- Request payload is compact JSON (only dates with data, null fields omitted)
- OkHttp connection pooling and HTTP/2 reduce overhead

### 11.3 APK Size

- Target: under 10 MB
- Jetpack Compose + Material 3 are the largest dependencies
- No image assets beyond launcher icon and a few vector drawables
- ProGuard/R8 minification enabled for release builds

---

## 12. Future Considerations

These items are explicitly out of scope for MVP but may be considered later:

- **Widget**: Home screen widget showing the 3 rings
- **Notifications**: Daily summary notification with sync results
- **Multiple devices**: Support syncing from multiple Health Connect sources
- **Wear OS**: Direct companion app on Galaxy Watch
- **Room database**: Local cache for offline viewing of historical data
- **Instrumented tests**: Compose UI tests and Health Connect integration tests
