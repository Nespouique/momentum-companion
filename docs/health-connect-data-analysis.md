# Health Connect Data Analysis - Samsung Health

Date: 2026-02-07

## Contexte

L'app Momentum Companion lit les donnees Samsung Health via Health Connect (HC).
Un ecran "HC Explorer" (Settings > Debug > Explorer Health Connect) a ete cree pour
inspecter **tous** les types de records HC exposes, par jour, avec le detail de chaque record.

## Types HC disponibles (connect-client 1.1.0)

40 types de records stables sont lus par l'explorer. 2 types supplementaires existent
dans la doc officielle mais ne compilent pas en 1.1.0 :

- `ActivityIntensityRecord` -- n'existe pas dans l'artefact 1.1.0 (probablement 1.2.0+)
- `MindfulnessSessionRecord` -- API experimentale, annotation `@OptIn` non disponible en 1.1.0

### Types lus (40)

**Activite :** ActiveCaloriesBurned, CyclingPedalingCadence, Distance, ElevationGained,
ExerciseSession, FloorsClimbed, PlannedExerciseSession, Power, Speed, Steps,
StepsCadence, TotalCaloriesBurned, Vo2Max, WheelchairPushes

**Corps :** BasalMetabolicRate, BodyFat, BodyWaterMass, BoneMass, Height, LeanBodyMass, Weight

**Cycle :** BasalBodyTemperature, CervicalMucus, IntermenstrualBleeding,
MenstruationFlow, MenstruationPeriod, OvulationTest

**Nutrition :** Hydration, Nutrition

**Sommeil :** SleepSession

**Constantes :** BloodGlucose, BloodPressure, BodyTemperature, HeartRate,
HeartRateVariabilityRmssd, OxygenSaturation, RespiratoryRate, RestingHeartRate,
SkinTemperature

**Autre :** SexualActivity

## Ce que Samsung Health expose reellement vers Health Connect

### Observations sur donnees reelles (exports JSON du 06 et 07/02/2026)

#### Journee sans exercice (07/02)
- **StepsRecord** : 1 record full-day (00:00-23:59), count=166
- **HeartRateRecord** : 12 records, 1 sample chacun, ~10min d'intervalle (51-89 bpm)
- Tout le reste : **0 records**

