package com.bsolutions.wallet.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object WalletDatabaseMigrations {
    /** v14 -> v15: identidad por origen y grupos canónicos para dedupe cruzado. */
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE detected_movements ADD COLUMN sourceReference TEXT")
            database.execSQL("ALTER TABLE detected_movements ADD COLUMN occurredAt INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE detected_movements ADD COLUMN direction TEXT NOT NULL DEFAULT 'expense'")
            database.execSQL("ALTER TABLE detected_movements ADD COLUMN eventType TEXT")
            database.execSQL("ALTER TABLE detected_movements ADD COLUMN baseAmountMinor INTEGER")
            database.execSQL("ALTER TABLE detected_movements ADD COLUMN baseCurrency TEXT")
            database.execSQL("ALTER TABLE detected_movements ADD COLUMN canonicalId TEXT")
            database.execSQL("ALTER TABLE detected_movements ADD COLUMN duplicateOfId TEXT")
            database.execSQL("ALTER TABLE detected_movements ADD COLUMN possibleDuplicateOfId TEXT")
            database.execSQL("ALTER TABLE detected_movements ADD COLUMN dedupeState TEXT NOT NULL DEFAULT 'CANONICAL'")
            database.execSQL("ALTER TABLE detected_movements ADD COLUMN dedupeReason TEXT")
            database.execSQL("UPDATE detected_movements SET occurredAt = detectedAt, canonicalId = id")
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_detected_movements_ownerId_source_sourceReference " +
                    "ON detected_movements(ownerId, source, sourceReference)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_detected_movements_ownerId_occurredAt " +
                    "ON detected_movements(ownerId, occurredAt)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_detected_movements_ownerId_canonicalId " +
                    "ON detected_movements(ownerId, canonicalId)"
            )
        }
    }

    /** v13 -> v14: fuentes autorizadas y avisos push crudos de la Fase A. */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS notification_sources (
                    ownerId TEXT NOT NULL,
                    packageName TEXT NOT NULL,
                    displayName TEXT NOT NULL,
                    isEnabled INTEGER NOT NULL,
                    lastSeenAt INTEGER NOT NULL,
                    observedCount INTEGER NOT NULL,
                    PRIMARY KEY(ownerId, packageName)
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS raw_bank_notices (
                    ownerId TEXT NOT NULL,
                    id TEXT NOT NULL,
                    packageName TEXT NOT NULL,
                    appLabel TEXT NOT NULL,
                    notificationKeyHash TEXT NOT NULL,
                    contentHash TEXT NOT NULL,
                    title TEXT NOT NULL,
                    text TEXT NOT NULL,
                    bigText TEXT NOT NULL,
                    postTime INTEGER NOT NULL,
                    capturedAt INTEGER NOT NULL,
                    expiresAt INTEGER NOT NULL,
                    PRIMARY KEY(ownerId, id)
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_raw_bank_notices_ownerId_postTime ON raw_bank_notices(ownerId, postTime)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_raw_bank_notices_ownerId_packageName ON raw_bank_notices(ownerId, packageName)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_raw_bank_notices_ownerId_expiresAt ON raw_bank_notices(ownerId, expiresAt)"
            )
        }
    }

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

    /**
     * v12 -> v13: tabla detected_movements para guardar candidatos de correo y push.
     */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS detected_movements (
                    id TEXT NOT NULL,
                    source TEXT NOT NULL,
                    senderOrApp TEXT NOT NULL DEFAULT '',
                    title TEXT NOT NULL DEFAULT '',
                    rawBody TEXT NOT NULL DEFAULT '',
                    merchant TEXT,
                    amountMinor INTEGER,
                    currency TEXT,
                    last4Digits TEXT,
                    detectedAt INTEGER NOT NULL,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    suggestedCategoryId TEXT,
                    confidence INTEGER NOT NULL DEFAULT 0,
                    needsSync INTEGER NOT NULL DEFAULT 0,
                    ownerId TEXT NOT NULL,
                    PRIMARY KEY(ownerId, id)
                )
            """.trimIndent())
        }
    }

    /**
     * v11 -> v12: un movimiento puede pertenecer a una deuda.
     *
     * Es el hilo que une prestar el dinero con cobrarlo: el gasto del prestamo y cada
     * abono recibido apuntan a la misma deuda, de modo que lo cobrado se calcula de los
     * movimientos reales en vez de ser un numero que se edita aparte y se desincroniza.
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN debtId TEXT")
        }
    }

    /**
     * v10 -> v11: las categorias distinguen ingreso de gasto. Todas las existentes
     * quedan como gasto salvo Salario, que es lo unico que se sembraba como ingreso;
     * asi un gasto deja de poder etiquetarse "Salario" y al reves.
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE categories ADD COLUMN type TEXT NOT NULL DEFAULT 'EXPENSE'")
            database.execSQL("UPDATE categories SET type = 'INCOME' WHERE id = 'cat_salario'")
            database.execSQL("UPDATE categories SET type = 'BOTH' WHERE id = 'cat_otros'")
            // Se vuelven a subir para que el backend reciba el tipo.
            database.execSQL("UPDATE categories SET needsSync = 1")
        }
    }

    /** v9 -> v10: límite de crédito opcional, expresado en unidades menores. */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE accounts ADD COLUMN creditLimit INTEGER")
        }
    }
}
