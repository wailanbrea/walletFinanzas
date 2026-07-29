package com.bsolutions.wallet.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object WalletDatabaseMigrations {
    /** v1 -> v4: Migración para asegurar compatibilidad de usuarios antiguos. */
    val MIGRATION_1_4 = object : Migration(1, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE accounts ADD COLUMN countryCode TEXT NOT NULL DEFAULT 'DO'")
            database.execSQL("ALTER TABLE accounts ADD COLUMN institutionName TEXT")
            database.execSQL("ALTER TABLE accounts ADD COLUMN cardLastFour TEXT")
        }
    }

    /** v2 -> v4: Migración para usuarios antiguos de la v2. */
    val MIGRATION_2_4 = object : Migration(2, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE accounts ADD COLUMN countryCode TEXT NOT NULL DEFAULT 'DO'")
            database.execSQL("ALTER TABLE accounts ADD COLUMN institutionName TEXT")
            database.execSQL("ALTER TABLE accounts ADD COLUMN cardLastFour TEXT")
        }
    }

    /** v2 -> v3: Adición de país e institución. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE accounts ADD COLUMN countryCode TEXT NOT NULL DEFAULT 'DO'")
            database.execSQL("ALTER TABLE accounts ADD COLUMN institutionName TEXT")
        }
    }

    /** v3 -> v4: Adición de últimos cuatro dígitos de tarjeta. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE accounts ADD COLUMN cardLastFour TEXT")
        }
    }

    /** v4 -> v5: Creación de tabla de conexiones bancarias. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS bank_connections (
                    id TEXT NOT NULL PRIMARY KEY,
                    providerName TEXT NOT NULL,
                    providerCode TEXT NOT NULL DEFAULT '',
                    countryCode TEXT NOT NULL DEFAULT '',
                    status TEXT NOT NULL DEFAULT '',
                    lastSyncAt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
        }
    }

    /** v5 -> v6: Adición de moneda por movimiento. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN currency TEXT NOT NULL DEFAULT 'DOP'")
        }
    }

    /** v6 -> v7: Cola de operaciones pendientes de sincronizar con el backend. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS pending_operations (
                    id TEXT NOT NULL PRIMARY KEY,
                    entityType TEXT NOT NULL,
                    entityId TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    attempts INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
    }

    /** v7 -> v8: marca categorías existentes para su primera subida al catálogo remoto. */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE categories ADD COLUMN needsSync INTEGER NOT NULL DEFAULT 1"
            )
        }
    }

    /**
     * v8 -> v9: separa todos los datos Room por propietario y habilita sync completo
     * para presupuestos, metas, deudas y pagos planificados. Los datos existentes se
     * conservan en el espacio invitado y se reclaman al iniciar sesion por primera vez.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            rebuild(
                database,
                table = "accounts",
                definition = """
                    ownerId TEXT NOT NULL,
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    balance INTEGER NOT NULL,
                    currency TEXT NOT NULL,
                    countryCode TEXT NOT NULL,
                    institutionName TEXT,
                    cardLastFour TEXT,
                    isDeleted INTEGER NOT NULL,
                    PRIMARY KEY(ownerId, id)
                """,
                columns = "id, name, type, balance, currency, countryCode, institutionName, cardLastFour, isDeleted"
            )
            rebuild(
                database,
                table = "transactions",
                definition = """
                    ownerId TEXT NOT NULL,
                    id TEXT NOT NULL,
                    accountId TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    categoryId TEXT NOT NULL,
                    date INTEGER NOT NULL,
                    note TEXT NOT NULL,
                    currency TEXT NOT NULL,
                    isDeleted INTEGER NOT NULL,
                    PRIMARY KEY(ownerId, id)
                """,
                columns = "id, accountId, amount, type, categoryId, date, note, currency, isDeleted"
            )
            rebuild(
                database,
                table = "categories",
                definition = """
                    ownerId TEXT NOT NULL,
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    icon TEXT NOT NULL,
                    colorHex TEXT NOT NULL,
                    isDeleted INTEGER NOT NULL,
                    needsSync INTEGER NOT NULL,
                    PRIMARY KEY(ownerId, id)
                """,
                columns = "id, name, icon, colorHex, isDeleted, needsSync"
            )
            rebuild(
                database,
                table = "budgets",
                definition = """
                    ownerId TEXT NOT NULL,
                    id TEXT NOT NULL,
                    categoryId TEXT NOT NULL,
                    limitAmount INTEGER NOT NULL,
                    spentAmount INTEGER NOT NULL,
                    period TEXT NOT NULL,
                    isDeleted INTEGER NOT NULL,
                    needsSync INTEGER NOT NULL,
                    PRIMARY KEY(ownerId, id)
                """,
                columns = "id, categoryId, limitAmount, spentAmount, period, isDeleted",
                extraSelect = ", 1"
            )
            rebuild(
                database,
                table = "goals",
                definition = """
                    ownerId TEXT NOT NULL,
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    icon TEXT NOT NULL,
                    targetAmount INTEGER NOT NULL,
                    savedAmount INTEGER NOT NULL,
                    targetDate INTEGER,
                    isCompleted INTEGER NOT NULL,
                    isDeleted INTEGER NOT NULL,
                    needsSync INTEGER NOT NULL,
                    PRIMARY KEY(ownerId, id)
                """,
                columns = "id, name, icon, targetAmount, savedAmount, targetDate, isCompleted, isDeleted",
                extraSelect = ", 1"
            )
            rebuild(
                database,
                table = "planned_payments",
                definition = """
                    ownerId TEXT NOT NULL,
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    accountId TEXT NOT NULL,
                    categoryId TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    frequency TEXT NOT NULL,
                    nextDueDate INTEGER NOT NULL,
                    isActive INTEGER NOT NULL,
                    isDeleted INTEGER NOT NULL,
                    needsSync INTEGER NOT NULL,
                    PRIMARY KEY(ownerId, id)
                """,
                columns = "id, name, accountId, categoryId, amount, type, frequency, nextDueDate, isActive, isDeleted",
                extraSelect = ", 1"
            )
            rebuild(
                database,
                table = "debts",
                definition = """
                    ownerId TEXT NOT NULL,
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    direction TEXT NOT NULL,
                    totalAmount INTEGER NOT NULL,
                    paidAmount INTEGER NOT NULL,
                    dueDate INTEGER,
                    isClosed INTEGER NOT NULL,
                    isDeleted INTEGER NOT NULL,
                    needsSync INTEGER NOT NULL,
                    PRIMARY KEY(ownerId, id)
                """,
                columns = "id, name, description, direction, totalAmount, paidAmount, dueDate, isClosed, isDeleted",
                extraSelect = ", 1"
            )
            rebuild(
                database,
                table = "pending_operations",
                definition = """
                    ownerId TEXT NOT NULL,
                    id TEXT NOT NULL,
                    entityType TEXT NOT NULL,
                    entityId TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    attempts INTEGER NOT NULL,
                    PRIMARY KEY(ownerId, id)
                """,
                columns = "id, entityType, entityId, payload, createdAt, attempts"
            )
            rebuild(
                database,
                table = "bank_connections",
                definition = """
                    ownerId TEXT NOT NULL,
                    id TEXT NOT NULL,
                    providerName TEXT NOT NULL,
                    providerCode TEXT NOT NULL,
                    countryCode TEXT NOT NULL,
                    status TEXT NOT NULL,
                    lastSyncAt INTEGER NOT NULL,
                    PRIMARY KEY(ownerId, id)
                """,
                columns = "id, providerName, providerCode, countryCode, status, lastSyncAt"
            )
        }

        private fun rebuild(
            database: SupportSQLiteDatabase,
            table: String,
            definition: String,
            columns: String,
            extraSelect: String = ""
        ) {
            val legacy = "${table}_v8"
            database.execSQL("ALTER TABLE $table RENAME TO $legacy")
            database.execSQL("CREATE TABLE $table ($definition)")
            val targetColumns = if (extraSelect.isEmpty()) "ownerId, $columns" else "ownerId, $columns, needsSync"
            database.execSQL(
                "INSERT INTO $table ($targetColumns) SELECT 'guest', $columns$extraSelect FROM $legacy"
            )
            database.execSQL("DROP TABLE $legacy")
        }
    }

    /** v9 -> v10: límite de crédito opcional, expresado en unidades menores. */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE accounts ADD COLUMN creditLimit INTEGER")
        }
    }
}
