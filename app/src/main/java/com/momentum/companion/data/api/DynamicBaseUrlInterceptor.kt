package com.momentum.companion.data.api

import com.momentum.companion.data.preferences.AppPreferences
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicBaseUrlInterceptor @Inject constructor(
    private val preferences: AppPreferences,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val serverUrl = preferences.serverUrl

        if (serverUrl != null) {
            val baseHttpUrl = serverUrl.toHttpUrlOrNull()
            if (baseHttpUrl != null) {
                val newUrl = request.url.newBuilder()
                    .scheme(baseHttpUrl.scheme)
                    .host(baseHttpUrl.host)
                    .port(baseHttpUrl.port)
                    .build()
                request = request.newBuilder().url(newUrl).build()
            }
        }

        return chain.proceed(request)
    }
}
