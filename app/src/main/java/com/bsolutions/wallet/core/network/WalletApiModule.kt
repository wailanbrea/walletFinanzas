package com.bsolutions.wallet.core.network

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.bsolutions.wallet.BuildConfig
import com.bsolutions.wallet.data.repository.DefaultEmailConnectionsRepository
import com.bsolutions.wallet.data.repository.EmailConnectionsRepository
import com.bsolutions.wallet.data.repository.EncryptedWalletSessionStore
import com.bsolutions.wallet.data.repository.WalletAuthRepository
import com.bsolutions.wallet.core.database.LocalDataIsolation
import com.bsolutions.wallet.data.repository.WalletSessionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.util.UUID
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WalletApiModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideWalletSessionStore(
        @ApplicationContext context: Context
    ): WalletSessionStore = EncryptedWalletSessionStore(context)

    @Provides
    @Singleton
    fun provideWalletApi(session: WalletSessionStore, gson: Gson): WalletApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(WalletAuthInterceptor(session))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                    })
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.WALLET_API_BASE_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(WalletApi::class.java)
    }

    @Provides
    @Singleton
    fun provideWalletAuthRepository(
        api: WalletApi,
        session: WalletSessionStore,
        localDataIsolation: LocalDataIsolation,
        @ApplicationContext context: Context
    ): WalletAuthRepository = WalletAuthRepository(
        api = api,
        session = session,
        deviceName = walletDeviceName(
            Build.MODEL,
            walletInstallationId(context)
        ),
        localDataIsolation = localDataIsolation
    )

    @Provides
    @Singleton
    fun provideEmailConnectionsRepository(
        api: WalletApi,
        session: WalletSessionStore
    ): EmailConnectionsRepository = DefaultEmailConnectionsRepository(api, session)
}

fun walletDeviceName(model: String, installationId: String): String =
    "android-$model-$installationId".take(120)

private fun walletInstallationId(context: Context): String {
    val preferences = context.getSharedPreferences(DEVICE_PREFERENCES, Context.MODE_PRIVATE)
    preferences.getString(KEY_INSTALLATION_ID, null)?.let { return it }

    return UUID.randomUUID().toString().also { generatedId ->
        preferences.edit { putString(KEY_INSTALLATION_ID, generatedId) }
    }
}

private const val DEVICE_PREFERENCES = "wallet_installation"
private const val KEY_INSTALLATION_ID = "installation_id"
