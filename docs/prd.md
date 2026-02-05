# Momentum Companion Android App - Product Requirements Document (PRD)

## 1. Goals and Background Context

### Goals

- Synchroniser automatiquement les donnees de sante Samsung Health vers l'API Momentum via Health Connect
- Recuperer les 3 metriques de l'anneau Samsung (pas, minutes d'activite, calories actives) + le score de sommeil + les activites/entrainements
- Fournir une app companion Android minimaliste qui tourne en background sans intervention utilisateur
- Alimenter le systeme de trackables Momentum existant (Epic 3) avec des donnees auto-synchees
- Conserver l'approche self-hosted : aucune dependance cloud tierce (pas de Firebase, pas de serveur intermediaire)

### Background Context

Momentum est une app fitness self-hosted (Next.js + Express + PostgreSQL) qui gere les programmes de musculation et le suivi d'habitudes. Les metriques quotidiennes (pas, activite, calories) sont actuellement saisies manuellement. Samsung Health collecte ces donnees via la montre Galaxy mais ne propose aucune API REST. Health Connect (Android) est le seul point d'acces programmatique a ces donnees. Ce PRD specifie une app companion Android native qui fait le pont entre Health Connect et l'API Momentum.

### Repository Scope

Ce document concerne le repository **`momentum-companion`** (polyrepo Android). Les modifications API Momentum (modeles de donnees, endpoints) vivent dans le monorepo `momentum` et sont documentees ici comme contexte uniquement. Voir la section Epic 1 pour les details.

### Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-02-05 | 1.0 | Creation initiale (adapte du PRD source momentum repo) | John (PM) |

---

## 2. Requirements

### Functional Requirements

- **FR1**: L'app companion lit les donnees Steps, ActiveCaloriesBurned, ExerciseSession, et SleepSession depuis Health Connect
- **FR2**: L'app calcule les minutes d'activite a partir des ExerciseSession (duree totale des sessions de la journee)
- **FR3**: L'app synchronise les donnees vers l'API Momentum via des appels REST authentifies
- **FR4**: La synchronisation se fait en arriere-plan via WorkManager a intervalle configurable (defaut : 15 minutes)
- **FR5**: L'app envoie un batch de donnees journalieres (date + valeurs) et non des records individuels
- **FR6**: La synchronisation est idempotente : renvoyer les memes donnees ne cree pas de doublons (upsert par date + type)
- **FR7**: L'app stocke localement le timestamp du dernier sync reussi pour ne pas relire tout l'historique
- **FR8**: L'ecran principal affiche : statut de connexion, dernier sync, et les 3 anneaux du jour (pas/activite/calories)
- **FR9**: L'ecran de configuration permet de saisir : URL de l'API Momentum, email, mot de passe
- **FR10**: L'app demande les permissions Health Connect au premier lancement avec explication claire
- **FR11**: L'API Momentum expose un endpoint bulk `/health-sync` qui recoit les donnees de sante *(implemented in momentum repo)*
- **FR12**: L'API Momentum cree automatiquement les TrackableItems systeme (pas, activite, calories, sommeil) s'ils n'existent pas *(implemented in momentum repo)*
- **FR13**: L'API Momentum stocke les activites/entrainements Health Connect dans un modele dedie `HealthActivity` *(implemented in momentum repo)*
- **FR14**: Les DailyEntry creees par la sync sont marquees `source: "health_connect"` pour les distinguer des saisies manuelles *(implemented in momentum repo)*
- **FR15**: L'app supporte la lecture d'historique (>30 jours) via la permission `READ_HEALTH_DATA_HISTORY` pour le sync initial

### Non-Functional Requirements

- **NFR1**: L'app companion doit consommer un minimum de batterie (WorkManager respecte les contraintes Doze mode)
- **NFR2**: L'app doit fonctionner sur Android 14+ (API 34+) qui inclut Health Connect nativement
- **NFR3**: La communication API utilise HTTPS exclusivement
- **NFR4**: Les credentials sont stockes dans EncryptedSharedPreferences (Android Keystore)
- **NFR5**: L'APK final doit faire moins de 10 Mo
- **NFR6**: L'app ne doit jamais planter silencieusement : les erreurs de sync sont loguees et visibles dans l'UI
- **NFR7**: L'app doit gerer la perte de connectivite gracieusement (retry au prochain cycle WorkManager)

---

## 3. Technical Assumptions

### Repository Structure

- **Polyrepo** : Projet Android separe du monorepo Momentum
- Nom du repo : `momentum-companion`
- Les modifications API Momentum restent dans le monorepo `momentum`

### Tech Stack - Android Companion

