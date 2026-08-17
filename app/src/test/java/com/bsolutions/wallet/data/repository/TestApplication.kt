package com.bsolutions.wallet.data.repository

import android.app.Application

/**
 * Application de tests: Robolectric crea la Application declarada en @Config. Si dejara
 * crear WalletApp, su onCreate arrancaria Hilt y DatabaseModule cargaria la libreria
 * nativa de SQLCipher, que no existe en la JVM. Con una Application vacia el Contexto
 * queda disponible para Room sin tocar la inyeccion.
 */
class TestApplication : Application()
