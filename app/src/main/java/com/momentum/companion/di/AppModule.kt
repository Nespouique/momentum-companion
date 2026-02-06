package com.momentum.companion.di

import android.content.Context
import com.momentum.companion.data.log.SyncLogRepository
import com.momentum.companion.data.preferences.AppPreferences
import com.momentum.companion.sync.SyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppPreferences(
        @ApplicationContext context: Context,
    ): AppPreferences {
        return AppPreferences(context)
    }

    @Provides
    @Singleton
    fun provideSyncScheduler(
        @ApplicationContext context: Context,
    ): SyncScheduler {
        return SyncScheduler(context)
    }

    @Provides
    @Singleton
    fun provideSyncLogRepository(
        @ApplicationContext context: Context,
    ): SyncLogRepository {
        return SyncLogRepository(context)
    }
}
