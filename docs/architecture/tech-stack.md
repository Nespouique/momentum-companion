# Momentum Companion - Tech Stack

## 1. Overview

Momentum Companion is a **native Android application** built with Kotlin and Jetpack Compose. It acts as a bridge between Samsung Health (via Health Connect) and the Momentum API, synchronizing health metrics automatically in the background.

---

## 2. Project Type

```
momentum-companion/          # Standalone Android project (polyrepo)
├── app/                     # Single-module Android application
├── gradle/                  # Gradle wrapper
├── docs/                    # Architecture and story documentation
├── build.gradle.kts         # Root build script
└── settings.gradle.kts      # Project settings
```

**Build System**: Gradle with Kotlin DSL (`.kts`)

---

## 3. Core Stack

### 3.1 Language & SDK

| Technology | Version | Purpose |
|------------|---------|---------|
| **Kotlin** | 2.x | Primary language |
| **Min SDK** | API 34 (Android 14) | Health Connect integrated natively |
| **Target SDK** | API 35 (Android 15) | READ_HEALTH_DATA_IN_BACKGROUND support |
| **Compile SDK** | API 35 | Latest compilation target |
| **Gradle** | 8.x | Build system |
| **AGP** | 8.x | Android Gradle Plugin |

### 3.2 Jetpack Compose (UI)

| Technology | Package | Purpose |
|------------|---------|---------|
| **Compose BOM** | `androidx.compose:compose-bom` | Version alignment for all Compose libs |
| **Compose UI** | `androidx.compose.ui:ui` | Core Compose UI toolkit |
| **Compose Material 3** | `androidx.compose.material3:material3` | Material Design 3 components |
| **Compose Tooling** | `androidx.compose.ui:ui-tooling` | Preview and debug tools |
| **Compose Navigation** | `androidx.navigation:navigation-compose` | Type-safe navigation |

### 3.3 Architecture Components

| Technology | Package | Purpose |
|------------|---------|---------|
| **Hilt** | `com.google.dagger:hilt-android` | Dependency injection |
| **Hilt Navigation Compose** | `androidx.hilt:hilt-navigation-compose` | `hiltViewModel()` in Compose |
| **ViewModel** | `androidx.lifecycle:lifecycle-viewmodel-compose` | MVVM ViewModel |
| **Lifecycle Runtime** | `androidx.lifecycle:lifecycle-runtime-compose` | `collectAsStateWithLifecycle` |

### 3.4 Networking

| Technology | Package | Purpose |
|------------|---------|---------|
| **Retrofit** | `com.squareup.retrofit2:retrofit` | Type-safe HTTP client |
| **OkHttp** | `com.squareup.okhttp3:okhttp` | HTTP engine, interceptors, self-signed cert support |
| **OkHttp Logging** | `com.squareup.okhttp3:logging-interceptor` | Debug HTTP logging |
| **kotlinx.serialization** | `org.jetbrains.kotlinx:kotlinx-serialization-json` | JSON serialization |
| **Retrofit Serialization Converter** | `com.squareup.retrofit2:converter-kotlinx-serialization` | Bridge Retrofit + kotlinx.serialization |

### 3.5 Health

| Technology | Package | Purpose |
|------------|---------|---------|
| **Health Connect Client** | `androidx.health.connect:connect-client` | Read Samsung Health data via Health Connect API |

### 3.6 Background Processing

| Technology | Package | Purpose |
|------------|---------|---------|
| **WorkManager** | `androidx.work:work-runtime-ktx` | Reliable background sync |
| **Hilt WorkManager** | `androidx.hilt:hilt-work` | Inject dependencies into Workers |

### 3.7 Storage

| Technology | Package | Purpose |
|------------|---------|---------|
| **EncryptedSharedPreferences** | `androidx.security:security-crypto` | Encrypted credential storage (AES-256, Android Keystore) |
| **DataStore Preferences** | `androidx.datastore:datastore-preferences` | Type-safe preference storage (sync settings, last sync timestamp) |

### 3.8 Testing

