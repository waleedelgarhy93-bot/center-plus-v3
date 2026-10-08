package com.example.ui.screens.content

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.AccessCodeWithStudent
import com.example.data.model.Quiz
import com.example.data.model.QuizWithVideoTitle
import com.example.data.model.SubjectWithGroupCount
import com.example.data.model.Video
import com.example.data.model.VideoWithQuizCount
import com.example.data.repository.ContentRepository
import com.example.data.repository.SubjectRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ContentViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val subjectRepository = SubjectRepository(database.subjectDao())
    private val contentRepository = ContentRepository(
        videoDao = database.videoDao(),
        quizDao = database.quizDao(),
        accessCodeDao = database.accessCodeDao(),
        studentSubjectAccessDao = database.studentSubjectAccessDao()
    )

    val subjects: StateFlow<List<SubjectWithGroupCount>> = subjectRepository.getAllSubjectsWithGroupCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedSubjectId = MutableStateFlow<Long?>(null)
    val selectedSubjectId: StateFlow<Long?> = _selectedSubjectId.asStateFlow()

    fun selectSubject(subjectId: Long) {
        _selectedSubjectId.value = subjectId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentVideos: StateFlow<List<VideoWithQuizCount>> = _selectedSubjectId
        .flatMapLatest { id ->
            if (id != null) contentRepository.getVideosWithQuizCount(id)
            else flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentQuizzes: StateFlow<List<QuizWithVideoTitle>> = _selectedSubjectId
        .flatMapLatest { id ->
            if (id != null) contentRepository.getQuizzesWithVideoTitle(id)
            else flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentCodes: StateFlow<List<AccessCodeWithStudent>> = _selectedSubjectId
        .flatMapLatest { id ->
            if (id != null) contentRepository.getCodesWithStudent(id)
            else flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // VIDEO ACTIONS
    fun saveVideo(
        id: Long,
        subjectId: Long,
        title: String,
        url: String,
        orderIndex: Int,
        isPaid: Boolean,
        description: String,
        onSuccess: () -> Unit
    ) {
        val trimmedTitle = title.trim()
        val trimmedUrl = url.trim()

        if (trimmedTitle.isEmpty()) {
            Toast.makeText(getApplication(), "يرجى كتابة عنوان الفيديو", Toast.LENGTH_SHORT).show()
            return
        }
        if (trimmedUrl.isEmpty()) {
            Toast.makeText(getApplication(), "يرجى وضع رابط الفيديو (Google Drive)", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            val video = Video(
                id = id,
                subjectId = subjectId,
                title = trimmedTitle,
                url = trimmedUrl,
                orderIndex = orderIndex,
                isPaid = isPaid,
                description = description.trim()
            )
            if (id == 0L) {
                contentRepository.insertVideo(video)
                Toast.makeText(getApplication(), "تم إضافة الفيديو بنجاح", Toast.LENGTH_SHORT).show()
            } else {
                contentRepository.updateVideo(video)
                Toast.makeText(getApplication(), "تم تعديل الفيديو بنجاح", Toast.LENGTH_SHORT).show()
            }
            onSuccess()
        }
    }

    fun deleteVideo(video: Video) {
        viewModelScope.launch {
            contentRepository.deleteVideo(video)
            Toast.makeText(getApplication(), "تم حذف الفيديو", Toast.LENGTH_SHORT).show()
        }
    }

    fun moveVideoUp(index: Int) {
        val list = currentVideos.value
        if (index > 0 && index < list.size) {
            val current = list[index].toVideo()
            val previous = list[index - 1].toVideo()
            viewModelScope.launch {
                contentRepository.swapVideoOrders(current, previous)
            }
        }
    }

    fun moveVideoDown(index: Int) {
        val list = currentVideos.value
        if (index >= 0 && index < list.size - 1) {
            val current = list[index].toVideo()
            val next = list[index + 1].toVideo()
            viewModelScope.launch {
                contentRepository.swapVideoOrders(current, next)
            }
        }
    }

    // QUIZ ACTIONS
    fun saveQuiz(
        id: Long,
        subjectId: Long,
        videoId: Long?,
        title: String,
        url: String,
        isPaid: Boolean,
        date: String,
        description: String,
        onSuccess: () -> Unit
    ) {
        val trimmedTitle = title.trim()
        val trimmedUrl = url.trim()

        if (trimmedTitle.isEmpty()) {
            Toast.makeText(getApplication(), "يرجى كتابة عنوان الاختبار", Toast.LENGTH_SHORT).show()
            return
        }
        if (trimmedUrl.isEmpty()) {
            Toast.makeText(getApplication(), "يرجى وضع رابط الاختبار (Google Form)", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            val quiz = Quiz(
                id = id,
                subjectId = subjectId,
                videoId = videoId,
                title = trimmedTitle,
                url = trimmedUrl,
                isPaid = isPaid,
                date = date.trim(),
                description = description.trim()
            )
            if (id == 0L) {
                contentRepository.insertQuiz(quiz)
                Toast.makeText(getApplication(), "تم إضافة الاختبار بنجاح", Toast.LENGTH_SHORT).show()
            } else {
                contentRepository.updateQuiz(quiz)
                Toast.makeText(getApplication(), "تم تعديل الاختبار بنجاح", Toast.LENGTH_SHORT).show()
            }
            onSuccess()
        }
    }

    fun deleteQuiz(quiz: Quiz) {
        viewModelScope.launch {
            contentRepository.deleteQuiz(quiz)
            Toast.makeText(getApplication(), "تم حذف الاختبار", Toast.LENGTH_SHORT).show()
        }
    }

    // CODE ACTIONS
    fun generateCode(
        subjectId: Long,
        subjectName: String,
        cardNumber: String,
        isOnline: Boolean = false,
        onSuccess: () -> Unit
    ) {
        val trimmedCard = cardNumber.trim()
        if (trimmedCard.isEmpty()) {
            Toast.makeText(getApplication(), "يرجى إدخال رقم كود / كارت الاشتراك", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            try {
                val code = contentRepository.generateCode(subjectId, subjectName, trimmedCard, isOnline)
                Toast.makeText(getApplication(), "تم توليد الكود: ${code.code}", Toast.LENGTH_SHORT).show()
                onSuccess()
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "خطأ: الكود موجود بالفعل!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteCode(code: AccessCodeWithStudent) {
        viewModelScope.launch {
            val entity = database.accessCodeDao().getCodeById(code.id)
            if (entity != null) {
                contentRepository.deleteCode(entity)
                Toast.makeText(getApplication(), "تم حذف الكود", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
