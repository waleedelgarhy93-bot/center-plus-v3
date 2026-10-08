package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ExamGrade
import com.example.data.model.StudentExamGradeItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamGradeDao {

    @Query("SELECT * FROM exam_grades WHERE examId = :examId")
    fun getGradesByExamId(examId: Long): Flow<List<ExamGrade>>

    @Query("SELECT * FROM exam_grades WHERE examId = :examId AND studentId = :studentId LIMIT 1")
    suspend fun getGradeByExamAndStudent(examId: Long, studentId: Long): ExamGrade?

    @Query(
        """
        SELECT 
            st.id AS studentId,
            st.name AS studentName,
            st.groupName AS groupName,
            :examId AS examId,
            eg.grade AS grade,
            eg.id AS gradeId
        FROM students st
        LEFT JOIN exam_grades eg ON eg.studentId = st.id AND eg.examId = :examId
        WHERE (
            st.groupName IN (
                SELECT (g.grade || ' - ' || g.center) FROM `groups` g WHERE g.subjectId = :subjectId
            )
            OR EXISTS (
                SELECT 1 FROM `groups` g WHERE g.subjectId = :subjectId AND (st.groupName LIKE '%' || g.center || '%' OR st.groupName LIKE '%' || g.grade || '%')
            )
            OR (SELECT COUNT(*) FROM `groups` WHERE subjectId = :subjectId) = 0
        )
        ORDER BY st.name ASC
        """
    )
    fun getStudentsWithGradesForExamAndSubject(examId: Long, subjectId: Long): Flow<List<StudentExamGradeItem>>

    @Query(
        """
        SELECT 
            st.id AS studentId,
            st.name AS studentName,
            st.groupName AS groupName,
            :examId AS examId,
            eg.grade AS grade,
            eg.id AS gradeId
        FROM students st
        LEFT JOIN exam_grades eg ON eg.studentId = st.id AND eg.examId = :examId
        ORDER BY st.name ASC
        """
    )
    fun getAllStudentsWithGradesForExam(examId: Long): Flow<List<StudentExamGradeItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrade(examGrade: ExamGrade): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrades(examGrades: List<ExamGrade>)

    @Update
    suspend fun updateGrade(examGrade: ExamGrade)

    @Delete
    suspend fun deleteGrade(examGrade: ExamGrade)

    @Query("DELETE FROM exam_grades WHERE id = :id")
    suspend fun deleteGradeById(id: Long)

    @Query("DELETE FROM exam_grades WHERE examId = :examId AND studentId = :studentId")
    suspend fun deleteGradeByExamAndStudent(examId: Long, studentId: Long)

    @Query("SELECT COUNT(*) FROM exam_grades WHERE examId = :examId")
    suspend fun getGradesCountByExam(examId: Long): Int

    @Query("SELECT COUNT(*) FROM exam_grades")
    suspend fun getCount(): Int
}
