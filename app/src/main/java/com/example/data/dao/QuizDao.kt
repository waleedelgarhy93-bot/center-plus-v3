package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Quiz
import com.example.data.model.QuizWithVideoTitle
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizDao {

    @Query("SELECT * FROM quizzes WHERE subjectId = :subjectId ORDER BY id ASC")
    fun getQuizzesBySubject(subjectId: Long): Flow<List<Quiz>>

    @Query(
        """
        SELECT 
            q.id AS id,
            q.subjectId AS subjectId,
            q.videoId AS videoId,
            q.title AS title,
            q.url AS url,
            q.isPaid AS isPaid,
            q.date AS date,
            q.description AS description,
            v.title AS videoTitle
        FROM quizzes q
        LEFT JOIN videos v ON q.videoId = v.id
        WHERE q.subjectId = :subjectId
        ORDER BY q.id ASC
        """
    )
    fun getQuizzesWithVideoTitle(subjectId: Long): Flow<List<QuizWithVideoTitle>>

    @Query("SELECT * FROM quizzes WHERE id = :id LIMIT 1")
    suspend fun getQuizById(id: Long): Quiz?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuiz(quiz: Quiz): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQuizzes(quizzes: List<Quiz>)

    @Update
    suspend fun updateQuiz(quiz: Quiz)

    @Delete
    suspend fun deleteQuiz(quiz: Quiz)

    @Query("DELETE FROM quizzes WHERE id = :id")
    suspend fun deleteQuizById(id: Long)

    @Query("SELECT COUNT(*) FROM quizzes")
    suspend fun getCount(): Int
}
