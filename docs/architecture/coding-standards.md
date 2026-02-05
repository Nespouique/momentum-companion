# Momentum Companion - Coding Standards

## 1. Overview

This document defines the coding standards and conventions for the Momentum Companion Android project. All contributors must follow these guidelines to maintain code consistency and quality.

---

## 2. Kotlin Standards

### 2.1 General Rules

- **Kotlin version**: Use the latest stable Kotlin version supported by Android Studio
- **Null safety**: Leverage Kotlin's null safety system. Avoid `!!` (non-null assertion) except in tests. Use `?.`, `?:`, and `let`/`also` for null handling
- **Immutability**: Prefer `val` over `var`. Use immutable collections (`List`, `Map`) over mutable ones unless mutation is required
- **Data classes**: Use `data class` for DTOs and models
- **Sealed classes**: Use `sealed class` or `sealed interface` for state representation
- **Expression body**: Use expression body for single-expression functions

### 2.2 Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Variables & Functions | camelCase | `getUserById`, `isConnected` |
| Constants | SCREAMING_SNAKE_CASE | `SYNC_INTERVAL_DEFAULT`, `BASE_RETRY_DELAY` |
| Classes & Interfaces | PascalCase | `SyncWorker`, `HealthConnectReader` |
| Objects | PascalCase | `AppModule`, `ApiConfig` |
| Enum values | SCREAMING_SNAKE_CASE | `SYNC_SUCCESS`, `NETWORK_ERROR` |
| Packages | lowercase, dot-separated | `com.momentum.companion.data.api` |
| Files (Classes) | PascalCase matching class | `SyncWorker.kt`, `DashboardScreen.kt` |
| Files (Utilities) | PascalCase | `DateUtils.kt`, `Extensions.kt` |
| Composable functions | PascalCase (noun) | `DashboardScreen`, `SyncStatusCard` |
| Non-composable functions | camelCase (verb) | `calculateProgress`, `formatDuration` |

### 2.3 Package Structure

```
com.momentum.companion/
├── di/                    # Hilt dependency injection modules
├── data/                  # Data layer (repositories, data sources)
│   ├── api/               # Retrofit service, DTOs
│   ├── healthconnect/     # Health Connect reader, mapper
│   └── preferences/       # EncryptedSharedPreferences, DataStore
├── sync/                  # WorkManager workers, scheduler
├── ui/                    # Presentation layer (screens, viewmodels)
│   ├── setup/             # Setup screen + ViewModel
│   ├── dashboard/         # Dashboard screen + ViewModel
│   ├── settings/          # Settings screen + ViewModel
│   ├── theme/             # Material 3 theme, colors, typography
│   └── components/        # Shared composable components
└── navigation/            # Navigation graph, destinations
```

---

## 3. Jetpack Compose Standards

### 3.1 Composable Conventions

- **Naming**: Composable functions use PascalCase and should be nouns (`DashboardScreen`, not `showDashboard`)
- **State hoisting**: Composables should be stateless when possible. Hoist state to the ViewModel
- **Preview annotations**: All significant composables must have `@Preview` functions
- **Modifiers**: Always accept a `modifier: Modifier = Modifier` parameter as the first optional parameter

```kotlin
@Composable
fun SyncStatusCard(
    lastSyncTime: Instant?,
    isConnected: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        // ...
    }
}

@Preview(showBackground = true)
@Composable
private fun SyncStatusCardPreview() {
    MomentumTheme {
        SyncStatusCard(
            lastSyncTime = Instant.now(),
            isConnected = true,
        )
    }
}
```

### 3.2 Screen Structure

Each screen follows a consistent pattern:

```kotlin
// 1. Screen composable (receives ViewModel, delegates to content)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardContent(
        uiState = uiState,
        onSyncNow = viewModel::syncNow,
        onNavigateToSettings = { /* nav callback */ },
    )
}

// 2. Content composable (pure UI, easy to preview)
@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    onSyncNow: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pure UI rendering
}
```

### 3.3 State Management

