package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Subject
import com.example.data.model.SubjectWithGroupCount
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {

    @Query("SELECT * FROM subjects ORDER BY id ASC")
    fun getAllSubjects(): Flow<List<Subject>>

    @Query(
        """
        SELECT 
            s.id AS id,
            s.name AS name,
            s.description AS description,
            s.icon AS icon,
            s.color AS color,
            COUNT(g.id) AS groupCount
        FROM subjects s
        LEFT JOIN `groups` g ON s.id = g.subjectId
        GROUP BY s.id
        ORDER BY s.id ASC
        """
    )
    fun getAllSubjectsWithGroupCount(): Flow<List<SubjectWithGroupCount>>

    @Query("SELECT * FROM subjects WHERE id = :id LIMIT 1")
    suspend fun getSubjectById(id: Long): Subject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: Subject): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubjects(subjects: List<Subject>)

    @Update
    suspend fun updateSubject(subject: Subject)

    @Delete
    suspend fun deleteSubject(subject: Subject)

    @Query("DELETE FROM subjects WHERE id = :id")
    suspend fun deleteSubjectById(id: Long)

    @Query("SELECT COUNT(*) FROM subjects")
    suspend fun getCount(): Int
}
