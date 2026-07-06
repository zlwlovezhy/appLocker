package com.applocker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.applocker.data.dao.LockedAppDao
import com.applocker.data.entity.LockedApp

@Database(entities = [LockedApp::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun lockedAppDao(): LockedAppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_locker_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
