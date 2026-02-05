# Momentum Companion - Source Tree

## 1. Overview

This document provides a complete map of the Momentum Companion Android project structure, explaining the purpose of each directory and key files.

---

## 2. Root Directory

```
momentum-companion/
├── .github/                         # GitHub Actions workflows (CI)
├── .gradle/                         # Gradle cache (git-ignored)
├── .idea/                           # Android Studio settings (git-ignored)
├── app/                             # Main Android application module
├── docs/                            # Project documentation
├── gradle/
│   ├── libs.versions.toml           # Gradle Version Catalog
│   └── wrapper/
│       ├── gradle-wrapper.jar       # Gradle wrapper binary
│       └── gradle-wrapper.properties # Gradle version config
├── .gitignore                       # Git ignore patterns
├── build.gradle.kts                 # Root build script (plugins, repositories)
├── gradle.properties                # Gradle JVM and Android settings
├── gradlew                          # Gradle wrapper (Unix)
├── gradlew.bat                      # Gradle wrapper (Windows)
├── local.properties                 # Local SDK path (git-ignored)
├── README.md                        # Project readme
└── settings.gradle.kts              # Project settings (module includes)
```

---

## 3. Application Module (app/)

```
app/
├── build/                           # Build output (git-ignored)
├── src/
│   ├── main/
│   │   ├── java/com/momentum/companion/
│   │   │   ├── MomentumApp.kt                  # Application class (@HiltAndroidApp)
│   │   │   ├── MainActivity.kt                 # Single Activity (Compose host)
│   │   │   ├── di/                              # Hilt dependency injection
│   │   │   │   ├── AppModule.kt                 # App-wide bindings (prefs, dispatchers)
│   │   │   │   ├── NetworkModule.kt             # OkHttp, Retrofit, API service
│   │   │   │   └── HealthModule.kt              # Health Connect client bindings
│   │   │   ├── data/                            # Data layer
│   │   │   │   ├── api/                         # Network / Momentum API
│   │   │   │   │   ├── MomentumApiService.kt    # Retrofit interface
│   │   │   │   │   ├── AuthInterceptor.kt       # OkHttp interceptor (JWT injection)
│   │   │   │   │   └── models/                  # Request/Response DTOs
│   │   │   │   │       ├── HealthSyncRequest.kt
│   │   │   │   │       ├── HealthSyncResponse.kt
│   │   │   │   │       ├── SyncStatusResponse.kt
│   │   │   │   │       └── AuthModels.kt
│   │   │   │   ├── healthconnect/               # Health Connect data source
│   │   │   │   │   ├── HealthConnectReader.kt   # Read steps, calories, exercises, sleep
│   │   │   │   │   └── HealthConnectMapper.kt   # HC records -> API DTOs
│   │   │   │   └── preferences/                 # Local storage
│   │   │   │       └── AppPreferences.kt        # EncryptedSharedPrefs + DataStore
│   │   │   ├── sync/                            # Background sync
│   │   │   │   ├── SyncWorker.kt                # WorkManager CoroutineWorker
│   │   │   │   └── SyncScheduler.kt             # Schedule/cancel PeriodicWorkRequest
│   │   │   ├── ui/                              # Presentation layer
│   │   │   │   ├── setup/                       # Setup / login screen
│   │   │   │   │   ├── SetupScreen.kt           # Composable
│   │   │   │   │   └── SetupViewModel.kt        # ViewModel
│   │   │   │   ├── permissions/                 # Health Connect permissions screen
│   │   │   │   │   └── PermissionsScreen.kt     # Composable
│   │   │   │   ├── dashboard/                   # Main dashboard screen
│   │   │   │   │   ├── DashboardScreen.kt       # Composable (3 rings + activities)
│   │   │   │   │   └── DashboardViewModel.kt    # ViewModel
│   │   │   │   ├── settings/                    # Settings screen
│   │   │   │   │   ├── SettingsScreen.kt        # Composable
│   │   │   │   │   └── SettingsViewModel.kt     # ViewModel
│   │   │   │   ├── theme/                       # Material 3 theme
│   │   │   │   │   ├── Theme.kt                 # Dark theme definition
│   │   │   │   │   ├── Color.kt                 # Color palette
│   │   │   │   │   └── Type.kt                  # Typography
│   │   │   │   └── components/                  # Shared composables
│   │   │   │       ├── ProgressRing.kt          # Circular progress indicator
│   │   │   │       ├── SyncStatusCard.kt        # Connection & sync status
│   │   │   │       └── ActivityListItem.kt      # Exercise session list item
│   │   │   └── navigation/                      # Navigation
│   │   │       └── NavGraph.kt                  # Navigation destinations & graph
│   │   ├── AndroidManifest.xml                  # App manifest (permissions, activities)
│   │   └── res/                                 # Android resources
│   │       ├── drawable/                        # Icons and drawables
│   │       ├── mipmap-*/                        # App launcher icons
│   │       ├── values/
│   │       │   ├── strings.xml                  # String resources
│   │       │   ├── colors.xml                   # Color resources
│   │       │   └── themes.xml                   # Legacy XML theme (splash screen)
│   │       └── xml/
│   │           └── health_permissions.xml        # Health Connect permission declarations
│   └── test/
│       └── java/com/momentum/companion/
│           ├── data/
│           │   ├── healthconnect/
│           │   │   └── HealthConnectMapperTest.kt
│           │   └── api/
│           │       └── MomentumApiServiceTest.kt
│           ├── sync/
│           │   └── SyncWorkerTest.kt
│           └── ui/
│               ├── setup/
│               │   └── SetupViewModelTest.kt
│               ├── dashboard/
│               │   └── DashboardViewModelTest.kt
│               └── settings/
│                   └── SettingsViewModelTest.kt
└── build.gradle.kts                             # App module build script
```

