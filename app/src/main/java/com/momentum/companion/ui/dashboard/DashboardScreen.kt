package com.momentum.companion.ui.dashboard

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.momentum.companion.ui.theme.MomentumBlue
import com.momentum.companion.ui.theme.MomentumGreen
import com.momentum.companion.ui.theme.MomentumRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSettingsClick: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    "MOMENTUM COMPANION",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            actions = {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Parametres")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Connection status
                item {
                    ConnectionStatusCard(
                        serverUrl = uiState.serverUrl,
                        lastSync = uiState.lastSyncFormatted,
                        isConnected = uiState.isConnected,
                    )
                }

                // Today's metrics
                item {
                    TodayMetricsCard(
                        steps = uiState.currentSteps,
                        stepsGoal = uiState.goalSteps,
                        activeMinutes = uiState.currentMinutes,
                        activeMinutesGoal = uiState.goalMinutes,
                        calories = uiState.currentCalories,
                        caloriesGoal = uiState.goalCalories,
                    )
                }

                // Sync button
                item {
                    Button(
                        onClick = { viewModel.syncNow() },
                        enabled = !uiState.isSyncing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (uiState.isSyncing) "SYNCHRONISATION..." else "SYNCHRONISER MAINTENANT",
                        )
                    }
                }

                // Error display
                if (uiState.error != null) {
                    item {
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
                }

                // Activities
                if (uiState.todayActivities.isNotEmpty()) {
                    item {
                        Text(
                            "Activites aujourd'hui :",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    items(uiState.todayActivities) { activity ->
                        ActivityListItem(activity = activity)
                    }
                }

                // Bottom spacing
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    serverUrl: String,
    lastSync: String,
    isConnected: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Circle,
            contentDescription = null,
            tint = if (isConnected) MomentumGreen else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(10.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = if (isConnected) "Connecte a $serverUrl" else "Non connecte",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Dernier sync : $lastSync",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TodayMetricsCard(
    steps: Int,
    stepsGoal: Int,
    activeMinutes: Int,
    activeMinutesGoal: Int,
    calories: Int,
    caloriesGoal: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Aujourd'hui",
                style = MaterialTheme.typography.titleMedium,
            )

            MetricProgressRow(
                icon = Icons.Default.DirectionsWalk,
                currentValue = steps,
                goalValue = stepsGoal,
                unit = "pas",
                color = MomentumGreen,
            )

            MetricProgressRow(
                icon = Icons.Default.Timer,
                currentValue = activeMinutes,
                goalValue = activeMinutesGoal,
                unit = "min",
                color = MomentumBlue,
            )

            MetricProgressRow(
                icon = Icons.Default.LocalFireDepartment,
                currentValue = calories,
                goalValue = caloriesGoal,
                unit = "kcal",
                color = MomentumRed,
            )
        }
    }
}

@Composable
private fun MetricProgressRow(
    icon: ImageVector,
    currentValue: Int,
    goalValue: Int,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val progress = if (goalValue > 0) {
        (currentValue.toFloat() / goalValue).coerceIn(0f, 1f)
    } else {
        0f
    }
    val percentage = (progress * 100).toInt()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "%,d / %,d %s".format(currentValue, goalValue, unit),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
        )
    }
}

@Composable
private fun ActivityListItem(
    activity: TodayActivity,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = activity.startTime,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "- ",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = activity.activityType,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = " (${activity.durationMinutes} min${
                if (activity.distanceKm != null) ", %.1f km".format(activity.distanceKm) else ""
            })",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
