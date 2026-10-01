package com.swish.campustime.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SemesterConfig::class, Course::class, ScheduleEvent::class, Task::class, TaskStep::class, TaskBlock::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "campus_time.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "UPDATE semesters SET periodTimes = ? WHERE periodTimes = ?",
                    arrayOf(ScheduleRepository.DEFAULT_PERIODS, ScheduleRepository.LEGACY_DEFAULT_PERIODS)
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE semesters ADD COLUMN dayStartMinute INTEGER NOT NULL DEFAULT 480")
                db.execSQL("ALTER TABLE semesters ADD COLUMN dayEndMinute INTEGER NOT NULL DEFAULT 1320")
                db.execSQL("ALTER TABLE semesters ADD COLUMN breakStartMinute INTEGER NOT NULL DEFAULT 720")
                db.execSQL("ALTER TABLE semesters ADD COLUMN breakEndMinute INTEGER NOT NULL DEFAULT 780")
                db.execSQL("ALTER TABLE semesters ADD COLUMN bufferMinutes INTEGER NOT NULL DEFAULT 10")
                db.execSQL("ALTER TABLE courses ADD COLUMN startPeriod INTEGER")
                db.execSQL("ALTER TABLE courses ADD COLUMN endPeriod INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN hasDueDate INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE tasks ADD COLUMN plannedEpochDay INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN estimateMinutes INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN category TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN completedEpochDaysCsv TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS task_steps (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId INTEGER NOT NULL, title TEXT NOT NULL, completed INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS task_blocks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId INTEGER NOT NULL, startEpochMillis INTEGER NOT NULL, endEpochMillis INTEGER NOT NULL, occurrenceDueEpochMillis INTEGER)")
            }
        }
    }
}
