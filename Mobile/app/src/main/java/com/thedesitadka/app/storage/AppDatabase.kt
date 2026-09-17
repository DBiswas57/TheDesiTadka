package com.thedesitadka.app.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WatchHistoryEntity::class,
        FavoriteEntity::class,
        DownloadRecordEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    val oldDb = context.getDatabasePath("streamhub.db")
                    val newDb = context.getDatabasePath("thedesitadka.db")
                    if (oldDb.exists() && !newDb.exists()) {
                        oldDb.renameTo(newDb)
                        val oldWal = context.getDatabasePath("streamhub.db-wal")
                        if (oldWal.exists()) oldWal.renameTo(context.getDatabasePath("thedesitadka.db-wal"))
                        val oldShm = context.getDatabasePath("streamhub.db-shm")
                        if (oldShm.exists()) oldShm.renameTo(context.getDatabasePath("thedesitadka.db-shm"))
                    }
                } catch (e: Exception) {
                    // fallback to fresh database creation
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "thedesitadka.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