| Technology | Package | Purpose |
|------------|---------|---------|
| **JUnit 5** | `org.junit.jupiter:junit-jupiter` | Unit test framework |
| **MockK** | `io.mockk:mockk` | Kotlin-first mocking library |
| **Kotlin Coroutines Test** | `org.jetbrains.kotlinx:kotlinx-coroutines-test` | Coroutine test utilities |
| **Turbine** | `app.cash.turbine:turbine` | Flow testing (optional) |

---

## 4. Development Tools

### 4.1 Build & Quality

| Technology | Purpose |
|------------|---------|
| **ktlint** (Gradle plugin) | Kotlin code formatting and linting |
| **Kotlin Compiler Plugin (Compose)** | Compose compiler integration |
| **KSP** | Kotlin Symbol Processing for Hilt annotation processing |

### 4.2 Recommended IDE

- **Android Studio Ladybug** (2024.2+) or newer, with plugins:
  - Kotlin
  - Compose Multiplatform (bundled)
  - Android Hilt Navigation (bundled)

---

## 5. Available Gradle Tasks

### 5.1 Build & Run

| Task | Command | Description |
|------|---------|-------------|
| Build debug APK | `./gradlew assembleDebug` | Build debug variant |
| Build release APK | `./gradlew assembleRelease` | Build release variant (requires signing) |
| Install debug | `./gradlew installDebug` | Build and install on connected device |
| Clean | `./gradlew clean` | Remove build artifacts |

### 5.2 Testing

| Task | Command | Description |
|------|---------|-------------|
| Unit tests | `./gradlew test` | Run all unit tests |
| Unit tests (debug) | `./gradlew testDebugUnitTest` | Run debug variant unit tests |
| Test report | `./gradlew testDebugUnitTest --info` | Run with verbose output |

### 5.3 Code Quality

| Task | Command | Description |
|------|---------|-------------|
| Lint check | `./gradlew ktlintCheck` | Check Kotlin formatting |
| Lint format | `./gradlew ktlintFormat` | Auto-fix Kotlin formatting |
| Android Lint | `./gradlew lint` | Run Android static analysis |

---

## 6. Version Requirements

### 6.1 Minimum Versions

| Requirement | Version |
|-------------|---------|
| Android Studio | Ladybug 2024.2+ |
| JDK | 17 |
| Gradle | 8.x |
| AGP | 8.x |
| Kotlin | 2.x |
| Android Device/Emulator | API 34+ (Android 14) |

### 6.2 Target Device

- **Primary**: Samsung Galaxy phones with Galaxy Watch (Samsung Health + Health Connect)
- **Secondary**: Any Android 14+ device with Health Connect-compatible health app

---

## 7. Development Workflow Notes

### 7.1 Important: Health Connect Testing

Health Connect requires a **real device or recent emulator image** (API 34+). The emulator must have Health Connect pre-installed or installed from Play Store.

For local development without a device:
- Unit tests mock `HealthConnectClient`
- Composable previews use fake data
- Retrofit calls can be tested against a local Momentum API instance

### 7.2 Build Verification

```bash
# Check compilation
./gradlew compileDebugKotlin

# Run unit tests
./gradlew testDebugUnitTest

# Full build
./gradlew assembleDebug

# Lint
./gradlew ktlintCheck lint
```

### 7.3 Self-Signed Certificate Testing

When developing against a self-hosted Momentum instance with a self-signed certificate:
1. The app provides an option to trust custom CA certificates
2. For development, certificate verification can be disabled via a setting (with a warning displayed to the user)
3. Production builds should always validate certificates

---

## 8. Dependency Version Management

### 8.1 Strategy

- Use **Gradle Version Catalog** (`gradle/libs.versions.toml`) for centralized version management
- Use **Compose BOM** to align all Compose library versions
- Pin major versions; allow minor/patch updates

### 8.2 Version Catalog Example

```toml
[versions]
kotlin = "2.1.x"
compose-bom = "2024.xx.xx"
hilt = "2.x"
retrofit = "2.x"
health-connect = "1.x"
workmanager = "2.x"
junit5 = "5.x"
mockk = "1.x"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
health-connect = { group = "androidx.health.connect", name = "connect-client", version.ref = "health-connect" }
```
