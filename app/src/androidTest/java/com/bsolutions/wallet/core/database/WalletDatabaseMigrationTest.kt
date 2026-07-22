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
    fun migration2To3_addsFinancialInstitutionColumns_andPreservesAccounts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("migration_2_3_test.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL(
                            "CREATE TABLE accounts (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, " +
                                "balance INTEGER NOT NULL, currency TEXT NOT NULL, isDeleted INTEGER NOT NULL)"
                        )
                    }

                    override fun onUpgrade(
                        database: androidx.sqlite.db.SupportSQLiteDatabase,
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
                    override fun onCreate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL(
                            "CREATE TABLE categories (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                                "icon TEXT NOT NULL, colorHex TEXT NOT NULL, isDeleted INTEGER NOT NULL)"
                        )
                    }

                    override fun onUpgrade(
                        database: androidx.sqlite.db.SupportSQLiteDatabase,
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
}
