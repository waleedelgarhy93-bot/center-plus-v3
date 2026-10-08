package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Video
import com.example.data.model.VideoWithQuizCount
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {

    @Query("SELECT * FROM videos WHERE subjectId = :subjectId ORDER BY orderIndex ASC, id ASC")
    fun getVideosBySubject(subjectId: Long): Flow<List<Video>>

    @Query(
        """
        SELECT 
            v.id AS id,
            v.subjectId AS subjectId,
            v.title AS title,
            v.url AS url,
            v.orderIndex AS orderIndex,
            v.isPaid AS isPaid,
            v.description AS description,
            COUNT(q.id) AS quizCount
        FROM videos v
        LEFT JOIN quizzes q ON v.id = q.videoId
        WHERE v.subjectId = :subjectId
        GROUP BY v.id
        ORDER BY v.orderIndex ASC, v.id ASC
        """
    )
    fun getVideosWithQuizCountBySubject(subjectId: Long): Flow<List<VideoWithQuizCount>>

    @Query("SELECT * FROM videos WHERE id = :id LIMIT 1")
    suspend fun getVideoById(id: Long): Video?

    @Query("SELECT MAX(orderIndex) FROM videos WHERE subjectId = :subjectId")
    suspend fun getMaxOrderIndex(subjectId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: Video): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVideos(videos: List<Video>)

    @Update
    suspend fun updateVideo(video: Video)

    @Delete
    suspend fun deleteVideo(video: Video)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteVideoById(id: Long)

    @Query("SELECT COUNT(*) FROM videos")
    suspend fun getCount(): Int
}