| Composant | Choix | Justification |
|-----------|-------|---------------|
| Langage | **Kotlin** | Standard Android, interop Health Connect SDK |
| Min SDK | **API 34 (Android 14)** | Health Connect integre nativement |
| Target SDK | **API 35 (Android 15)** | Support READ_HEALTH_DATA_IN_BACKGROUND |
| UI | **Jetpack Compose** | UI moderne, minimal, declaratif |
| Health | **Health Connect Jetpack SDK** (`androidx.health.connect:connect-client`) | SDK officiel Google |
| Background | **WorkManager** | Fiable, respecte Doze, survit aux redemarrages |
| HTTP | **Ktor Client** ou **Retrofit** | Leger pour appels REST |
| Storage | **EncryptedSharedPreferences** | Credentials securises |
| DI | **Hilt** | Standard Android pour injection |
| Serialization | **kotlinx.serialization** | Performant, natif Kotlin |

### Tech Stack - API Momentum (contexte, implemented in momentum repo)

| Composant | Existant | Notes |
|-----------|----------|-------|
| Framework | Express.js + TypeScript | Ajout de nouvelles routes |
| ORM | Prisma 7.x | Nouveaux modeles a ajouter |
| Database | PostgreSQL 16 | Nouvelles tables |
| Auth | JWT Bearer | Reutilisation du systeme existant |

### Testing Requirements

- **Android** : Tests unitaires (JUnit 5 + MockK) pour la logique de sync et transformation des donnees
- **API** : Tests d'integration pour les endpoints health-sync *(in momentum repo)*

---

## 4. Architecture Overview

### Data Flow

```
Samsung Health
     |
     | (sync automatique Android)
     v
Health Connect (on-device)
     |
     | (Health Connect Jetpack SDK)
     v
Momentum Companion App (Android)        <-- THIS REPO
     |
     | (HTTP POST /health-sync)
     v
Momentum API (Express.js)               <-- momentum repo
     |
     | (Prisma upsert)
     v
PostgreSQL
     |
     | (query existantes)
     v
Momentum Web (Next.js PWA)
```

### Health Connect Data Mapping

| Source Health Connect | Type HC | Donnee extraite | Trackable Momentum | Unit |
|----------------------|---------|-----------------|-------------------|------|
| `StepsRecord` | Aggregate par jour | Total pas du jour | Pas | pas |
| `ActiveCaloriesBurnedRecord` | Aggregate par jour | Total kcal actives du jour | Calories actives | kcal |
| `ExerciseSessionRecord` | Liste sessions du jour | Somme des durees | Minutes d'activite | min |
| `SleepSessionRecord` | Session de nuit | Duree totale | Durée sommeil | min |
| `ExerciseSessionRecord` | Liste detaillee | Type, duree, calories, distance | HealthActivity (modele dedie) | - |

### Sync Strategy

1. **Incremental sync** : Lire depuis `lastSyncTimestamp` jusqu'a maintenant
2. **Aggregation locale** : Le companion agregge par jour avant d'envoyer
3. **Batch POST** : Un seul appel API avec toutes les donnees de la periode
4. **Upsert serveur** : L'API fait un upsert sur `(trackableId, date)` pour les DailyEntry
5. **Idempotence** : Envoyer les memes donnees 2 fois produit le meme resultat

---

## 5. Data Models

### 5.1 API Momentum Models (contexte, implemented in momentum repo)

Les modeles suivants sont implementes dans le monorepo Momentum. Ils sont documentes ici pour que le developpeur Android comprenne la structure des donnees cote serveur.

#### Modele : `HealthActivity`

```prisma
model HealthActivity {
  id              String   @id @default(uuid())
  userId          String   @map("user_id")
  user            User     @relation(fields: [userId], references: [id])
  date            DateTime @db.Date
  startTime       DateTime @map("start_time")
  endTime         DateTime @map("end_time")
  activityType    String   @map("activity_type")    // Health Connect exercise type string
  title           String?                             // Nom personnalise si dispo
  durationMinutes Float    @map("duration_minutes")
  calories        Float?                              // kcal si disponible
  distance        Float?                              // metres si disponible
  heartRateAvg    Int?     @map("heart_rate_avg")    // bpm si disponible
  sourceApp       String?  @map("source_app")        // package name de l'app source
  hcRecordId      String?  @map("hc_record_id")     // Health Connect record UUID pour dedup
  createdAt       DateTime @default(now()) @map("created_at")
  updatedAt       DateTime @updatedAt @map("updated_at")

  @@unique([userId, hcRecordId])
  @@index([userId, date])
  @@map("health_activities")
}
```

#### Modele `DailyEntry` (avec champ source)

