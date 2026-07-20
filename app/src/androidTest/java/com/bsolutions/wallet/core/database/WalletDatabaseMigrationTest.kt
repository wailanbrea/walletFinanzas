package com.bsolutions.wallet.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletDatabaseMigrationTest {
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
}
