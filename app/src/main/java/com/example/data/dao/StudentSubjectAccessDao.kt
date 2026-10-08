package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.StudentSubjectAccess
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentSubjectAccessDao {

    @Query("SELECT COUNT(*) > 0 FROM student_subject_access WHERE studentId = :studentId AND subjectId = :subjectId")
    suspend fun isSubjectUnlockedForStudent(studentId: Long, subjectId: Long): Boolean

    @Query("SELECT subjectId FROM student_subject_access WHERE studentId = :studentId")
    fun getUnlockedSubjectIdsForStudent(studentId: Long): Flow<List<Long>>

    @Query("SELECT subjectId FROM student_subject_access WHERE studentId = :studentId")
    suspend fun getUnlockedSubjectIds(studentId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun grantAccess(access: StudentSubjectAccess): Long

    @Query("DELETE FROM student_subject_access WHERE studentId = :studentId AND subjectId = :subjectId")
    suspend fun revokeAccess(studentId: Long, subjectId: Long)
}
