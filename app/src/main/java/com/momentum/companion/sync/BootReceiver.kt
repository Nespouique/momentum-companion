package com.momentum.companion.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.momentum.companion.data.preferences.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Re-schedules the periodic sync after device reboot.
 *
 * WorkManager periodic work does not automatically survive reboots on all Android versions.
 * This receiver ensures the sync is re-registered whenever the device boots,
 * but only if the user has already completed setup (i.e., a JWT token is stored).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferences: AppPreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Only re-schedule if setup is complete
        if (preferences.jwtToken.isNullOrBlank()) return

        SyncScheduler(context).schedulePeriodic(preferences.syncFrequencyMinutes)
    }
}
