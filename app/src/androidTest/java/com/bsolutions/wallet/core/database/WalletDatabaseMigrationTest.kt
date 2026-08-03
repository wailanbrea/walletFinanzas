package com.bsolutions.wallet.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.room.testing.MigrationTestHelper
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletDatabaseMigrationTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WalletDatabase::class.java
    )

    @Test
    fun migration14To15_addsCanonicalDedupeWithoutLosingDetectedMovements() {
        val databaseName = "migration_14_15_test.db"
        migrationHelper.createDatabase(databaseName, 14).apply {
            execSQL(
                "INSERT INTO detected_movements " +
                    "(ownerId, id, source, senderOrApp, title, rawBody, merchant, amountMinor, currency, " +
                    "last4Digits, detectedAt, status, suggestedCategoryId, confidence, needsSync) VALUES " +
                    "('owner-1', 'legacy-1', 'EMAIL', 'Gmail', 'Compra', '', 'Amazon', 10000, 'DOP', " +
                    "'1234', 1000, 'PENDING', NULL, 90, 0)"
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            15,
            true,
            WalletDatabaseMigrations.MIGRATION_14_15
        )

        migrated.query(
            "SELECT occurredAt, canonicalId, dedupeState, duplicateOfId " +
                "FROM detected_movements WHERE id = 'legacy-1'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1000L, cursor.getLong(0))
            assertEquals("legacy-1", cursor.getString(1))
            assertEquals("CANONICAL", cursor.getString(2))
            assertTrue(cursor.isNull(3))
        }
        migrated.close()
    }

    @Test
    fun migration13To14_addsSecureRawNoticeCorpus() {
        val databaseName = "migration_13_14_test.db"
        migrationHelper.createDatabase(databaseName, 13).close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            14,
            true,
            WalletDatabaseMigrations.MIGRATION_13_14
        )
        migrated.execSQL(
            "INSERT INTO notification_sources " +
                "(ownerId, packageName, displayName, isEnabled, lastSeenAt, observedCount) " +
                "VALUES ('guest', 'com.bank.app', 'Banco', 1, 1000, 1)"
        )
        migrated.execSQL(
            "INSERT INTO raw_bank_notices " +
                "(ownerId, id, packageName, appLabel, notificationKeyHash, contentHash, title, text, bigText, postTime, capturedAt, expiresAt) " +
                "VALUES ('guest', 'notice-1', 'com.bank.app', 'Banco', 'key-hash', 'content-hash', " +
                "'Compra', 'RD$ 100.00', '', 1000, 1001, 2000)"
        )

        migrated.query("SELECT packageName, text FROM raw_bank_notices").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("com.bank.app", cursor.getString(0))
            assertEquals("RD$ 100.00", cursor.getString(1))
        }
        migrated.close()
    }

    @Test
    fun migration2To3_addsFinancialInstitutionColumns_andPreservesAccounts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("migration_2_3_test.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE accounts (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, " +
                                "balance INTEGER NOT NULL, currency TEXT NOT NULL, isDeleted INTEGER NOT NULL)"
                        )
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
        val database = helper.writableDatabase
        try {
            database.execSQL("INSERT INTO accounts VALUES ('legacy', 'Cuenta existente', 'BANK', 1000, 'DOP', 0)")

            WalletDatabaseMigrations.MIGRATION_2_3.migrate(database)

            database.query("SELECT countryCode, institutionName, balance FROM accounts WHERE id = 'legacy'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("DO", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertEquals(1000L, cursor.getLong(2))
            }
        } finally {
            helper.close()
            context.deleteDatabase("migration_2_3_test.db")
        }
    }

    @Test
    fun migration7To8_marksExistingCategoriesForInitialSync() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("migration_7_8_test.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE categories (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                                "icon TEXT NOT NULL, colorHex TEXT NOT NULL, isDeleted INTEGER NOT NULL)"
                        )
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
        val database = helper.writableDatabase
        try {
            database.execSQL(
                "INSERT INTO categories VALUES ('cat_existente', 'Existente', 'category', '#90A4AE', 0)"
            )

            WalletDatabaseMigrations.MIGRATION_7_8.migrate(database)

            database.query("SELECT needsSync FROM categories WHERE id = 'cat_existente'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase("migration_7_8_test.db")
        }
    }

    @Test
    fun migration8To9_preservesExistingDataInIsolatedGuestPartition() {
        val databaseName = "migration_8_9_test.db"
        migrationHelper.createDatabase(databaseName, 8).apply {
            execSQL(
                "INSERT INTO accounts (id, name, type, balance, currency, countryCode, institutionName, cardLastFour, isDeleted) " +
                    "VALUES ('00000000-0000-4000-8000-000000000001', 'Existente', 'CASH', 1234, 'DOP', 'DO', NULL, NULL, 0)"
            )
            execSQL(
                "INSERT INTO budgets (id, categoryId, limitAmount, spentAmount, period, isDeleted) " +
                    "VALUES ('00000000-0000-4000-8000-000000000002', 'cat_comida', 5000, 1000, 'MONTHLY', 0)"
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            9,
            true,
            WalletDatabaseMigrations.MIGRATION_8_9
        )
        migrated.query("SELECT ownerId, balance FROM accounts").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("guest", cursor.getString(0))
            assertEquals(1234L, cursor.getLong(1))
        }
        migrated.query("SELECT ownerId, needsSync FROM budgets").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("guest", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }
        migrated.close()
    }

    @Test
    fun migration9To10_addsNullableCreditLimit_andPreservesAccountType() {
        val databaseName = "migration_9_10_test.db"
        migrationHelper.createDatabase(databaseName, 9).apply {
            execSQL(
                "INSERT INTO accounts (ownerId, id, name, type, balance, currency, countryCode, " +
                    "institutionName, cardLastFour, isDeleted) VALUES " +
                    "('owner-1', 'credit-1', 'Tarjeta', 'CREDIT_CARD', -2500, 'DOP', 'DO', NULL, '1234', 0)"
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            10,
            true,
            WalletDatabaseMigrations.MIGRATION_9_10
        )
        migrated.query("SELECT type, balance, creditLimit FROM accounts WHERE id = 'credit-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("CREDIT_CARD", cursor.getString(0))
            assertEquals(-2500L, cursor.getLong(1))
            assertTrue(cursor.isNull(2))
        }
        migrated.execSQL("UPDATE accounts SET creditLimit = 150000 WHERE id = 'credit-1'")
        migrated.query("SELECT creditLimit FROM accounts WHERE id = 'credit-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(150000L, cursor.getLong(0))
        }
        migrated.close()
    }
}
