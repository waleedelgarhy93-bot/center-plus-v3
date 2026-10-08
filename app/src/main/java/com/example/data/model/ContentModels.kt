package com.example.data.model

data class VideoWithQuizCount(
    val id: Long,
    val subjectId: Long,
    val title: String,
    val url: String,
    val orderIndex: Int,
    val isPaid: Boolean,
    val description: String,
    val quizCount: Int = 0
) {
    fun toVideo(): Video = Video(
        id = id,
        subjectId = subjectId,
        title = title,
        url = url,
        orderIndex = orderIndex,
        isPaid = isPaid,
        description = description
    )
}

data class QuizWithVideoTitle(
    val id: Long,
    val subjectId: Long,
    val videoId: Long?,
    val title: String,
    val url: String,
    val isPaid: Boolean,
    val date: String,
    val description: String,
    val videoTitle: String? = null
) {
    fun toQuiz(): Quiz = Quiz(
        id = id,
        subjectId = subjectId,
        videoId = videoId,
        title = title,
        url = url,
        isPaid = isPaid,
        date = date,
        description = description
    )
}

data class AccessCodeWithStudent(
    val id: Long,
    val code: String,
    val cardNumber: String,
    val subjectId: Long,
    val usedByStudentId: Long?,
    val usedAt: String?,
    val isOnline: Boolean = false,
    val studentName: String? = null
)
