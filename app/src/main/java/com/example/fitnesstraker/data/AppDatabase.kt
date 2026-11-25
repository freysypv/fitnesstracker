package com.example.fitnesstraker.data




import android.content.Context
import android.graphics.Color
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase: RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    companion object {

        // guarantees every thread sees th lastest value of INSTANCE immediately
        // not a stale  cached copy
        @Volatile
        private var INSTANCE:  AppDatabase? = null
        // the only way anything in
        fun getInstance(context: Context): AppDatabase {

            return INSTANCE ?: synchronized(this) {

                Room.databaseBuilder(
                    context.applicationContext,     // outlives any single activity/screen
                    AppDatabase::class.java, //
                    "fitness_traker_db" //
                ).build().also { INSTANCE = it } //
            }

        }
    }
}