package com.ghostvpn.tester.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ghostvpn.tester.data.model.TestResult

@Database(entities = [TestResult::class], version = 1, exportSchema = false)
abstract class ResultDatabase : RoomDatabase() {
    abstract fun resultDao(): ResultDao

    companion object {
        @Volatile
        private var INSTANCE: ResultDatabase? = null

        fun getDatabase(context: Context): ResultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ResultDatabase::class.java,
                    "tester_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
