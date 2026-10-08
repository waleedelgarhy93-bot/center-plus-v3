package com.example.data.repository

import com.example.data.dao.AccessCodeDao
import com.example.data.dao.QuizDao
import com.example.data.dao.StudentSubjectAccessDao
import com.example.data.dao.VideoDao
import com.example.data.model.AccessCode
import com.example.data.model.AccessCodeWithStudent
import com.example.data.model.Quiz
import com.example.data.model.QuizWithVideoTitle
import com.example.data.model.StudentSubjectAccess
import com.example.data.model.Video
import com.example.data.model.VideoWithQuizCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface CodeVerificationResult {
    data class Success(val message: String) : CodeVerificationResult
    data class InvalidCode(val message: String) : CodeVerificationResult
    data class AlreadyUsed(val message: String) : CodeVerificationResult
    data class WrongSubject(val message: String) : CodeVerificationResult
}

class ContentRepository(
    private val videoDao: VideoDao,
    private val quizDao: QuizDao,
    private val accessCodeDao: AccessCodeDao,
    private val studentSubjectAccessDao: StudentSubjectAccessDao
) {

    // VIDEOS
    fun getVideosWithQuizCount(subjectId: Long): Flow<List<VideoWithQuizCount>> =
        videoDao.getVideosWithQuizCountBySubject(subjectId)

    fun getVideosBySubject(subjectId: Long): Flow<List<Video>> =
        videoDao.getVideosBySubject(subjectId)

    suspend fun insertVideo(video: Video): Long = withContext(Dispatchers.IO) {
        val nextOrder = if (video.orderIndex <= 0) {
            (videoDao.getMaxOrderIndex(video.subjectId) ?: 0) + 1
        } else {
            video.orderIndex
        }
        videoDao.insertVideo(video.copy(orderIndex = nextOrder))
    }

    suspend fun updateVideo(video: Video) = withContext(Dispatchers.IO) {
        videoDao.updateVideo(video)
    }

    suspend fun deleteVideo(video: Video) = withContext(Dispatchers.IO) {
        videoDao.deleteVideo(video)
    }

    suspend fun swapVideoOrders(video1: Video, video2: Video) = withContext(Dispatchers.IO) {
        val order1 = video1.orderIndex
        val order2 = video2.orderIndex
        videoDao.updateVideo(video1.copy(orderIndex = order2))
        videoDao.updateVideo(video2.copy(orderIndex = order1))
    }

    // QUIZZES
    fun getQuizzesWithVideoTitle(subjectId: Long): Flow<List<QuizWithVideoTitle>> =
        quizDao.getQuizzesWithVideoTitle(subjectId)

    suspend fun insertQuiz(quiz: Quiz): Long = withContext(Dispatchers.IO) {
        quizDao.insertQuiz(quiz)
    }

    suspend fun updateQuiz(quiz: Quiz) = withContext(Dispatchers.IO) {
        quizDao.updateQuiz(quiz)
    }

    suspend fun deleteQuiz(quiz: Quiz) = withContext(Dispatchers.IO) {
        quizDao.deleteQuiz(quiz)
    }

    // ACCESS CODES
    fun getCodesWithStudent(subjectId: Long): Flow<List<AccessCodeWithStudent>> =
        accessCodeDao.getCodesWithStudentBySubject(subjectId)

    suspend fun generateCode(
        subjectId: Long,
        subjectName: String,
        cardNumber: String,
        isOnline: Boolean = false
    ): AccessCode = withContext(Dispatchers.IO) {
        val prefix = when {
            subjectName.contains("رياض") -> "RWD"
            subjectName.contains("فيز") -> "PHZ"
            subjectName.contains("كيم") -> "CHM"
            subjectName.contains("عرب") -> "ARB"
            subjectName.contains("إنج") || subjectName.contains("انج") -> "ENG"
            else -> subjectName.take(3).uppercase(Locale.ENGLISH)
        }
        val cleanCard = cardNumber.trim()
        val codeString = if (isOnline) "ONL-$prefix-$cleanCard" else "$prefix-$cleanCard"

        val accessCode = AccessCode(
            code = codeString,
            cardNumber = cleanCard,
            subjectId = subjectId,
            usedByStudentId = null,
            usedAt = null,
            isOnline = isOnline
        )
        val id = accessCodeDao.insertCode(accessCode)
        accessCode.copy(id = id)
    }

    suspend fun deleteCode(code: AccessCode) = withContext(Dispatchers.IO) {
        accessCodeDao.deleteCode(code)
    }

    // STUDENT UNLOCKING
    fun getUnlockedSubjectIds(studentId: Long): Flow<List<Long>> =
        studentSubjectAccessDao.getUnlockedSubjectIdsForStudent(studentId)

    suspend fun isSubjectUnlocked(studentId: Long, subjectId: Long): Boolean = withContext(Dispatchers.IO) {
        studentSubjectAccessDao.isSubjectUnlockedForStudent(studentId, subjectId)
    }

    suspend fun verifyAndApplyCode(
        rawCode: String,
        studentId: Long,
        targetSubjectId: Long? = null
    ): CodeVerificationResult = withContext(Dispatchers.IO) {
        val trimmedCode = rawCode.trim().uppercase(Locale.ENGLISH)
        val codeObj = accessCodeDao.getCodeByString(trimmedCode)
            ?: return@withContext CodeVerificationResult.InvalidCode("الكود غير صحيح، تأكد من كتابته بشكل سليم")

        if (codeObj.usedByStudentId != null && codeObj.usedByStudentId != studentId) {
            return@withContext CodeVerificationResult.AlreadyUsed("هذا الكود مستخدم بالفعل من طالب آخر")
        }

        if (targetSubjectId != null && targetSubjectId > 0 && codeObj.subjectId != targetSubjectId) {
            return@withContext CodeVerificationResult.WrongSubject("هذا الكود مخصص لمادة دراسية أخرى")
        }

        val subjectToUnlock = codeObj.subjectId

        // Grant access
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(Date())
        studentSubjectAccessDao.grantAccess(
            StudentSubjectAccess(
                studentId = studentId,
                subjectId = subjectToUnlock,
                unlockedAt = now
            )
        )

        // Mark code as used
        accessCodeDao.updateCode(
            codeObj.copy(
                usedByStudentId = studentId,
                usedAt = now
            )
        )

        CodeVerificationResult.Success("تم تفعيل كود الاشتراك بنجاح وفتح محتوى المادة!")
    }
}
