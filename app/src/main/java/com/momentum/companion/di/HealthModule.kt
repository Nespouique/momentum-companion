package com.momentum.companion.di

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import com.momentum.companion.data.healthconnect.HealthConnectReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HealthModule {

    @Provides
    @Singleton
    fun provideHealthConnectClient(
        @ApplicationContext context: Context,
    ): HealthConnectClient {
        return HealthConnectClient.getOrCreate(context)
    }

    @Provides
    @Singleton
    fun provideHealthConnectReader(
        client: HealthConnectClient,
    ): HealthConnectReader {
        return HealthConnectReader(client)
    }
}