- Use `sealed interface` for UI state:

```kotlin
sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(
        val steps: Int,
        val stepsGoal: Int,
        val activeMinutes: Int,
        val activeMinutesGoal: Int,
        val calories: Int,
        val caloriesGoal: Int,
        val lastSync: Instant?,
        val activities: List<ActivityItem>,
    ) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}
```

### 3.4 Theme

- Use Material 3 dynamic color where possible, with a dark-theme fallback
- Define all colors, typography, and shapes in the `theme/` package
- Never hardcode colors in composables; always reference `MaterialTheme.colorScheme`

---

## 4. Coroutine Patterns

### 4.1 Scope Usage

| Context | Scope | Example |
|---------|-------|---------|
| ViewModel | `viewModelScope` | Data loading, user actions |
| WorkManager | `CoroutineWorker.doWork()` | Background sync |
| Repository | Caller's scope (structured concurrency) | No standalone scope |

### 4.2 Dispatcher Usage

- **Do not hardcode dispatchers**: Inject them via Hilt for testability
- **Default mapping**: `Dispatchers.IO` for network/disk, `Dispatchers.Default` for CPU-bound work
- **Main**: Only for UI updates (handled automatically by `collectAsStateWithLifecycle`)

```kotlin
// Good: inject dispatchers
class HealthConnectReader @Inject constructor(
    private val client: HealthConnectClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun readSteps(start: LocalDate, end: LocalDate): Map<LocalDate, Long> =
        withContext(ioDispatcher) {
            // ...
        }
}
```

### 4.3 Error Handling in Coroutines

- Use `try/catch` within suspend functions
- Use `Result<T>` or sealed classes for propagating errors to the UI layer
- Never swallow exceptions silently

```kotlin
suspend fun syncHealthData(): SyncResult {
    return try {
        val data = healthConnectReader.readAll(lastSync, now)
        val response = api.postHealthSync(token, data)
        SyncResult.Success(response.synced)
    } catch (e: IOException) {
        SyncResult.NetworkError(e.message)
    } catch (e: HttpException) {
        SyncResult.ApiError(e.code(), e.message())
    }
}
```

---

## 5. Code Formatting

### 5.1 Formatter

Use **ktlint** (via the `ktlint-gradle` plugin) for consistent formatting.

### 5.2 Key Rules

- **Indentation**: 4 spaces (Kotlin standard)
- **Max line length**: 120 characters
- **Trailing commas**: Required on multi-line parameter/argument lists
- **Imports**: No wildcard imports (`import package.*` is prohibited)
- **Blank lines**: One blank line between functions, two blank lines between top-level declarations in the same file
- **Braces**: Opening brace on the same line; closing brace on its own line

### 5.3 Import Conventions

```kotlin
// Good: explicit imports, alphabetically ordered
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import com.momentum.companion.data.api.MomentumApiService

// Bad: wildcard imports
import androidx.compose.foundation.layout.*
```

Import ordering (enforced by ktlint):
1. Android / AndroidX imports
2. Third-party imports
3. Project imports (`com.momentum.companion.*`)
4. Java/Kotlin stdlib imports

---

## 6. Error Handling Patterns

### 6.1 Sealed Result Types

```kotlin
sealed interface SyncResult {
    data class Success(val counts: SyncCounts) : SyncResult
    data class NetworkError(val message: String?) : SyncResult
    data class ApiError(val code: Int, val message: String?) : SyncResult
    data class HealthConnectError(val message: String?) : SyncResult
}
```

### 6.2 ViewModel Error Handling

- Catch exceptions in the ViewModel, not in the UI layer
- Map exceptions to user-facing error messages
- Log all errors with sufficient context

### 6.3 WorkManager Error Handling

- Return `Result.retry()` for transient errors (network, server 5xx) up to 3 attempts
- Return `Result.failure()` for permanent errors (auth invalid, permissions revoked)
- Log all sync outcomes for the debug log screen

---

## 7. Testing Standards

### 7.1 Framework