```prisma
model DailyEntry {
  id          String        @id @default(uuid())
  trackableId String        @map("trackable_id")
  trackable   TrackableItem @relation(fields: [trackableId], references: [id], onDelete: Cascade)
  date        DateTime      @db.Date
  value       Float
  notes       String?
  source      String        @default("manual") // "manual" | "health_connect"
  createdAt   DateTime      @default(now()) @map("created_at")
  updatedAt   DateTime      @updatedAt @map("updated_at")

  @@unique([trackableId, date])
  @@map("daily_entries")
}
```

#### Modele : `SyncDevice`

```prisma
model SyncDevice {
  id            String   @id @default(uuid())
  userId        String   @map("user_id")
  user          User     @relation(fields: [userId], references: [id])
  deviceName    String   @map("device_name")
  lastSyncAt    DateTime @map("last_sync_at")
  createdAt     DateTime @default(now()) @map("created_at")

  @@index([userId])
  @@map("sync_devices")
}
```

#### Trackables systeme (seed data)

Ces trackables sont crees automatiquement par l'API au premier sync d'un utilisateur :

```typescript
const HEALTH_CONNECT_TRACKABLES = [
  {
    name: "Pas",
    icon: "footprints",
    color: "#22C55E",      // green
    trackingType: "number",
    unit: "pas",
    isSystem: true,
    defaultGoal: { targetValue: 10000, frequency: "daily" },
  },
  {
    name: "Minutes d'activite",
    icon: "timer",
    color: "#3B82F6",      // blue
    trackingType: "duration",
    unit: "min",
    isSystem: true,
    defaultGoal: { targetValue: 90, frequency: "daily" },
  },
  {
    name: "Calories actives",
    icon: "flame",
    color: "#EF4444",      // red
    trackingType: "number",
    unit: "kcal",
    isSystem: true,
    defaultGoal: { targetValue: 500, frequency: "daily" },
  },
  {
    name: "Durée sommeil",
    icon: "moon",
    color: "#8B5CF6",      // purple
    trackingType: "number",
    unit: "score",
    isSystem: true,
    defaultGoal: null, // pas d'objectif par defaut
  },
];
```

### 5.2 Relations User (ajouts dans momentum repo)

```prisma
model User {
  // ... champs existants ...

  // Nouveaux
  healthActivities HealthActivity[]
  syncDevices      SyncDevice[]
  trackableItems   TrackableItem[]  // deja prevu Epic 3
}
```

---

## 6. API Specification

> **Note** : Ces endpoints sont implementes dans le monorepo `momentum`. Ils sont documentes ici integralement car le developpeur Android doit connaitre les contrats d'API pour implementer le client HTTP.

### 6.1 Endpoint : `POST /health-sync`

Endpoint principal appele par le companion. Recoit un batch de donnees de sante pour une plage de dates.

**Auth** : Bearer token (JWT existant Momentum)

#### Request Body

```typescript
interface HealthSyncRequest {
  deviceName: string;             // Ex: "Galaxy Watch 6"
  syncedAt: string;               // ISO 8601 timestamp du sync
  dailyMetrics: DailyMetric[];    // Metriques journalieres aggregees
  activities: ActivityRecord[];   // Activites/entrainements individuels
  sleepSessions: SleepRecord[];   // Sessions de sommeil
}

interface DailyMetric {
  date: string;                   // "YYYY-MM-DD"
  steps: number | null;           // Total pas du jour
  activeCalories: number | null;  // Total kcal actives
  activeMinutes: number | null;   // Total minutes d'activite
}

interface SleepRecord {
  date: string;                   // "YYYY-MM-DD" (date du reveil)
  startTime: string;              // ISO 8601
  endTime: string;                // ISO 8601
  durationMinutes: number;
  score: number | null;           // Score Samsung Health si disponible (0-100)
  stages: SleepStage[] | null;    // Detail des phases (optionnel)
}

interface SleepStage {
  stage: "awake" | "light" | "deep" | "rem" | "sleeping";
  startTime: string;
  endTime: string;
}

interface ActivityRecord {
  hcRecordId: string;             // Health Connect UUID
  date: string;                   // "YYYY-MM-DD"
  startTime: string;              // ISO 8601
  endTime: string;                // ISO 8601
  activityType: string;           // Health Connect ExerciseType string
  title: string | null;
  durationMinutes: number;
  calories: number | null;
  distance: number | null;        // metres
  heartRateAvg: number | null;
  sourceApp: string | null;       // Ex: "com.samsung.android.health"
}
```

#### Response

```typescript
// 200 OK
interface HealthSyncResponse {
  synced: {
    dailyMetrics: number;         // Nombre de jours upserted
    activities: number;           // Nombre d'activites upserted
    sleepSessions: number;        // Nombre de sessions sommeil upserted
  };
  device: {
    id: string;
    lastSyncAt: string;
  };
}

// 401 Unauthorized - Token invalide
// 400 Bad Request - Payload invalide (details dans error.details)
```

