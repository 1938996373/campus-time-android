package com.swish.campustime.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM semesters WHERE id = 1") fun observeSemester(): Flow<SemesterConfig?>
    @Query("SELECT * FROM semesters WHERE id = 1") suspend fun semester(): SemesterConfig?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSemester(value: SemesterConfig)

    @Query("SELECT * FROM courses ORDER BY dayOfWeek, startMinute") fun observeCourses(): Flow<List<Course>>
    @Query("SELECT * FROM courses ORDER BY dayOfWeek, startMinute") suspend fun courses(): List<Course>
    @Insert suspend fun insertCourse(value: Course): Long
    @Update suspend fun updateCourse(value: Course)
    @Delete suspend fun deleteCourse(value: Course)
    @Query("DELETE FROM courses") suspend fun clearCourses()

    @Query("SELECT * FROM schedule_events ORDER BY startEpochMillis") fun observeEvents(): Flow<List<ScheduleEvent>>
    @Query("SELECT * FROM schedule_events ORDER BY startEpochMillis") suspend fun events(): List<ScheduleEvent>
    @Insert suspend fun insertEvent(value: ScheduleEvent): Long
    @Update suspend fun updateEvent(value: ScheduleEvent)
    @Delete suspend fun deleteEvent(value: ScheduleEvent)
    @Query("DELETE FROM schedule_events") suspend fun clearEvents()

    @Query("SELECT * FROM tasks ORDER BY completed, dueEpochMillis") fun observeTasks(): Flow<List<Task>>
    @Query("SELECT * FROM tasks ORDER BY completed, dueEpochMillis") suspend fun tasks(): List<Task>
    @Insert suspend fun insertTask(value: Task): Long
    @Update suspend fun updateTask(value: Task)
    @Delete suspend fun deleteTask(value: Task)
    @Query("DELETE FROM tasks") suspend fun clearTasks()

    @Query("SELECT * FROM task_steps ORDER BY id") fun observeSteps(): Flow<List<TaskStep>>
    @Query("SELECT * FROM task_steps ORDER BY id") suspend fun steps(): List<TaskStep>
    @Insert suspend fun insertStep(value: TaskStep): Long
    @Update suspend fun updateStep(value: TaskStep)
    @Delete suspend fun deleteStep(value: TaskStep)
    @Query("DELETE FROM task_steps WHERE taskId = :taskId") suspend fun clearStepsForTask(taskId: Long)
    @Query("DELETE FROM task_steps") suspend fun clearSteps()

    @Query("SELECT * FROM task_blocks ORDER BY startEpochMillis") fun observeBlocks(): Flow<List<TaskBlock>>
    @Query("SELECT * FROM task_blocks ORDER BY startEpochMillis") suspend fun blocks(): List<TaskBlock>
    @Query("SELECT * FROM task_blocks WHERE taskId = :taskId AND occurrenceDueEpochMillis = :due") suspend fun blocksForOccurrence(taskId: Long, due: Long): List<TaskBlock>
    @Insert suspend fun insertBlock(value: TaskBlock): Long
    @Update suspend fun updateBlock(value: TaskBlock)
    @Delete suspend fun deleteBlock(value: TaskBlock)
    @Query("DELETE FROM task_blocks WHERE taskId = :taskId") suspend fun clearBlocksForTask(taskId: Long)
    @Query("DELETE FROM task_blocks WHERE taskId = :taskId AND occurrenceDueEpochMillis = :due") suspend fun clearBlocksForOccurrence(taskId: Long, due: Long)
    @Query("DELETE FROM task_blocks") suspend fun clearBlocks()
}