#### Journee avec exercice auto-detecte (06/02 - marche 16min)
- **StepsRecord** : 1 record full-day (00:00-23:59), count=7348
- **ExerciseSessionRecord** : 1 record, exerciseType=79 (WALKING), 14:25-14:41, title=null, segments=[], laps=[], route=NoData
- **TotalCaloriesBurnedRecord** : 1 record, 104 kcal (14:25-14:41, meme plage que l'exercice)
- **DistanceRecord** : 1 record, 1240.62m (14:25-14:41)
- **SpeedRecord** : 1 record, 16 samples (~1/min), 1.22-1.86 m/s (4.4-6.7 km/h)
- **HeartRateRecord** : 37 records dont 1 exercice (centaines de samples a 1/sec, 91-115 bpm) + 36 passifs
- Tout le reste : **0 records**

### Donnees passives (podometre, quotidien)

| Type HC | Donnees observees | Detail |
|---------|-------------------|--------|
| **StepsRecord** | 1 record full-day (00:00:00 - 23:59:59) | Total des pas (passifs + exercice inclus). Record unique, pas de granularite. |
| **HeartRateRecord** | 1 sample / ~10-20min | Mesures periodiques, non utile pour les "rings". Source: `com.sec.android.app.shealth` |

### Donnees d'exercice (detection auto Samsung Health)

Quand Samsung Health detecte automatiquement un exercice (ex: marche), il ecrit **4 types simultanes avec les memes timestamps** :

| Type HC | Donnees observees |
|---------|-------------------|
| **ExerciseSessionRecord** | Type d'exercice (79=WALKING), duree, title=null, segments=[], laps=[], route=NoData |
| **TotalCaloriesBurnedRecord** | Calories totales brulees pendant l'exercice (104 kcal pour 16min de marche) |
| **DistanceRecord** | Distance en metres (1240.62m) |
| **SpeedRecord** | Echantillons de vitesse (~1/min, 1.2-1.9 m/s) |
| **HeartRateRecord** | Echantillons a 1/sec pendant l'exercice (91-115 bpm) |

### Donnees JAMAIS exposees par Samsung Health

| Donnee | Type HC attendu | Statut |
|--------|----------------|--------|
| Calories actives passives (hors exercice) | ActiveCaloriesBurnedRecord | **Absent** -- Samsung ne sync jamais (toujours 0 records) |
| Minutes actives passives | Pas de type HC dedie | **Inexistant** dans HC |
| Steps granulaires (par bout de marche) | StepsRecord multiples | **Absent** -- Samsung ecrit 1 seul record full-day |
| Calories totales passives (hors exercice) | TotalCaloriesBurnedRecord | **Absent** -- present uniquement pendant les exercices |
| Steps cadence | StepsCadenceRecord | **Absent** -- jamais sync par Samsung |
| Etages montes | FloorsClimbedRecord | **Absent** |

## Impact sur les "3 rings" Samsung

Samsung Health affiche 3 anneaux sur le cadran Galaxy Watch :

| Ring | Composition Samsung | Disponible via HC ? |
|------|--------------------|---------------------|
| **Pas** | Total podometre | **OUI** -- StepsRecord full-day |
| **Minutes actives** | Minutes exercice + minutes marche passive | **PARTIEL** -- ExerciseSession seulement, pas la marche passive |
| **Calories actives** | Calories exercice + calories marche passive | **PARTIEL** -- TotalCaloriesBurned des exercices seulement |

## Strategie d'estimation implementee

Puisque Samsung ne fournit pas les donnees passives de calories et minutes actives,
l'app les **estime** a partir du nombre de pas et du profil utilisateur.

### Profil utilisateur (configurable dans Settings > Profil)

| Parametre | Defaut | Utilisation |
|-----------|--------|-------------|
| Pas/minute | 100 | Cadence de marche moyenne, pour convertir pas -> minutes |
| Poids (kg) | 70 | Calcul des calories via formule MET |
| Taille (cm) | 170 | Reserve pour ameliorations futures |
| Age | 30 | Reserve pour ameliorations futures |
| Sexe | Homme | Reserve pour ameliorations futures |

### Formule de calcul

```
# 1. Deduire les pas d'exercice du total (eviter double comptage)
estimatedExerciseSteps = exerciseMinutes * stepsPerMin
passiveSteps = max(0, totalSteps - estimatedExerciseSteps)

# 2. Estimer minutes actives passives
passiveMinutes = passiveSteps / stepsPerMin

# 3. Estimer calories actives passives (formule MET)
#    MET marche = 3.0 (marche decontractee 3-4 km/h)
#    Cout metabolique total pendant l'activite (Samsung compte le total, pas l'exces)
calPerMin = (MET_marche * 3.5 * weightKg) / 200.0
passiveCalories = passiveMinutes * calPerMin

# 4. Totaux
totalActiveMinutes = passiveMinutes + sum(exerciseSessions.duration)
totalActiveCalories = passiveCalories + sum(exerciseSessions.totalCaloriesBurned)
```

### Exemples de calcul

**Journee du 06/02 (7348 pas, 1 marche de 16min) :**
- Profil : 100 pas/min, 80 kg
- Estimated exercise steps : 16 * 100 = 1600
- Passive steps : 7348 - 1600 = 5748
- Passive minutes : 5748 / 100 = 57.48 min
- Cal/min walking : (3.0 * 3.5 * 80) / 200 = 4.2 kcal/min
- Passive calories : 57.48 * 4.2 = 241.4 kcal
- **Total minutes actives** : 57.48 + 16 = **73 min**
- **Total calories actives** : 241.4 + 104 = **345 kcal**

**Journee du 07/02 (245 pas, pas d'exercice) :**
- Passive steps : 245
- Passive minutes : 245 / 100 = 2.45 min
- Passive calories : 2.45 * 4.2 = 10.3 kcal
- **Total minutes actives** : **2 min**
- **Total calories actives** : **10 kcal** (Samsung affiche 11)

### Precision et limites

- L'estimation est **toujours inferieure** au reel Samsung car :
  - Samsung utilise accelerometre + GPS pour les calories (pas juste le nombre de pas)
  - La cadence varie selon la personne et l'activite
  - Samsung compte aussi des "minutes actives" pour des mouvements non-pas (menage, etc.)
- Le StepsRecord full-day **inclut** les pas des exercices auto-detectes
  (verifie empiriquement : 7348 pas pour une journee avec 16min de marche est coherent)
- Les pas d'exercice sont **estimes** (duration * stepsPerMin) car Samsung
  n'ecrit pas de StepsRecord separe pour chaque exercice

## Implementation

### Fichiers modifies

| Fichier | Role |
|---------|------|
| `data/preferences/AppPreferences.kt` | Stockage du profil utilisateur (stepsPerMin, weightKg, heightCm, age, isMale) dans EncryptedSharedPreferences |
| `data/healthconnect/HealthConnectReader.kt` | Lecture des donnees brutes HC. `readSteps()` lit les StepsRecord raw (pas d'agregation car Samsung ecrit un record full-day incompatible). `readTotalCaloriesBurned()` lit les calories exercice. Les anciennes methodes `readActiveCalories()` (toujours 0) et `readActiveMinutes()` (retournait 1440min) ont ete supprimees |
| `data/healthconnect/HealthConnectMapper.kt` | `UserProfile` data class + estimation MET dans `buildDailyMetrics()`. Recoit steps, exerciseSessions, exerciseCalories, userProfile et produit les DailyMetric avec les valeurs estimees |
| `sync/SyncWorker.kt` | Sync periodique utilise les nouvelles APIs Reader/Mapper avec UserProfile depuis les preferences |
| `ui/dashboard/DashboardViewModel.kt` | Affichage dashboard utilise les valeurs estimees via le Mapper |
| `ui/settings/SettingsViewModel.kt` | Gestion du profil utilisateur (load/save) + import initial avec estimation |
| `ui/settings/SettingsScreen.kt` | Section "Profil" avec champs editables : pas/min, age, poids, taille, sexe (Homme/Femme) |

### Flux de donnees

```
Samsung Health
    |
    v (sync automatique)
Health Connect (StepsRecord, ExerciseSession, TotalCaloriesBurned, ...)
    |
    v (HealthConnectReader)
Donnees brutes : steps/jour, exerciseSessions, exerciseCalories/jour
    |
    v (HealthConnectMapper.buildDailyMetrics + UserProfile)
Estimation : passiveSteps -> passiveMinutes + passiveCalories
    + exerciseMinutes + exerciseCalories
    = totalActiveMinutes + totalActiveCalories
    |
    v (DailyMetric)
API Momentum : { steps, activeCalories, activeMinutes }
```

### HC Explorer (debug)

L'ecran HC Explorer (Settings > Debug > Explorer Health Connect) permet de :
- Lire les 40 types de records HC pour une date donnee
- Afficher le nombre de records par type avec detail expandable
- Exporter les donnees brutes en JSON (bouton Share dans la top bar)
- Choisir une date avec le date picker

Source : `ui/hcexplorer/HCExplorerViewModel.kt` + `HCExplorerScreen.kt`

## Reference

- Doc officielle : https://developer.android.com/health-and-fitness/health-connect/data-types
- Ecran explorer : Settings > Debug > Explorer Health Connect
- Source estimation : `data/healthconnect/HealthConnectMapper.kt`
- Source lecture HC : `data/healthconnect/HealthConnectReader.kt`
- Exports JSON : `hc-export-2026-02-06.json`, `hc-export-2026-02-07.json`
