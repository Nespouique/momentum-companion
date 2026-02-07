package com.momentum.companion.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    onViewLogs: () -> Unit,
    onHCExplorer: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parametres") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Server section
            SettingsSection(title = "Serveur") {
                Text(
                    text = uiState.serverUrl,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            HorizontalDivider()

            // Account section
            SettingsSection(title = "Compte") {
                Text(
                    text = uiState.email,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        viewModel.disconnect()
                        onDisconnect()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Deconnecter")
                }
            }

            HorizontalDivider()

            // Sync frequency section
            SettingsSection(title = "Frequence de sync") {
                SYNC_FREQUENCY_OPTIONS.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = uiState.syncFrequencyMinutes == option.minutes,
                                onClick = { viewModel.updateSyncFrequency(option.minutes) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = uiState.syncFrequencyMinutes == option.minutes,
                            onClick = null,
                        )
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            HorizontalDivider()

            // Profile section
            SettingsSection(title = "Profil") {
                Text(
                    text = "Utilise pour estimer les minutes actives et calories a partir des pas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))

                var stepsText by remember(uiState.stepsPerMin) {
                    mutableStateOf(uiState.stepsPerMin.toString())
                }
                var ageText by remember(uiState.age) {
                    mutableStateOf(uiState.age.toString())
                }
                var weightText by remember(uiState.weightKg) {
                    mutableStateOf(uiState.weightKg.toInt().toString())
                }
                var heightText by remember(uiState.heightCm) {
                    mutableStateOf(uiState.heightCm.toString())
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = stepsText,
                        onValueChange = { v ->
                            stepsText = v
                            v.toIntOrNull()?.let { if (it in 50..200) viewModel.updateStepsPerMin(it) }
                        },
                        label = { Text("Pas/min") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = ageText,
                        onValueChange = { v ->
                            ageText = v
                            v.toIntOrNull()?.let { if (it in 10..120) viewModel.updateAge(it) }
                        },
                        label = { Text("Age") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { v ->
                            weightText = v
                            v.toIntOrNull()?.let { if (it in 30..250) viewModel.updateWeightKg(it.toFloat()) }
                        },
                        label = { Text("Poids (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { v ->
                            heightText = v
                            v.toIntOrNull()?.let { if (it in 100..250) viewModel.updateHeightCm(it) }
                        },
                        label = { Text("Taille (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Sexe",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(48.dp),
                    )
                    Row(
                        modifier = Modifier
                            .selectable(
                                selected = uiState.isMale,
                                onClick = { viewModel.updateIsMale(true) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = uiState.isMale, onClick = null)
                        Text("Homme", modifier = Modifier.padding(start = 4.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Row(
                        modifier = Modifier
                            .selectable(
                                selected = !uiState.isMale,
                                onClick = { viewModel.updateIsMale(false) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = !uiState.isMale, onClick = null)
                        Text("Femme", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            HorizontalDivider()

            // Initial import section
            SettingsSection(title = "Sync initial") {
                Text(
                    text = "Importer les 30 derniers jours",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (uiState.isImporting) {
                    LinearProgressIndicator(
                        progress = { uiState.importProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${uiState.importDaysCompleted} / 30 jours importes",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Button(
                        onClick = { viewModel.startInitialImport() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("LANCER IMPORT INITIAL")
                    }
                }
            }

            // Error display
            if (uiState.error != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = uiState.error!!,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            HorizontalDivider()

            // Debug section
            SettingsSection(title = "Debug") {
                OutlinedButton(
                    onClick = onViewLogs,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("VOIR LES LOGS")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onHCExplorer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("EXPLORER HEALTH CONNECT")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}
