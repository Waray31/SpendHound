package com.waray.spendhound.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CachedMessage::class, CachedTransaction::class, CachedJsonBlob::class, PendingTransaction::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun transactionDao(): TransactionDao
    abstract fun jsonBlobDao(): JsonBlobDao
    abstract fun pendingTransactionDao(): PendingTransactionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `pending_transactions` (`localId` TEXT NOT NULL, `groupId` INTEGER NOT NULL, `createdByUserId` INTEGER NOT NULL, `description` TEXT, `totalAmount` REAL NOT NULL, `itemsJson` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `status` INTEGER NOT NULL, `retryCount` INTEGER NOT NULL, `failureReason` TEXT, PRIMARY KEY(`localId`))")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `pending_transactions_new` (`localId` TEXT NOT NULL, `groupId` INTEGER, `createdByUserId` INTEGER NOT NULL, `description` TEXT, `totalAmount` REAL NOT NULL, `itemsJson` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `status` INTEGER NOT NULL, `retryCount` INTEGER NOT NULL, `failureReason` TEXT, PRIMARY KEY(`localId`))")
                db.execSQL("INSERT INTO `pending_transactions_new` SELECT * FROM `pending_transactions`")
                db.execSQL("DROP TABLE `pending_transactions`")
                db.execSQL("ALTER TABLE `pending_transactions_new` RENAME TO `pending_transactions`")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure groupId is nullable. Some users might have version 5 with NOT NULL groupId.
                db.execSQL("CREATE TABLE IF NOT EXISTS `pending_transactions_new` (`localId` TEXT NOT NULL, `groupId` INTEGER, `createdByUserId` INTEGER NOT NULL, `description` TEXT, `totalAmount` REAL NOT NULL, `itemsJson` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `status` INTEGER NOT NULL, `retryCount` INTEGER NOT NULL, `failureReason` TEXT, PRIMARY KEY(`localId`))")
                try {
                    db.execSQL("INSERT INTO `pending_transactions_new` SELECT * FROM `pending_transactions`")
                } catch (e: Exception) {
                    android.util.Log.e("AppDatabase", "Migration 5->6 failed to copy data: ${e.message}")
                }
                db.execSQL("DROP TABLE IF EXISTS `pending_transactions`")
                db.execSQL("ALTER TABLE `pending_transactions_new` RENAME TO `pending_transactions`")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "spendhound_cache.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
