package com.example.data.repository

import com.example.data.dao.PaymentDao
import com.example.data.model.Payment
import com.example.data.model.PaymentWithSubject
import kotlinx.coroutines.flow.Flow

class PaymentRepository(private val paymentDao: PaymentDao) {

    fun getPaymentsWithSubjectForStudent(studentId: Long): Flow<List<PaymentWithSubject>> {
        return paymentDao.getPaymentsWithSubjectForStudent(studentId)
    }

    suspend fun getPaymentsWithSubjectForStudentSync(studentId: Long): List<PaymentWithSubject> {
        return paymentDao.getPaymentsWithSubjectForStudentSync(studentId)
    }

    suspend fun insertPayment(payment: Payment): Long {
        return paymentDao.insertPayment(payment)
    }

    suspend fun insertPayments(payments: List<Payment>): List<Long> {
        return paymentDao.insertPayments(payments)
    }

    suspend fun getCount(): Int {
        return paymentDao.getCount()
    }
}