#### Server-side Logic (implemented in momentum repo)

```
1. Valider le payload (Zod)
2. Upsert SyncDevice (deviceName, lastSyncAt)
3. Pour chaque DailyMetric :
   a. Ensure les TrackableItems systeme existent pour l'user (creation lazy)
   b. Upsert DailyEntry pour chaque metrique non-null :
      - steps    -> trackable "Pas"
      - activeCalories -> trackable "Calories actives"
      - activeMinutes  -> trackable "Minutes d'activite"
      avec source = "health_connect"
4. Pour chaque SleepRecord :
   a. Upsert DailyEntry pour le score de sommeil (ou duree si pas de score)
      avec source = "health_connect"
5. Pour chaque ActivityRecord :
   a. Upsert HealthActivity via hcRecordId (dedup)
6. Retourner le recap
```

### 6.2 Endpoint : `GET /health-sync/status`

Permet au companion de verifier la config et le dernier sync.

**Auth** : Bearer token

```typescript
// Response 200
{
  "configured": true,
  "lastSync": "2026-02-05T08:30:00Z",
  "trackables": {
    "steps": { "id": "uuid", "goalValue": 10000 },
    "activeCalories": { "id": "uuid", "goalValue": 500 },
    "activeMinutes": { "id": "uuid", "goalValue": 90 },
    "sleepDuration": { "id": "uuid", "goalValue": null }
  }
}
```

### 6.3 Endpoint : `GET /health-sync/activities`

Liste les activites synchronisees.

**Auth** : Bearer token

**Query params** : `from`, `to`, `activityType`, `limit`, `offset`

```typescript
// Response 200
{
  "data": [
    {
      "id": "uuid",
      "date": "2026-02-05",
      "startTime": "2026-02-05T07:00:00Z",
      "endTime": "2026-02-05T08:15:00Z",
      "activityType": "WALKING",
      "title": null,
      "durationMinutes": 75,
      "calories": 320,
      "distance": 5200,
      "heartRateAvg": 125,
      "sourceApp": "com.samsung.android.health"
    }
  ],
  "total": 42
}
```

---

## 7. Android Companion App Specification

### 7.1 Screens

#### Screen 1 : Setup (premiere ouverture)

```
+----------------------------------+
|     MOMENTUM COMPANION           |
|                                  |
|  URL du serveur Momentum         |
|  [https://momentum.local:3001 ]  |
|                                  |
|  Email                           |
|  [user@example.com            ]  |
|                                  |
|  Mot de passe                    |
|  [--------                    ]  |
|                                  |
|  [    TESTER LA CONNEXION     ]  |
|                                  |
|  [    SUIVANT ->              ]  |
+----------------------------------+
```

#### Screen 2 : Permissions Health Connect

```
+----------------------------------+
|  PERMISSIONS SANTE               |
|                                  |
|  Momentum Companion a besoin     |
|  d'acceder a vos donnees de      |
|  sante pour synchroniser :       |
|                                  |
|  * Pas                           |
|  * Calories actives              |
|  * Sessions d'exercice           |
|  * Sommeil                       |
|                                  |
|  Les donnees sont envoyees       |
|  uniquement a votre serveur      |
|  Momentum self-hosted.           |
|                                  |
|  [ AUTORISER L'ACCES SANTE   ]  |
+----------------------------------+
```

#### Screen 3 : Dashboard (ecran principal)

```
+----------------------------------+
|  MOMENTUM COMPANION    [gear]    |
|                                  |
|  * Connecte a momentum.local     |
|  Dernier sync : il y a 12 min   |
|                                  |
|  +----------------------------+  |
|  |   Aujourd'hui              |  |
|  |                            |  |
|  |   [walk] 8 432 / 10 000   |  |
|  |   ========-- 84%          |  |
|  |                            |  |
|  |   [timer] 62 / 90 min     |  |
|  |   ======---- 69%          |  |
|  |                            |  |
|  |   [flame] 385 / 500 kcal  |  |
|  |   =======--- 77%          |  |
|  +----------------------------+  |
|                                  |
|  [ SYNCHRONISER MAINTENANT  ]   |
|                                  |
|  Activites aujourd'hui :         |
|  07:00 - Marche (45 min, 3.2km) |
|  12:30 - Musculation (62 min)   |
+----------------------------------+
```

#### Screen 4 : Settings

