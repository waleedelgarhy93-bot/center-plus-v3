package com.example.ui.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.model.GroupWithSubject
import com.example.data.model.QuizWithVideoTitle
import com.example.data.model.Student
import com.example.data.model.SubjectWithGroupCount
import com.example.data.model.VideoWithQuizCount

sealed interface Screen {
    data object Splash : Screen
    data object Setup : Screen
    data object Login : Screen
    data object RoleSelection : Screen
    data object TeacherDashboard : Screen
    data object Settings : Screen
    data object Scanner : Screen
    data object StudentsList : Screen
    data class AddEditStudent(val student: Student? = null) : Screen
    data class CreateParentAccount(val student: Student) : Screen
    data object SubjectsList : Screen
    data class AddEditSubject(val subject: SubjectWithGroupCount? = null) : Screen
    data object GroupsList : Screen
    data class AddEditGroup(val group: GroupWithSubject? = null) : Screen

    // Teacher Content Management
    data object ContentSubjectsList : Screen
    data class TeacherSubjectDetail(val subject: SubjectWithGroupCount) : Screen
    data class AddEditVideo(val subjectId: Long, val video: VideoWithQuizCount? = null) : Screen
    data class AddEditQuiz(val subjectId: Long, val quiz: QuizWithVideoTitle? = null) : Screen
    data class GenerateCode(val subjectId: Long, val subjectName: String) : Screen

    // Student & Parent Views
    data object Signup : Screen
    data class StudentHome(val studentName: String) : Screen
    data class StudentOnlineHome(val studentName: String) : Screen
    data object SubscriptionCode : Screen
    data class ParentHome(val parentName: String, val studentName: String) : Screen
    data object StudentPortal : Screen
    data class StudentSubjectDetail(val subject: SubjectWithGroupCount) : Screen
    data class StudentEnterCode(val subject: SubjectWithGroupCount) : Screen
    data class InAppViewer(val url: String, val title: String) : Screen

    data class Placeholder(
        val title: String,
        val subtitle: String,
        val accentColor: Color,
        val icon: ImageVector,
        val returnToTeacher: Boolean = true
    ) : Screen
}
