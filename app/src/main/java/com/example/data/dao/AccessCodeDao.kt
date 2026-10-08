package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AccessCode
import com.example.data.model.AccessCodeWithStudent
import kotlinx.coroutines.flow.Flow

@Dao
interface AccessCodeDao {

    @Query(
        """
        SELECT 
            c.id AS id,
            c.code AS code,
            COALESCE(c.cardNumber, '') AS cardNumber,
            c.subjectId AS subjectId,
            c.usedByStudentId AS usedByStudentId,
            c.usedAt AS usedAt,
            c.isOnline AS isOnline,
            s.name AS studentName
        FROM access_codes c
        LEFT JOIN students s ON c.usedByStudentId = s.id
        WHERE c.subjectId = :subjectId
        ORDER BY c.id DESC
        """
    )
    fun getCodesWithStudentBySubject(subjectId: Long): Flow<List<AccessCodeWithStudent>>

    @Query("SELECT * FROM access_codes WHERE isOnline = 1 ORDER BY id DESC")
    fun getAllOnlineCodes(): Flow<List<AccessCode>>

    @Query("SELECT * FROM access_codes WHERE isOnline = 1 AND subjectId = :subjectId ORDER BY id DESC")
    fun getOnlineCodesBySubject(subjectId: Long): Flow<List<AccessCode>>

    @Query("SELECT * FROM access_codes WHERE UPPER(TRIM(code)) = UPPER(TRIM(:code)) LIMIT 1")
    suspend fun getCodeByString(code: String): AccessCode?

    @Query("SELECT * FROM access_codes WHERE id = :id LIMIT 1")
    suspend fun getCodeById(id: Long): AccessCode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCode(code: AccessCode): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCodes(codes: List<AccessCode>)

    @Update
    suspend fun updateCode(code: AccessCode)

    @Delete
    suspend fun deleteCode(code: AccessCode)

    @Query("DELETE FROM access_codes WHERE id = :id")
    suspend fun deleteCodeById(id: Long)

    @Query("SELECT COUNT(*) FROM access_codes")
    suspend fun getCount(): Int
}
