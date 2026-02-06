package com.momentum.companion.di

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import com.momentum.companion.data.healthconnect.HealthConnectReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HealthConnectAvailable

@Module
@InstallIn(SingletonComponent::class)
object HealthModule {

    @Provides
    @Singleton
    fun provideHealthConnectClient(
        @ApplicationContext context: Context,
    ): HealthConnectClient? {
        val status = HealthConnectClient.getSdkStatus(context)
        if (status != HealthConnectClient.SDK_AVAILABLE) return null
        return HealthConnectClient.getOrCreate(context)
    }

    @Provides
    @Singleton
    fun provideHealthConnectReader(
        client: HealthConnectClient?,
    ): HealthConnectReader? {
        return client?.let { HealthConnectReader(it) }
    }
}
