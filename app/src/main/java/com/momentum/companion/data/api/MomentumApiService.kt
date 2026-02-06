package com.momentum.companion.data.api

import com.momentum.companion.data.api.models.HealthSyncRequest
import com.momentum.companion.data.api.models.HealthSyncResponse
import com.momentum.companion.data.api.models.LoginRequest
import com.momentum.companion.data.api.models.LoginResponse
import com.momentum.companion.data.api.models.SyncStatusResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface MomentumApiService {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("health-sync")
    suspend fun postHealthSync(
        @Header("Authorization") token: String,
        @Body request: HealthSyncRequest,
    ): HealthSyncResponse

    @GET("health-sync/status")
    suspend fun getSyncStatus(
        @Header("Authorization") token: String,
    ): SyncStatusResponse
}
