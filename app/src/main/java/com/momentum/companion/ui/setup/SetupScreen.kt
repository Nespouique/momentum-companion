package com.momentum.companion.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.connectionTestResult) {
        when (val result = uiState.connectionTestResult) {
            is ConnectionTestResult.Success -> {
                snackbarHostState.showSnackbar("Connexion reussie !")
            }
            is ConnectionTestResult.Error -> {
                snackbarHostState.showSnackbar(result.message)
            }
            null -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "MOMENTUM COMPANION",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(48.dp))

            OutlinedTextField(
                value = uiState.serverUrl,
                onValueChange = viewModel::updateServerUrl,
                label = { Text("URL du serveur Momentum") },
                placeholder = { Text("https://momentum.local:3001") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::updateEmail,
                label = { Text("Email") },
                placeholder = { Text("user@example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::updatePassword,
                label = { Text("Mot de passe") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = uiState.allowSelfSignedCerts,
                    onCheckedChange = viewModel::updateAllowSelfSignedCerts,
                )
                Text(
                    text = "Accepter les certificats self-signed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = viewModel::testConnection,
                enabled = !uiState.isTestingConnection,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isTestingConnection) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("TESTER LA CONNEXION")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.completeSetup()
                    onSetupComplete()
                },
                enabled = uiState.isConnectionSuccessful,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("SUIVANT →")
            }
        }
    }
}