---

## 4. Key Files Explained

### 4.1 Application Entry Points

| File | Purpose |
|------|---------|
| `MomentumApp.kt` | `@HiltAndroidApp` Application class. Initializes Hilt DI container |
| `MainActivity.kt` | Single Activity host for Jetpack Compose. Sets up `NavHost` and theme |
| `NavGraph.kt` | Defines navigation destinations: Setup, Permissions, Dashboard, Settings |

### 4.2 Data Layer

| File | Purpose |
|------|---------|
| `MomentumApiService.kt` | Retrofit interface defining HTTP endpoints (`POST /health-sync`, `GET /health-sync/status`, `POST /auth/login`) |
| `AuthInterceptor.kt` | OkHttp interceptor that attaches the JWT Bearer token to outgoing requests |
| `HealthConnectReader.kt` | Reads `StepsRecord`, `ActiveCaloriesBurnedRecord`, `ExerciseSessionRecord`, `SleepSessionRecord` from Health Connect |
| `HealthConnectMapper.kt` | Transforms raw Health Connect records into `HealthSyncRequest` DTOs (daily aggregation, exercise mapping) |
| `AppPreferences.kt` | Wraps `EncryptedSharedPreferences` (for credentials/token) and `DataStore` (for sync settings, last sync timestamp) |

### 4.3 Sync Layer

| File | Purpose |
|------|---------|
| `SyncWorker.kt` | `CoroutineWorker` that performs the full sync cycle: read HC data, aggregate, POST to API, update last sync |
| `SyncScheduler.kt` | Manages `PeriodicWorkRequest` registration and cancellation. Handles frequency changes |

### 4.4 UI Layer

| File | Purpose |
|------|---------|
| `SetupScreen.kt` | Server URL, email, password inputs. Tests connection to Momentum API |
| `PermissionsScreen.kt` | Explains required Health Connect permissions. Launches permission request |
| `DashboardScreen.kt` | Three progress rings (steps, active minutes, calories), sync status, activity list |
| `SettingsScreen.kt` | Server info, sync frequency selector, initial import, debug logs, disconnect |
| `Theme.kt` | Material 3 dark theme matching Momentum web app color scheme |

