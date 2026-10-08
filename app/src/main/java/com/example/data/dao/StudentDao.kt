package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Student
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE barcode = :barcode LIMIT 1")
    suspend fun getStudentByBarcode(barcode: String): Student?

    @Query("SELECT * FROM students WHERE cardNumber = :cardNumber LIMIT 1")
    suspend fun getStudentByCardNumber(cardNumber: String): Student?

    @Query("SELECT * FROM students WHERE cardNumber = :code OR barcode = :code LIMIT 1")
    suspend fun getStudentByCardOrBarcode(code: String): Student?

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    suspend fun getStudentById(id: Long): Student?

    @Query("SELECT * FROM students ORDER BY name ASC")
    fun getAllStudents(): Flow<List<Student>>

    @Query(
        """
        SELECT * FROM students 
        WHERE name LIKE '%' || :query || '%' 
           OR barcode LIKE '%' || :query || '%' 
           OR groupName LIKE '%' || :query || '%' 
        ORDER BY name ASC
        """
    )
    fun searchStudents(query: String): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE barcode = :barcode AND id != :excludeId LIMIT 1")
    suspend fun getOtherStudentWithBarcode(barcode: String, excludeId: Long): Student?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: Student): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStudents(students: List<Student>)

    @Update
    suspend fun updateStudent(student: Student)

    @Delete
    suspend fun deleteStudent(student: Student)

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun deleteStudentById(id: Long)

    @Query("SELECT COUNT(*) FROM students")
    suspend fun getCount(): Int
}