```
+----------------------------------+
|  <- PARAMETRES                   |
|                                  |
|  Serveur                         |
|  https://momentum.local:3001     |
|  [Modifier]                      |
|                                  |
|  Compte                          |
|  user@example.com                |
|  [Deconnecter]                   |
|                                  |
|  Frequence de sync               |
|  (*) 15 minutes                  |
|  ( ) 30 minutes                  |
|  ( ) 1 heure                     |
|  ( ) 2 heures                    |
|                                  |
|  Sync initial                    |
|  Importer les 30 derniers jours  |
|  [ LANCER IMPORT INITIAL     ]  |
|                                  |
|  Debug                           |
|  [ VOIR LES LOGS             ]  |
+----------------------------------+
```

### 7.2 Project Structure

```
momentum-companion/
├── app/
│   ├── src/main/
│   │   ├── java/com/momentum/companion/
│   │   │   ├── MomentumApp.kt                 // Application class + Hilt
│   │   │   ├── di/
│   │   │   │   └── AppModule.kt               // Hilt modules
│   │   │   ├── data/
│   │   │   │   ├── api/
│   │   │   │   │   ├── MomentumApiService.kt   // Retrofit/Ktor interface
│   │   │   │   │   └── models/                 // Request/Response DTOs
│   │   │   │   │       ├── HealthSyncRequest.kt
│   │   │   │   │       ├── HealthSyncResponse.kt
│   │   │   │   │       └── AuthModels.kt
│   │   │   │   ├── healthconnect/
│   │   │   │   │   ├── HealthConnectReader.kt  // Lecture Health Connect
│   │   │   │   │   └── HealthConnectMapper.kt  // HC records -> DTOs
│   │   │   │   └── preferences/
│   │   │   │       └── AppPreferences.kt       // EncryptedSharedPrefs
│   │   │   ├── sync/
│   │   │   │   ├── SyncWorker.kt               // WorkManager Worker
│   │   │   │   └── SyncScheduler.kt            // Schedule/cancel periodic work
│   │   │   ├── ui/
│   │   │   │   ├── setup/
│   │   │   │   │   └── SetupScreen.kt
│   │   │   │   ├── dashboard/
│   │   │   │   │   └── DashboardScreen.kt
│   │   │   │   ├── settings/
│   │   │   │   │   └── SettingsScreen.kt
│   │   │   │   └── theme/
│   │   │   │       └── Theme.kt                // Dark theme matching Momentum
│   │   │   └── navigation/
│   │   │       └── NavGraph.kt
│   │   ├── AndroidManifest.xml
│   │   └── res/
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts                            // Root build
├── settings.gradle.kts
└── docs/
    └── prd.md                                  // Ce document
```

### 7.3 Health Connect Permissions Required

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.health.READ_STEPS" />
<uses-permission android:name="android.permission.health.READ_ACTIVE_CALORIES_BURNED" />
<uses-permission android:name="android.permission.health.READ_EXERCISE" />
<uses-permission android:name="android.permission.health.READ_SLEEP" />
<uses-permission android:name="android.permission.health.READ_HEART_RATE" />
<uses-permission android:name="android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND" />
<uses-permission android:name="android.permission.health.READ_HEALTH_DATA_HISTORY" />
```

### 7.4 Sync Worker Logic (Pseudocode)

```kotlin
class SyncWorker : CoroutineWorker() {

