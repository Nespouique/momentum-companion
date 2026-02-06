package com.momentum.companion.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.momentum.companion.data.api.MomentumApiService
import com.momentum.companion.data.api.models.LoginRequest
import com.momentum.companion.data.preferences.AppPreferences
import com.momentum.companion.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val serverUrl: String = "https://",
    val email: String = "",
    val password: String = "",
    val allowSelfSignedCerts: Boolean = false,
    val isTestingConnection: Boolean = false,
    val connectionTestResult: ConnectionTestResult? = null,
    val isConnectionSuccessful: Boolean = false,
    val error: String? = null,
)

sealed interface ConnectionTestResult {
    data object Success : ConnectionTestResult
    data class Error(val message: String) : ConnectionTestResult
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val apiService: MomentumApiService,
    private val preferences: AppPreferences,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        // Pre-fill from saved preferences if any
        _uiState.value = _uiState.value.copy(
            serverUrl = preferences.serverUrl ?: "https://",
            email = preferences.email ?: "",
            allowSelfSignedCerts = preferences.allowSelfSignedCerts,
        )
    }

    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(
            serverUrl = url,
            connectionTestResult = null,
        )
    }

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(
            email = email,
            connectionTestResult = null,
        )
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            connectionTestResult = null,
        )
    }

    fun updateAllowSelfSignedCerts(allow: Boolean) {
        _uiState.value = _uiState.value.copy(allowSelfSignedCerts = allow)
        preferences.allowSelfSignedCerts = allow
    }

    fun testConnection() {
        val state = _uiState.value

        // Basic validation
        if (state.serverUrl.isBlank() || state.serverUrl == "https://") {
            _uiState.value = _uiState.value.copy(
                connectionTestResult = ConnectionTestResult.Error("URL du serveur requise"),
            )
            return
        }
        if (state.email.isBlank()) {
            _uiState.value = _uiState.value.copy(
                connectionTestResult = ConnectionTestResult.Error("Email requis"),
            )
            return
        }
        if (state.password.isBlank()) {
            _uiState.value = _uiState.value.copy(
                connectionTestResult = ConnectionTestResult.Error("Mot de passe requis"),
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingConnection = true)

            // Save server URL first so Retrofit can use it
            val serverUrl = if (state.serverUrl.endsWith("/")) {
                state.serverUrl
            } else {
                "${state.serverUrl}/"
            }
            preferences.serverUrl = serverUrl
            preferences.allowSelfSignedCerts = state.allowSelfSignedCerts

            try {
                val response = apiService.login(
                    LoginRequest(
                        email = state.email,
                        password = state.password,
                    ),
                )

                // Save credentials on success
                preferences.jwtToken = response.token
                preferences.email = state.email
                preferences.password = state.password

                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    connectionTestResult = ConnectionTestResult.Success,
                    isConnectionSuccessful = true,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    connectionTestResult = ConnectionTestResult.Error(
                        e.message ?: "Erreur de connexion inconnue",
                    ),
                )
            }
        }
    }

    fun completeSetup() {
        // Start periodic sync after setup
        syncScheduler.schedulePeriodic(preferences.syncFrequencyMinutes)
    }
}