- **Unit tests**: JUnit 5 + MockK
- **No instrumented tests** for MVP (Health Connect requires a real device)
- **Test source set**: `src/test/java/com/momentum/companion/`

### 7.2 File Naming

- Unit tests: `*Test.kt` (e.g., `HealthConnectMapperTest.kt`)
- Test fixtures: `*Fixtures.kt` or inline in test file

### 7.3 Test Naming Convention

Use the pattern: `should_expectedBehavior_when_condition`

```kotlin
class HealthConnectMapperTest {

    @Test
    fun should_aggregateStepsByDay_when_multipleRecordsExist() {
        // Arrange
        // Act
        // Assert
    }

    @Test
    fun should_returnEmptyMap_when_noStepsRecorded() {
        // ...
    }

    @Test
    fun should_calculateActiveMinutes_when_exerciseSessionsProvided() {
        // ...
    }
}
```

### 7.4 Test Structure

```kotlin
@ExtendWith(MockKExtension::class)
class SyncWorkerTest {

    @MockK
    private lateinit var healthConnectReader: HealthConnectReader

    @MockK
    private lateinit var apiService: MomentumApiService

    private lateinit var syncWorker: SyncWorker

    @BeforeEach
    fun setup() {
        // Initialize subject under test
    }

    @Test
    fun should_syncSuccessfully_when_allDataAvailable() {
        // Arrange
        coEvery { healthConnectReader.readSteps(any(), any()) } returns mapOf(...)
        coEvery { apiService.postHealthSync(any(), any()) } returns mockResponse

        // Act
        val result = runBlocking { syncWorker.doWork() }

        // Assert
        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        coVerify { apiService.postHealthSync(any(), any()) }
    }
}
```

### 7.5 What to Test

| Layer | What to test | Priority |
|-------|-------------|----------|
| Mapper | Data transformation (HC records to DTOs) | High |
| ViewModel | State transitions, error handling | High |
| Repository | Coordination between data sources | Medium |
| Worker | Sync flow, retry logic | Medium |
| Composables | Not tested for MVP (preview only) | Low |

---

## 8. Git Conventions

### 8.1 Commit Messages

Use conventional commits (same as Momentum):

```
type(scope): description

feat(sync): add background WorkManager sync
fix(dashboard): correct progress ring calculation
docs(readme): update installation instructions
refactor(api): simplify Retrofit service interface
test(mapper): add Health Connect mapper unit tests
chore(deps): update Compose BOM to latest
```

**Types**: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

**Scopes**: `sync`, `dashboard`, `setup`, `settings`, `api`, `health`, `di`, `nav`, `theme`, `deps`

### 8.2 Branch Naming

```
feature/description
fix/issue-description
refactor/component-name
```

---

## 9. Dependency Injection (Hilt)

### 9.1 Module Organization

- One `@Module` per concern (network, health, storage, dispatchers)
- Use `@Singleton` scope for services that should live as long as the app
- Use `@ViewModelScoped` when appropriate

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(preferences: AppPreferences): OkHttpClient {
        // ...
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        // ...
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): MomentumApiService {
        return retrofit.create(MomentumApiService::class.java)
    }
}
```

### 9.2 Qualifier Annotations

```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
```

---

## 10. Documentation

### 10.1 Code Comments

- Prefer self-documenting code over comments
- Use KDoc for public APIs and complex functions
- Keep comments up-to-date with code changes

```kotlin
/**
 * Reads and aggregates Health Connect step records per day.
 *
 * Uses [HealthConnectClient.aggregateGroupByPeriod] to bucket steps
 * into daily totals for the given date range.
 *
 * @param start Inclusive start date
 * @param end Inclusive end date
 * @return Map of date to total step count for that day
 */
suspend fun readSteps(start: LocalDate, end: LocalDate): Map<LocalDate, Long>
```

### 10.2 TODO Comments

Use `TODO(username)` format for tracking:

```kotlin
// TODO(dev): Handle READ_HEALTH_DATA_IN_BACKGROUND permission for API 35+
```