    override suspend fun doWork(): Result {
        // 1. Lire les preferences (API URL, token)
        val config = preferences.getConfig() ?: return Result.failure()

        // 2. Verifier/refresh le token JWT
        val token = ensureValidToken(config)

        // 3. Determiner la plage de dates a sync
        val lastSync = preferences.getLastSyncTimestamp()
        val startDate = lastSync?.toLocalDate() ?: LocalDate.now().minusDays(30)
        val endDate = LocalDate.now()

        // 4. Lire les donnees Health Connect
        val steps = healthConnectReader.readSteps(startDate, endDate)
        val calories = healthConnectReader.readActiveCalories(startDate, endDate)
        val exercises = healthConnectReader.readExerciseSessions(startDate, endDate)
        val sleep = healthConnectReader.readSleepSessions(startDate, endDate)

        // 5. Agreger par jour
        val dailyMetrics = aggregateByDay(steps, calories, exercises, startDate, endDate)
        val activities = mapExerciseSessions(exercises)
        val sleepRecords = mapSleepSessions(sleep)

        // 6. Envoyer a l'API Momentum
        val request = HealthSyncRequest(
            deviceName = Build.MODEL,
            syncedAt = Instant.now().toString(),
            dailyMetrics = dailyMetrics,
            activities = activities,
            sleepSessions = sleepRecords,
        )

        return try {
            api.postHealthSync(token, request)
            preferences.setLastSyncTimestamp(Instant.now())
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
```

### 7.5 Health Connect Reading (Pseudocode)

```kotlin
class HealthConnectReader(private val client: HealthConnectClient) {

    // Aggregate steps per day
    suspend fun readSteps(start: LocalDate, end: LocalDate): Map<LocalDate, Long> {
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    start.atStartOfDay().toInstant(ZoneOffset.UTC),
                    end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
                )
            )
        )
        // Aggregate donne le total sur toute la plage
        // Pour le per-day, utiliser AggregateGroupByPeriod
        val result = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(...),
                timeRangeSlicer = Period.ofDays(1)
            )
        )
        return result.associate { bucket ->
            bucket.startTime.toLocalDate() to (bucket.result[StepsRecord.COUNT_TOTAL] ?: 0L)
        }
    }

    // Aggregate active calories per day
    suspend fun readActiveCalories(start: LocalDate, end: LocalDate): Map<LocalDate, Double> {
        val result = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(...),
                timeRangeSlicer = Period.ofDays(1)
            )
        )
        return result.associate { bucket ->
            bucket.startTime.toLocalDate() to
                (bucket.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
                    ?.inKilocalories ?: 0.0)
        }
    }

    // Read exercise sessions (full records, not aggregated)
    suspend fun readExerciseSessions(start: LocalDate, end: LocalDate): List<ExerciseSessionRecord> {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(...)
            )
        )
        return response.records
    }

    // Read sleep sessions
    suspend fun readSleepSessions(start: LocalDate, end: LocalDate): List<SleepSessionRecord> {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(...)
            )
        )
        return response.records
    }
}
```

---

## 8. Epic List

### Epic 1 : API Momentum - Endpoints Health Sync (Implemented in momentum repo)

Ajouter les modeles de donnees et endpoints dans l'API Momentum existante pour recevoir et stocker les donnees de sante synchronisees depuis le companion. **Cet epic est un prerequis pour l'Epic 2 mais vit dans le monorepo `momentum`, pas dans ce repository.**

### Epic 2 : Android Companion - Foundation & Sync (PRIMARY - this repo)

Creer l'app Android companion avec la configuration, les permissions Health Connect, la logique de sync en background, et l'ecran dashboard. **C'est l'epic principal de ce repository.**

---

## 9. Epic Details

### Epic 1 : API Momentum - Endpoints Health Sync (Implemented in momentum repo)

> **IMPORTANT** : Cet epic est documente ici pour contexte uniquement. Toutes les stories ci-dessous sont implementees dans le monorepo `momentum`, pas dans `momentum-companion`. Le developpeur Android doit comprendre ces stories pour savoir quels endpoints et modeles de donnees sont disponibles cote serveur.

**Objectif** : Etendre l'API Momentum avec les modeles de donnees et endpoints necessaires pour recevoir les donnees de sante du companion Android. Cet epic est prerequis de l'Epic 2 (le companion a besoin d'une API fonctionnelle pour envoyer les donnees).

> **Note** : Cet epic presuppose que l'Epic 3 de Momentum (TrackableItem, DailyEntry, TrackableGoal) est implemente. Si ce n'est pas le cas, les stories 1.1 et 1.2 incluent la creation de ces modeles.

---

#### Story 1.1 : Schema de donnees Health Sync *(momentum repo)*

**En tant que** developpeur backend,
**je veux** ajouter les modeles HealthActivity et SyncDevice au schema Prisma et etendre DailyEntry avec le champ `source`,
**afin que** l'API puisse stocker les donnees de sante synchronisees.

**Acceptance Criteria :**

1. Le modele `HealthActivity` est cree dans le schema Prisma avec tous les champs specifies dans la section 5.1
2. Le modele `SyncDevice` est cree dans le schema Prisma
3. Le modele `DailyEntry` (Epic 3) inclut le champ `source` avec valeur par defaut `"manual"`
4. Les relations `User` sont mises a jour (healthActivities, syncDevices)
5. La migration Prisma s'execute sans erreur
6. Un index existe sur `health_activities(user_id, date)` et une contrainte unique sur `(user_id, hc_record_id)`

---

#### Story 1.2 : Endpoint POST /health-sync *(momentum repo)*

**En tant que** app companion Android,
**je veux** envoyer un batch de donnees de sante (metriques journalieres + activites + sommeil) en un seul appel API,
**afin que** les donnees soient stockees dans Momentum sans multiplier les appels reseau.

**Acceptance Criteria :**

1. L'endpoint `POST /health-sync` accepte le payload `HealthSyncRequest` tel que specifie en section 6.1
2. Le payload est valide par un schema Zod (tous les champs obligatoires presents, dates valides, valeurs positives)
3. Les TrackableItems systeme (Pas, Minutes d'activite, Calories actives, Durée sommeil) sont crees automatiquement pour l'utilisateur s'ils n'existent pas encore (creation lazy avec les valeurs de la section 5.1)
4. Les DailyEntry sont upsertees sur la contrainte `(trackableId, date)` avec `source = "health_connect"`
5. Les HealthActivity sont upsertees sur la contrainte `(userId, hcRecordId)`
6. Les SleepRecord sont transformes en DailyEntry pour le trackable "Durée sommeil" (valeur = duree en minutes)
7. Le SyncDevice est upserte avec le `deviceName` et `lastSyncAt`
8. La reponse inclut le nombre de records upserted par categorie
9. En cas de payload invalide, retourner 400 avec les details d'erreur Zod
10. En cas de token invalide, retourner 401

---

#### Story 1.3 : Endpoints status et activites *(momentum repo)*

**En tant que** app companion Android et frontend web,
**je veux** pouvoir verifier le statut de sync et lister les activites synchronisees,
**afin de** pouvoir afficher l'etat de la connexion et l'historique d'activites.

**Acceptance Criteria :**

1. L'endpoint `GET /health-sync/status` retourne la config du sync pour l'utilisateur (derniere sync, trackables avec IDs et goals)
2. L'endpoint `GET /health-sync/activities` retourne la liste paginee des activites de l'utilisateur
3. Les query params `from`, `to`, `activityType`, `limit`, `offset` sont supportes
4. Les endpoints sont proteges par authentification JWT
5. Les reponses suivent le format standard de l'API Momentum (error codes, pagination)

---

### Epic 2 : Android Companion - Foundation & Sync (PRIMARY - this repo)

**Objectif** : Creer l'application Android companion complete avec la configuration initiale, la lecture des donnees Health Connect, la synchronisation automatique en background, et un dashboard minimal affichant les metriques du jour.

---

#### Story 2.1 : Setup projet et ecran de configuration

**En tant que** utilisateur,
**je veux** configurer mon app companion avec l'URL de mon serveur Momentum et mes identifiants,
**afin de** pouvoir connecter l'app a mon instance self-hosted.

**Acceptance Criteria :**

1. Le projet Android est initialise avec Kotlin, Jetpack Compose, Hilt, et le theme sombre
2. L'ecran de setup (Screen 1) permet de saisir l'URL du serveur, l'email et le mot de passe
3. Le bouton "Tester la connexion" appelle `POST /auth/login` et affiche le resultat (succes/erreur)
4. En cas de succes, le token JWT et les credentials sont stockes dans EncryptedSharedPreferences
5. L'app gere le refresh de token JWT automatiquement
6. L'URL du serveur accepte les certificats self-signed (option configurable) car le serveur est self-hosted
7. La navigation redirige vers le setup si aucun serveur n'est configure

---

#### Story 2.2 : Permissions et lecture Health Connect

**En tant que** utilisateur,
**je veux** autoriser l'app a lire mes donnees de sante,
**afin que** l'app puisse acceder a mes pas, calories, activites et sommeil.

**Acceptance Criteria :**

1. L'ecran de permissions (Screen 2) explique quelles donnees seront lues et pourquoi
2. L'app demande les permissions Health Connect listees en section 7.3
3. Si Health Connect n'est pas installe/disponible, un message explicatif est affiche avec un lien vers le Play Store
4. Le `HealthConnectReader` lit correctement les StepsRecord, ActiveCaloriesBurnedRecord, ExerciseSessionRecord, et SleepSessionRecord
5. Les donnees sont aggregees par jour pour les metriques (steps, calories) via `aggregateGroupByPeriod`
6. Les ExerciseSessionRecord sont lues individuellement avec leurs metadonnees (type, duree, calories, distance)
7. Les SleepSessionRecord incluent les stages si disponibles

---

#### Story 2.3 : Sync background et envoi API

**En tant que** utilisateur,
**je veux** que mes donnees de sante soient synchronisees automatiquement en arriere-plan,
**afin de** ne pas avoir a ouvrir l'app manuellement.

**Acceptance Criteria :**

1. Un `PeriodicWorkRequest` est enregistre via WorkManager avec l'intervalle configure (defaut 15 min)
2. Le `SyncWorker` lit les donnees Health Connect depuis le dernier sync reussi
3. Les donnees sont envoyees a l'endpoint `POST /health-sync`
4. En cas de succes, le timestamp du dernier sync est mis a jour
5. En cas d'erreur reseau, le worker retourne `Result.retry()` (max 3 tentatives avec backoff exponentiel)
6. La frequence de sync est configurable dans les settings (15min, 30min, 1h, 2h)
7. Le sync fonctionne meme si l'app est fermee (WorkManager persiste entre les redemarrages)
8. Les contraintes WorkManager sont configurees : reseau requis, batterie non critique

---

#### Story 2.4 : Dashboard et sync manuel

**En tant que** utilisateur,
**je veux** voir mes metriques du jour et pouvoir declencher un sync manuellement,
**afin de** verifier que tout fonctionne et voir mes progres.

**Acceptance Criteria :**

1. L'ecran dashboard (Screen 3) affiche les 3 anneaux du jour (pas, minutes activite, calories) avec progression vers l'objectif
2. Les objectifs sont recuperes depuis l'API Momentum (`GET /health-sync/status`)
3. Les valeurs actuelles sont lues directement depuis Health Connect (pas d'appel API pour l'affichage local)
4. Le statut de connexion et la date du dernier sync sont affiches
5. Le bouton "Synchroniser maintenant" declenche un `OneTimeWorkRequest` immediat
6. La liste des activites du jour est affichee sous les anneaux
7. Un pull-to-refresh rafraichit les donnees locales Health Connect

---

#### Story 2.5 : Settings et import initial

**En tant que** utilisateur,
**je veux** pouvoir modifier mes parametres et importer mon historique,
**afin de** personnaliser l'app et recuperer mes donnees passees.

**Acceptance Criteria :**

1. L'ecran settings (Screen 4) affiche les infos du serveur et du compte
2. La frequence de sync est modifiable (met a jour le PeriodicWorkRequest)
3. Le bouton "Lancer import initial" lit les 30 derniers jours de Health Connect et les envoie a l'API
4. Un indicateur de progression est affiche pendant l'import initial
5. Le bouton "Voir les logs" affiche les 50 derniers evenements de sync (succes, erreur, nombre de records)
6. Le bouton "Deconnecter" supprime les credentials et revient au setup
7. Les logs sont stockes localement dans une Room database ou un fichier texte

---

## 10. Health Connect Exercise Types Reference

Mapping des types d'exercices Health Connect les plus courants pour l'affichage :

| Health Connect Type | Label FR | Icone suggeree |
|---|---|---|
| `EXERCISE_TYPE_WALKING` | Marche | footprints |
| `EXERCISE_TYPE_RUNNING` | Course | running |
| `EXERCISE_TYPE_BIKING` | Velo | bike |
| `EXERCISE_TYPE_SWIMMING_OPEN_WATER` | Natation | waves |
| `EXERCISE_TYPE_WEIGHTLIFTING` | Musculation | dumbbell |
| `EXERCISE_TYPE_YOGA` | Yoga | flower-lotus |
| `EXERCISE_TYPE_HIKING` | Randonnee | mountain |
| `EXERCISE_TYPE_ELLIPTICAL` | Elliptique | activity |
| `EXERCISE_TYPE_STAIR_CLIMBING` | Escaliers | stairs |
| `EXERCISE_TYPE_OTHER_WORKOUT` | Autre | heart-pulse |

---

## 11. Sleep Score Handling

Samsung Health calcule un score de sommeil (0-100) mais Health Connect **ne stocke pas nativement ce score**. Health Connect stocke uniquement les `SleepSessionRecord` avec les stages.

**Strategie** :
1. Lire les `SleepSessionRecord` avec leurs stages
2. Calculer un score simplifie cote companion si Samsung Health ne l'expose pas :
   - Base 100, penalites : duree < 7h (-15), peu de deep (-10), peu de REM (-10), reveils frequents (-5 par reveil)
   - Ou simplement stocker la duree totale en minutes comme valeur si pas de score disponible
3. Le champ `score` est nullable dans le payload API -- si null, on stocke la duree comme valeur fallback

---

## 12. Security Considerations

1. **Credentials** : Stockes dans `EncryptedSharedPreferences` (AES-256-SIV + AES-256-GCM via Android Keystore)
2. **Token rotation** : Le JWT Momentum expire (configurable). Le companion stocke email/password pour re-login automatiquement
3. **HTTPS** : Obligatoire. Pour les serveurs self-hosted avec certificats self-signed, une option permet d'ajouter un certificat CA custom ou de desactiver la verification (avec warning)
4. **Permissions** : L'app ne demande que des permissions de lecture Health Connect, jamais d'ecriture
5. **Minimal data** : L'app ne stocke aucune donnee de sante localement (sauf les logs de sync). Les donnees transitent de Health Connect vers l'API

---

## 13. Next Steps

### Architect Prompt

> Concevoir l'architecture technique pour l'app Momentum Companion (Android Kotlin + Jetpack Compose) et les modifications API Momentum (Express + Prisma). Se baser sur ce PRD, l'architecture existante de Momentum (`docs/architecture/`), et le schema Prisma actuel. Points d'attention : strategy d'upsert pour l'idempotence, gestion des tokens JWT longue duree pour un background worker, et structure du WorkManager.
