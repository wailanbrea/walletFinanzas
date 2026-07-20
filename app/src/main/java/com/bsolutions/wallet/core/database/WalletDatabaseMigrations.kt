package com.bsolutions.wallet.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object WalletDatabaseMigrations {
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE accounts ADD COLUMN countryCode TEXT NOT NULL DEFAULT 'DO'")
            database.execSQL("ALTER TABLE accounts ADD COLUMN institutionName TEXT")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE accounts ADD COLUMN cardLastFour TEXT")
        }
    }

    /** v5: tabla de conexiones bancarias (Salt Edge, Etapa A). */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS bank_connections (
                    id TEXT NOT NULL PRIMARY KEY,
                    providerName TEXT NOT NULL,
                    providerCode TEXT NOT NULL DEFAULT '',
                    countryCode TEXT NOT NULL DEFAULT '',
                    status TEXT NOT NULL DEFAULT '',
                    lastSyncAt INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
    }

    /** v6: moneda por movimiento (multi-divisa en cuentas bancarias importadas). */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN currency TEXT NOT NULL DEFAULT 'DOP'")
        }
    }
}
