package com.momentum.companion.ui.setup

import com.momentum.companion.data.api.MomentumApiService
import com.momentum.companion.data.api.models.LoginRequest
import com.momentum.companion.data.api.models.LoginResponse
import com.momentum.companion.data.api.models.UserInfo
import com.momentum.companion.data.preferences.AppPreferences
import com.momentum.companion.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var apiService: MomentumApiService
    private lateinit var preferences: AppPreferences
    private lateinit var syncScheduler: SyncScheduler
    private lateinit var viewModel: SetupViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        apiService = mockk(relaxed = true)
        preferences = mockk(relaxed = true)
        syncScheduler = mockk(relaxed = true)

        // Default preference stubs so the init block reads clean values
        every { preferences.serverUrl } returns null
        every { preferences.email } returns null
        every { preferences.allowSelfSignedCerts } returns false

        viewModel = SetupViewModel(apiService, preferences, syncScheduler)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------
    // Initial state
    // ---------------------------------------------------------------

    @Test
    fun should_haveEmptyFields_when_preferencesAreEmpty() {
        val state = viewModel.uiState.value

        assertEquals("https://", state.serverUrl)
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertFalse(state.allowSelfSignedCerts)
        assertFalse(state.isTestingConnection)
        assertNull(state.connectionTestResult)
        assertFalse(state.isConnectionSuccessful)
        assertNull(state.error)
    }

    @Test
    fun should_prefillFields_when_preferencesHaveSavedValues() {
        every { preferences.serverUrl } returns "https://my.server.com/"
        every { preferences.email } returns "user@example.com"
        every { preferences.allowSelfSignedCerts } returns true

        val vm = SetupViewModel(apiService, preferences, syncScheduler)
        val state = vm.uiState.value

        assertEquals("https://my.server.com/", state.serverUrl)
        assertEquals("user@example.com", state.email)
        assertTrue(state.allowSelfSignedCerts)
    }

    // ---------------------------------------------------------------
    // Field updates
    // ---------------------------------------------------------------

    @Test
    fun should_updateServerUrl_when_updateServerUrlIsCalled() {
        viewModel.updateServerUrl("https://new.server.com")

        assertEquals("https://new.server.com", viewModel.uiState.value.serverUrl)
        assertNull(viewModel.uiState.value.connectionTestResult)
    }

    @Test
    fun should_updateEmail_when_updateEmailIsCalled() {
        viewModel.updateEmail("test@example.com")

        assertEquals("test@example.com", viewModel.uiState.value.email)
    }

    @Test
    fun should_updatePassword_when_updatePasswordIsCalled() {
        viewModel.updatePassword("secret123")

        assertEquals("secret123", viewModel.uiState.value.password)
    }

    // ---------------------------------------------------------------
    // Validation errors in testConnection
    // ---------------------------------------------------------------

    @Test
    fun should_showUrlError_when_testConnectionWithDefaultUrl() {
        viewModel.testConnection()

        val result = viewModel.uiState.value.connectionTestResult
        assertTrue(result is ConnectionTestResult.Error)
        assertEquals("URL du serveur requise", (result as ConnectionTestResult.Error).message)
    }

    @Test
    fun should_showEmailError_when_testConnectionWithEmptyEmail() {
        viewModel.updateServerUrl("https://valid.server.com")
        viewModel.testConnection()

        val result = viewModel.uiState.value.connectionTestResult
        assertTrue(result is ConnectionTestResult.Error)
        assertEquals("Email requis", (result as ConnectionTestResult.Error).message)
    }

    @Test
    fun should_showPasswordError_when_testConnectionWithEmptyPassword() {
        viewModel.updateServerUrl("https://valid.server.com")
        viewModel.updateEmail("user@example.com")
        viewModel.testConnection()

        val result = viewModel.uiState.value.connectionTestResult
        assertTrue(result is ConnectionTestResult.Error)
        assertEquals("Mot de passe requis", (result as ConnectionTestResult.Error).message)
    }

    // ---------------------------------------------------------------
    // Successful login
    // ---------------------------------------------------------------

    @Test
    fun should_saveTokenAndMarkSuccess_when_loginSucceeds() = runTest {
        val loginResponse = LoginResponse(
            token = "jwt-token-123",
            user = UserInfo(id = "1", email = "user@example.com", name = "Test User"),
        )
        coEvery { apiService.login(any()) } returns loginResponse

        viewModel.updateServerUrl("https://valid.server.com")
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("password123")

        viewModel.testConnection()

        coVerify {
            apiService.login(
                LoginRequest(email = "user@example.com", password = "password123"),
            )
        }
        verify { preferences.jwtToken = "jwt-token-123" }
        verify { preferences.email = "user@example.com" }
        verify { preferences.password = "password123" }

        val state = viewModel.uiState.value
        assertTrue(state.connectionTestResult is ConnectionTestResult.Success)
        assertTrue(state.isConnectionSuccessful)
        assertFalse(state.isTestingConnection)
    }

    // ---------------------------------------------------------------
    // Failed login
    // ---------------------------------------------------------------

    @Test
    fun should_showError_when_loginThrowsException() = runTest {
        coEvery { apiService.login(any()) } throws RuntimeException("401 Unauthorized")

        viewModel.updateServerUrl("https://valid.server.com")
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("wrong-password")

        viewModel.testConnection()

        val state = viewModel.uiState.value
        assertTrue(state.connectionTestResult is ConnectionTestResult.Error)
        assertEquals(
            "401 Unauthorized",
            (state.connectionTestResult as ConnectionTestResult.Error).message,
        )
        assertFalse(state.isTestingConnection)
        assertFalse(state.isConnectionSuccessful)
    }

    // ---------------------------------------------------------------
    // completeSetup
    // ---------------------------------------------------------------

    @Test
    fun should_schedulePeriodicSync_when_completeSetupIsCalled() {
        every { preferences.syncFrequencyMinutes } returns 30

        viewModel.completeSetup()

        verify { syncScheduler.schedulePeriodic(30) }
    }
}
