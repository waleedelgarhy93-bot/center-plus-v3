package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.Payment
import com.example.data.model.PaymentWithSubject
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<Payment>): List<Long>

    @Query("SELECT * FROM payments WHERE studentId = :studentId ORDER BY month DESC, id DESC")
    fun getPaymentsForStudent(studentId: Long): Flow<List<Payment>>

    @Query(
        """
        SELECT 
            p.id AS id,
            p.studentId AS studentId,
            p.subjectId AS subjectId,
            s.name AS subjectName,
            s.color AS subjectColor,
            p.amount AS amount,
            p.month AS month,
            p.status AS status,
            p.paidAt AS paidAt,
            p.createdAt AS createdAt
        FROM payments p
        INNER JOIN subjects s ON p.subjectId = s.id
        WHERE p.studentId = :studentId
        ORDER BY p.month DESC, p.id DESC
        """
    )
    fun getPaymentsWithSubjectForStudent(studentId: Long): Flow<List<PaymentWithSubject>>

    @Query(
        """
        SELECT 
            p.id AS id,
            p.studentId AS studentId,
            p.subjectId AS subjectId,
            s.name AS subjectName,
            s.color AS subjectColor,
            p.amount AS amount,
            p.month AS month,
            p.status AS status,
            p.paidAt AS paidAt,
            p.createdAt AS createdAt
        FROM payments p
        INNER JOIN subjects s ON p.subjectId = s.id
        WHERE p.studentId = :studentId
        ORDER BY p.month DESC, p.id DESC
        """
    )
    suspend fun getPaymentsWithSubjectForStudentSync(studentId: Long): List<PaymentWithSubject>

    @Query("SELECT COUNT(*) FROM payments")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM payments WHERE studentId = :studentId")
    suspend fun getCountForStudent(studentId: Long): Int
}
