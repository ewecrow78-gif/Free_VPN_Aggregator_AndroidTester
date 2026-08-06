package com.ghostvpn.tester.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ghostvpn.tester.data.model.TestResult
import kotlinx.coroutines.flow.Flow

@Dao
interface ResultDao {
    @Insert
    suspend fun insert(result: TestResult): Long

    @Update
    suspend fun update(result: TestResult)

    @Query("SELECT * FROM test_results ORDER BY startTime DESC")
    fun getAllResults(): Flow<List<TestResult>>

    @Query("SELECT * FROM test_results WHERE isUploaded = 0")
    suspend fun getUnuploadedResults(): List<TestResult>

    @Query("UPDATE test_results SET isUploaded = 1 WHERE id IN (:ids)")
    suspend fun markAsUploaded(ids: List<Long>)
    
    @Query("DELETE FROM test_results WHERE isUploaded = 1")
    suspend fun deleteUploaded()
}
