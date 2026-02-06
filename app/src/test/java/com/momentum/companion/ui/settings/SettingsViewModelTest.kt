package com.momentum.companion.ui.settings

import com.momentum.companion.data.api.MomentumApiService
import com.momentum.companion.data.healthconnect.HealthConnectReader
import com.momentum.companion.data.log.SyncLogRepository
import com.momentum.companion.data.preferences.AppPreferences
import com.momentum.companion.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var preferences: AppPreferences
    private lateinit var syncScheduler: SyncScheduler
    private lateinit var healthConnectReader: HealthConnectReader
    private lateinit var apiService: MomentumApiService
    private lateinit var syncLogRepository: SyncLogRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        preferences = mockk(relaxed = true)
        syncScheduler = mockk(relaxed = true)
        healthConnectReader = mockk(relaxed = true)
        apiService = mockk(relaxed = true)
        syncLogRepository = mockk(relaxed = true)

        // Sensible defaults for init block
        every { preferences.serverUrl } returns "https://server.test/"
        every { preferences.email } returns "user@test.com"
        every { preferences.syncFrequencyMinutes } returns 15

        coEvery { syncLogRepository.getRecentLogs(any()) } returns emptyList()
        coEvery { syncLogRepository.getRecentLogs() } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------

    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(
            preferences,
            syncScheduler,
            healthConnectReader,
            apiService,
            syncLogRepository,
        )
    }

    // ---------------------------------------------------------------
    // Initial state
    // ---------------------------------------------------------------

    @Test
    fun should_loadSettingsFromPreferences_when_initialised() {
        val vm = createViewModel()
        val state = vm.uiState.value

        assertEquals("https://server.test/", state.serverUrl)
        assertEquals("user@test.com", state.email)
        assertEquals(15, state.syncFrequencyMinutes)
        assertFalse(state.isImporting)
        assertEquals(0f, state.importProgress)
    }

    // ---------------------------------------------------------------
    // updateSyncFrequency
    // ---------------------------------------------------------------

    @Test
    fun should_saveToPreferencesAndReschedule_when_updateSyncFrequencyIsCalled() = runTest {
        val vm = createViewModel()

        vm.updateSyncFrequency(60)

        verify { preferences.syncFrequencyMinutes = 60 }
        verify { syncScheduler.schedulePeriodic(60) }
        assertEquals(60, vm.uiState.value.syncFrequencyMinutes)
    }

    @Test
    fun should_updateUiState_when_syncFrequencyChangedTo30() = runTest {
        val vm = createViewModel()

        vm.updateSyncFrequency(30)

        assertEquals(30, vm.uiState.value.syncFrequencyMinutes)
    }

    // ---------------------------------------------------------------
    // disconnect
    // ---------------------------------------------------------------

    @Test
    fun should_clearPreferencesAndCancelWork_when_disconnectIsCalled() {
        val vm = createViewModel()

        vm.disconnect()

        verify { syncScheduler.cancelAll() }
        verify { preferences.clearAll() }
    }

    @Test
    fun should_callCancelBeforeClear_when_disconnectIsCalled() {
        val vm = createViewModel()
        val callOrder = mutableListOf<String>()

        every { syncScheduler.cancelAll() } answers { callOrder.add("cancelAll") }
        every { preferences.clearAll() } answers { callOrder.add("clearAll") }

        vm.disconnect()

        assertEquals(listOf("cancelAll", "clearAll"), callOrder)
    }

    // ---------------------------------------------------------------
    // Logs
    // ---------------------------------------------------------------

    @Test
    fun should_loadEmptyLogs_when_noLogsExist() {
        coEvery { syncLogRepository.getRecentLogs() } returns emptyList()

        val vm = createViewModel()

        assertEquals(emptyList<Any>(), vm.logs.value)
    }
}