### 4.5 Dependency Injection

| File | Purpose |
|------|---------|
| `AppModule.kt` | Provides `AppPreferences`, coroutine dispatchers, `SyncScheduler` |
| `NetworkModule.kt` | Provides `OkHttpClient` (with auth interceptor, self-signed cert support), `Retrofit`, `MomentumApiService` |
| `HealthModule.kt` | Provides `HealthConnectClient`, `HealthConnectReader`, `HealthConnectMapper` |

---

## 5. Documentation (docs/)

```
docs/
├── architecture/                    # Sharded architecture docs
│   ├── index.md                     # This index
│   ├── coding-standards.md          # Kotlin/Compose conventions
│   ├── tech-stack.md                # Technology reference
│   └── source-tree.md              # This file
├── architecture.md                  # Main architecture document
└── stories/                         # User stories for each epic
```

---

## 6. Build Configuration

### 6.1 Root Build Script (`build.gradle.kts`)

- Declares Gradle plugins (AGP, Kotlin, Hilt, KSP, kotlinx.serialization)
- Does not apply them (applied at module level)

### 6.2 App Build Script (`app/build.gradle.kts`)

- Applies plugins: `com.android.application`, `kotlin-android`, `kotlin-kapt` or `ksp`, `dagger.hilt.android.plugin`, `kotlinx-serialization`
- Configures `compileSdk`, `minSdk`, `targetSdk`
- Declares all dependencies (Compose, Hilt, Retrofit, Health Connect, WorkManager, etc.)
- Configures JUnit 5 for unit tests

### 6.3 Version Catalog (`gradle/libs.versions.toml`)

Centralizes all dependency versions in a single file. Referenced in `build.gradle.kts` via `libs.*` accessors.

### 6.4 Settings (`settings.gradle.kts`)

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolution {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "momentum-companion"
include(":app")
```

---

## 7. Android Manifest Highlights

```xml
<!-- Health Connect permissions -->
<uses-permission android:name="android.permission.health.READ_STEPS" />
<uses-permission android:name="android.permission.health.READ_ACTIVE_CALORIES_BURNED" />
<uses-permission android:name="android.permission.health.READ_EXERCISE" />
<uses-permission android:name="android.permission.health.READ_SLEEP" />
<uses-permission android:name="android.permission.health.READ_HEART_RATE" />
<uses-permission android:name="android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND" />
<uses-permission android:name="android.permission.health.READ_HEALTH_DATA_HISTORY" />

<!-- Network -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- WorkManager foreground service (sync notifications) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

---

## 8. Navigation Flow

```
App Launch
    │
    ├── No credentials saved? ──> SetupScreen
    │                                  │
    │                              [Login success]
    │                                  │
    │                                  v
    │                          PermissionsScreen
    │                                  │
    │                           [Permissions granted]
    │                                  │
    ├──────────────────────────────────┘
    │
    v
DashboardScreen  ──[gear icon]──> SettingsScreen
```

---

## 9. Test Source Organization

```
src/test/java/com/momentum/companion/
├── data/
│   ├── healthconnect/
│   │   └── HealthConnectMapperTest.kt    # HC record → DTO transformations
│   └── api/
│       └── MomentumApiServiceTest.kt     # Retrofit service contract tests
├── sync/
│   └── SyncWorkerTest.kt                 # Sync flow, retry logic, error handling
└── ui/
    ├── setup/
    │   └── SetupViewModelTest.kt         # Login flow, validation, token storage
    ├── dashboard/
    │   └── DashboardViewModelTest.kt     # Data loading, sync trigger, state management
    └── settings/
        └── SettingsViewModelTest.kt      # Frequency change, disconnect, import
```
