package com.bsolutions.wallet.core.network

import com.bsolutions.wallet.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

/** Disponibilidad del sandbox: solo builds DEBUG con credenciales en local.properties. */
object SaltEdgeConfig {
    val isAvailable: Boolean
        get() = BuildConfig.DEBUG &&
            BuildConfig.SALTEDGE_APP_ID.isNotBlank() &&
            BuildConfig.SALTEDGE_SECRET.isNotBlank()
}

@Module
@InstallIn(SingletonComponent::class)
object SaltEdgeModule {

    @Provides
    @Singleton
    fun provideSaltEdgeApi(): SaltEdgeApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("App-id", BuildConfig.SALTEDGE_APP_ID)
                    .header("Secret", BuildConfig.SALTEDGE_SECRET)
                    .build()
                chain.proceed(request)
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    // Nunca loguear headers (contienen el Secret): solo línea básica
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl("https://www.saltedge.com/api/v6/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SaltEdgeApi::class.java)
    }
}
