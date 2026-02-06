package com.momentum.companion.ui.dashboard

import com.momentum.companion.data.api.MomentumApiService
import com.momentum.companion.data.api.models.SyncStatusResponse
import com.momentum.companion.data.api.models.TrackableInfo
import com.momentum.companion.data.api.models.TrackablesStatus
import com.momentum.companion.data.healthconnect.HealthConnectReader
import com.momentum.companion.data.preferences.AppPreferences
import com.momentum.companion.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var healthConnectReader: HealthConnectReader
    private lateinit var apiService: MomentumApiService
    private lateinit var preferences: AppPreferences
    private lateinit var syncScheduler: SyncScheduler

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        healthConnectReader = mockk(relaxed = true)
        apiService = mockk(relaxed = true)
        preferences = mockk(relaxed = true)
        syncScheduler = mockk(relaxed = true)

        // Sensible defaults so init block does not crash
        every { preferences.serverUrl } returns null
        every { preferences.isConfigured } returns false
        every { preferences.lastSyncTimestamp } returns 0L
        every { preferences.jwtToken } returns null

        // HealthConnectReader suspend functions return empty data by default
        coEvery { healthConnectReader.readSteps(any(), any()) } returns emptyMap()
        coEvery { healthConnectReader.readActiveCalories(any(), any()) } returns emptyMap()
        coEvery { healthConnectReader.readExerciseSessions(any(), any()) } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------
    // Helper to build the ViewModel (after per-test stub overrides)
    // ---------------------------------------------------------------

    private fun createViewModel(): DashboardViewModel {
        return DashboardViewModel(healthConnectReader, apiService, preferences, syncScheduler)
    }

    // ---------------------------------------------------------------
    // Initial / default state
    // ---------------------------------------------------------------

    @Test
    fun should_haveDefaultValues_when_initialised() {
        val vm = createViewModel()
        val state = vm.uiState.value

        assertEquals("", state.serverUrl)
        assertFalse(state.isConnected)
        assertEquals(0, state.currentSteps)
        assertEquals(0, state.currentCalories)
        assertEquals(0, state.currentMinutes)
        assertEquals(emptyList<TodayActivity>(), state.todayActivities)
        assertFalse(state.isRefreshing)
        assertFalse(state.isSyncing)
        assertNull(state.error)
    }

    @Test
    fun should_formatLastSyncAsJamais_when_timestampIsZero() {
        every { preferences.lastSyncTimestamp } returns 0L

        val vm = createViewModel()

        assertEquals("Jamais", vm.uiState.value.lastSyncFormatted)
    }

    @Test
    fun should_showServerUrlWithoutScheme_when_preferencesContainUrl() {
        every { preferences.serverUrl } returns "https://my.server.com/"

        val vm = createViewModel()

        assertEquals("my.server.com", vm.uiState.value.serverUrl)
    }

    @Test
    fun should_showConnected_when_preferencesAreConfigured() {
        every { preferences.isConfigured } returns true

        val vm = createViewModel()

        assertTrue(vm.uiState.value.isConnected)
    }

    // ---------------------------------------------------------------
    // Goals loaded from API
    // ---------------------------------------------------------------

    @Test
    fun should_loadGoalsFromApi_when_tokenIsPresent() = runTest {
        every { preferences.jwtToken } returns "jwt-token-123"

        val statusResponse = SyncStatusResponse(
            configured = true,
            lastSync = "2025-01-01T00:00:00Z",
            trackables = TrackablesStatus(
                steps = TrackableInfo(id = "steps", goalValue = 12000),
                activeCalories = TrackableInfo(id = "cal", goalValue = 600),
                activeMinutes = TrackableInfo(id = "min", goalValue = 45),
            ),
        )
        coEvery { apiService.getSyncStatus("Bearer jwt-token-123") } returns statusResponse

        val vm = createViewModel()
        val state = vm.uiState.value

        assertEquals(12000, state.goalSteps)
        assertEquals(600, state.goalCalories)
        assertEquals(45, state.goalMinutes)

        coVerify { apiService.getSyncStatus("Bearer jwt-token-123") }
    }

    @Test
    fun should_useDefaultGoals_when_apiCallFails() = runTest {
        every { preferences.jwtToken } returns "jwt-token-123"
        coEvery { apiService.getSyncStatus(any()) } throws RuntimeException("Network error")

        val vm = createViewModel()
        val state = vm.uiState.value

        // Default goals from DashboardUiState
        assertEquals(10000, state.goalSteps)
        assertEquals(500, state.goalCalories)
        assertEquals(90, state.goalMinutes)
    }

    @Test
    fun should_skipGoalLoading_when_tokenIsNull() = runTest {
        every { preferences.jwtToken } returns null

        val vm = createViewModel()

        coVerify(exactly = 0) { apiService.getSyncStatus(any()) }

        // Defaults remain
        assertEquals(10000, vm.uiState.value.goalSteps)
    }

    // ---------------------------------------------------------------
    // Health Connect data errors
    // ---------------------------------------------------------------

    @Test
    fun should_showError_when_healthConnectReadFails() = runTest {
        coEvery {
            healthConnectReader.readSteps(any(), any())
        } throws RuntimeException("Permission denied")

        val vm = createViewModel()

        assertTrue(vm.uiState.value.error?.contains("Health Connect") == true)
    }
}
