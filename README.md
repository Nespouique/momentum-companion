# Momentum Companion

**A lightweight Android companion app for syncing Samsung Health data to your self-hosted Momentum fitness tracker.**

![Android](https://img.shields.io/badge/Android-14%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)

---

## Overview

Momentum Companion bridges Samsung Health and the [Momentum API](https://github.com/your-org/momentum) by reading health data through Android's Health Connect platform and pushing it to your self-hosted server on a recurring schedule. Once configured, syncs happen automatically in the background with no manual intervention required.

## Features

- **Steps** -- daily step counts aggregated from Health Connect
- **Active Calories** -- energy expenditure from activities throughout the day
- **Active Minutes** -- total active duration per day
- **Sleep Sessions** -- full sleep records including stage breakdowns (awake, light, deep, REM)
- **Exercise Sessions** -- individual workout records with duration, calories, distance, and heart rate
- **Background Sync** -- periodic sync via WorkManager (default every 15 minutes) with network and battery constraints
- **Manual Sync** -- on-demand sync from the dashboard
- **Sync Logs** -- built-in log viewer for troubleshooting sync history

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.3 with kotlinx.serialization |
| UI | Jetpack Compose with Material 3 |
| DI | Hilt (Dagger) via KSP |
| Networking | Retrofit 2 + OkHttp 5 |
| Background Work | WorkManager |
| Health Data | Health Connect SDK (androidx.health.connect) |
| Secure Storage | EncryptedSharedPreferences + DataStore Preferences |
| Testing | JUnit 5, MockK, kotlinx-coroutines-test |

## Requirements

- **Android 14+** (API level 34) -- minSdk 34, targetSdk 35
- **Health Connect** app installed on the device (provides the Health Connect data layer)
- **Samsung Health** (or another health app) writing data into Health Connect
- **Momentum API** server running and accessible from the device's network

## Setup

1. **Clone the repository**

   ```bash
   git clone https://github.com/your-org/momentum-companion.git
   cd momentum-companion
   ```

2. **Open in Android Studio** (Ladybug 2024.2+ or later recommended)

3. **Build the debug APK**

   ```bash
   ./gradlew assembleDebug
   ```

   The output APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

4. **Install on a physical device**

   Health Connect requires a real device -- emulators have limited Health Connect support.

   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

5. **Configure the app**

   On first launch the Setup screen will prompt for:
   - **Server URL** -- base URL of your Momentum API instance (e.g. `https://momentum.example.com/api/`)
   - **Email and Password** -- your Momentum account credentials

   After authentication the app navigates to the Permissions screen to request Health Connect access, then to the Dashboard.

## Architecture

The app follows **MVVM + Repository** with constructor-based dependency injection via Hilt.

```
UI (Compose Screens + ViewModels)
        |
   Repositories / Mappers
        |
  +-----------+-----------+
  |           |           |
Health     Retrofit     WorkManager
Connect    API Client   (SyncWorker)
```

- **ViewModels** expose UI state as `StateFlow` and handle user actions.
- **Repositories** abstract data sources (Health Connect reads, API calls, local preferences).
- **SyncWorker** runs as a periodic `CoroutineWorker` under WorkManager, reading Health Connect data and posting it to the Momentum API.
- **Hilt modules** (`AppModule`, `NetworkModule`, `HealthModule`) provide all injectable dependencies.

## Project Structure

```
app/src/main/java/com/momentum/companion/
|-- MomentumApp.kt                  # Application class (Hilt entry point)
|-- MainActivity.kt                 # Single-activity host
|-- navigation/
|   |-- NavGraph.kt                 # Compose Navigation graph (Setup, Permissions, Dashboard, Settings, Logs)
|-- ui/
|   |-- setup/                      # Server URL + login screen
|   |-- permissions/                # Health Connect permission request
|   |-- dashboard/                  # Sync status, last sync time, manual sync trigger
|   |-- settings/                   # Server config, sync interval, disconnect, log viewer
|   |-- theme/                      # Material 3 color, typography, theme definitions
|-- data/
|   |-- api/
|   |   |-- MomentumApiService.kt   # Retrofit interface
|   |   |-- AuthInterceptor.kt      # OkHttp interceptor for Bearer token
|   |   |-- models/                 # Request/response DTOs (HealthSyncRequest, AuthModels, etc.)
|   |-- healthconnect/
|   |   |-- HealthConnectReader.kt  # Reads steps, calories, sleep, exercises from Health Connect
|   |   |-- HealthConnectMapper.kt  # Maps Health Connect records to API models
|   |-- preferences/
|   |   |-- AppPreferences.kt       # EncryptedSharedPreferences wrapper
|   |-- log/
|       |-- SyncLogEntry.kt         # Sync log data model
|       |-- SyncLogRepository.kt    # Persists and retrieves sync history
|-- sync/
|   |-- SyncWorker.kt              # CoroutineWorker that performs the sync
|   |-- SyncScheduler.kt           # Schedules periodic and one-time sync work
|-- di/
    |-- AppModule.kt               # General app-level bindings
    |-- NetworkModule.kt           # Retrofit, OkHttp, API service
    |-- HealthModule.kt            # Health Connect client
```

## API Endpoints

The app communicates with three Momentum API endpoints:

| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/login` | Authenticate with email/password, returns a JWT token |
| `POST` | `/health-sync` | Submit health data (daily metrics, activities, sleep sessions) |
| `GET`  | `/health-sync/status` | Retrieve the last successful sync timestamp and status |

All `/health-sync` endpoints require an `Authorization: Bearer <token>` header.

### Request payload structure (`POST /health-sync`)

```json
{
  "deviceName": "Galaxy S24",
  "syncedAt": "2026-02-06T12:00:00Z",
  "dailyMetrics": [
    { "date": "2026-02-06", "steps": 8432, "activeCalories": 312, "activeMinutes": 47 }
  ],
  "activities": [
    {
      "hcRecordId": "...",
      "date": "2026-02-06",
      "startTime": "...",
      "endTime": "...",
      "activityType": "running",
      "title": null,
      "durationMinutes": 32.5,
      "calories": 285.0,
      "distance": 4.8,
      "heartRateAvg": 145,
      "sourceApp": "com.samsung.shealth"
    }
  ],
  "sleepSessions": [
    {
      "date": "2026-02-06",
      "startTime": "...",
      "endTime": "...",
      "durationMinutes": 462.0,
      "score": null,
      "stages": [
        { "stage": "deep", "startTime": "...", "endTime": "..." }
      ]
    }
  ]
}
```

## Health Connect Data & Limitations

Samsung Health ne synchronise qu'une partie de ses donnees vers Health Connect. Voir **[docs/health-connect-data-analysis.md](docs/health-connect-data-analysis.md)** pour l'analyse complete :

- Quels types HC Samsung expose reellement (steps, exercices) vs ce qui manque (calories passives, minutes actives)
- Impact sur la reconstruction des "3 rings" Samsung
- Strategie d'estimation des minutes actives et calories a partir des pas

Un ecran **HC Explorer** (Settings > Debug > Explorer Health Connect) permet d'inspecter les 40 types de records HC en temps reel.

## Development

### Run unit tests

```bash
./gradlew test
```

Tests use JUnit 5 with MockK for mocking and kotlinx-coroutines-test for coroutine testing.

### Lint

```bash
./gradlew lint
```

### Build release APK

```bash
./gradlew assembleRelease
```

Release builds have minification and resource shrinking enabled via R8.

### Code style

The project follows standard Kotlin coding conventions with trailing commas enabled. See `docs/architecture/coding-standards.md` for full guidelines.

## License

This project is private. All rights reserved.
