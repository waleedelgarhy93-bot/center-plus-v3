package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.SupervisedUserCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.data.database.AppDatabase
import com.example.data.repository.SettingsRepository
import com.example.ui.model.Screen
import com.example.ui.screens.PlaceholderScreen
import com.example.ui.screens.RoleSelectionScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TeacherDashboardScreen
import com.example.ui.screens.setup.SetupScreen
import kotlinx.coroutines.launch
import com.example.ui.screens.scanner.ScannerScreen
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.auth.LoginViewModel
import com.example.ui.screens.auth.SignupScreen
import com.example.ui.screens.auth.SignupViewModel
import com.example.ui.screens.home.ParentHomeScreen
import com.example.ui.screens.home.StudentHomeScreen
import com.example.ui.screens.home.StudentOnlineHomeScreen
import com.example.ui.screens.studentview.SubscriptionCodeScreen
import com.example.ui.screens.students.CreateParentAccountScreen
import com.example.ui.screens.students.AddEditStudentScreen
import com.example.ui.screens.students.StudentsScreen
import com.example.ui.screens.students.StudentsViewModel
import com.example.ui.screens.subjects.AddEditSubjectScreen
import com.example.ui.screens.subjects.SubjectsScreen
import com.example.ui.screens.subjects.SubjectsViewModel
import com.example.ui.screens.groups.AddEditGroupScreen
import com.example.ui.screens.groups.GroupsScreen
import com.example.ui.screens.groups.GroupsViewModel
import com.example.ui.screens.content.AddEditQuizScreen
import com.example.ui.screens.content.AddEditVideoScreen
import com.example.ui.screens.content.ContentScreen
import com.example.ui.screens.content.ContentViewModel
import com.example.ui.screens.content.GenerateCodeScreen
import com.example.ui.screens.content.SubjectContentScreen
import com.example.ui.screens.studentview.EnterCodeScreen
import com.example.ui.screens.studentview.StudentContentScreen
import com.example.ui.screens.studentview.StudentSubjectScreen
import com.example.ui.screens.studentview.StudentViewModel
import com.example.ui.screens.studentview.VideoPlayerScreen
import com.example.data.model.SubjectWithGroupCount
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.LHopitalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkTheme by remember { mutableStateOf(systemDark) }

            LHopitalTheme(darkTheme = isDarkTheme) {
                // Ensure RTL layout direction for Arabic language interface
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigation(
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = { isDarkTheme = !isDarkTheme }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Splash) }
    val loginViewModel: LoginViewModel = viewModel()
    val signupViewModel: SignupViewModel = viewModel()
    val studentsViewModel: StudentsViewModel = viewModel()
    val subjectsViewModel: SubjectsViewModel = viewModel()
    val groupsViewModel: GroupsViewModel = viewModel()
    val contentViewModel: ContentViewModel = viewModel()
    val studentViewModel: StudentViewModel = viewModel()
    var activeTeacherSubject by remember { mutableStateOf<SubjectWithGroupCount?>(null) }
    var activeStudentSubject by remember { mutableStateOf<SubjectWithGroupCount?>(null) }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState is Screen.Splash) {
                fadeIn() togetherWith fadeOut()
            } else {
                (slideInHorizontally { width -> -width } + fadeIn()) togetherWith
                        (slideOutHorizontally { width -> width } + fadeOut())
            }
        },
        label = "screen_navigation"
    ) { screen ->
        when (screen) {
            is Screen.Splash -> {
                SplashScreen(
                    onTimeout = {
                        coroutineScope.launch {
                            val db = AppDatabase.getDatabase(context)
                            val settingsRepo = SettingsRepository(db.appSettingsDao())
                            val isComplete = settingsRepo.isSetupComplete()
                            if (!isComplete) {
                                currentScreen = Screen.Setup
                            } else if (loginViewModel.sessionManager.isLoggedIn) {
                                when (loginViewModel.sessionManager.role) {
                                    "teacher" -> currentScreen = Screen.TeacherDashboard
                                    "student", "student_online", "parent" -> {
                                        // Requirement: Card scan required every time on app open
                                        currentScreen = Screen.Login
                                    }
                                    else -> currentScreen = Screen.Login
                                }
                            } else {
                                currentScreen = Screen.Login
                            }
                        }
                    }
                )
            }

            is Screen.Setup -> {
                SetupScreen(
                    onSetupCompleted = {
                        currentScreen = Screen.Login
                    }
                )
            }

            is Screen.Login -> {
                LoginScreen(
                    onLoginSuccess = { role, user ->
                        when (role) {
                            "teacher" -> currentScreen = Screen.TeacherDashboard
                            "student" -> {
                                user.studentId?.let { studentViewModel.selectStudent(it) }
                                currentScreen = Screen.StudentHome(user.fullName)
                            }
                            "student_online" -> {
                                user.studentId?.let { studentViewModel.selectStudent(it) }
                                currentScreen = Screen.StudentOnlineHome(user.fullName)
                            }
                            "parent" -> {
                                user.studentId?.let { studentViewModel.selectStudent(it) }
                                currentScreen = Screen.ParentHome(
                                    parentName = user.fullName,
                                    studentName = "الطالب المسجل"
                                )
                            }
                            else -> currentScreen = Screen.TeacherDashboard
                        }
                    },
                    onOpenSignup = {
                        currentScreen = Screen.Signup
                    },
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    viewModel = loginViewModel
                )
            }

            is Screen.RoleSelection -> {
                RoleSelectionScreen(
                    onSelectTeacher = {
                        currentScreen = Screen.TeacherDashboard
                    },
                    onSelectParent = {
                        currentScreen = Screen.Placeholder(
                            title = "بوابة أولياء الأمور",
                            subtitle = "متابعة حضور ودرجات ابنك والتقارير الشهرية",
                            accentColor = Color(0xFF0D9488),
                            icon = Icons.Default.FamilyRestroom,
                            returnToTeacher = false
                        )
                    },
                    onSelectStudent = {
                        currentScreen = Screen.StudentPortal
                    },
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme
                )
            }

            is Screen.TeacherDashboard -> {
                // Back gesture returns to role selection or login
                BackHandler {
                    currentScreen = Screen.Login
                }

                TeacherDashboardScreen(
                    onLogout = {
                        loginViewModel.logout {
                            currentScreen = Screen.Login
                        }
                    },
                    onOpenAttendanceScanner = {
                        currentScreen = Screen.Scanner
                    },
                    onOpenStudents = {
                        currentScreen = Screen.StudentsList
                    },
                    onOpenSubjects = {
                        currentScreen = Screen.SubjectsList
                    },
                    onOpenGroups = {
                        currentScreen = Screen.GroupsList
                    },
                    onOpenContent = {
                        currentScreen = Screen.ContentSubjectsList
                    },
                    onSelectItem = { title, subtitle, color, icon ->
                        currentScreen = Screen.Placeholder(
                            title = title,
                            subtitle = subtitle,
                            accentColor = color,
                            icon = icon,
                            returnToTeacher = true
                        )
                    },
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme
                )
            }

            is Screen.Scanner -> {
                ScannerScreen(
                    onBack = {
                        currentScreen = Screen.TeacherDashboard
                    }
                )
            }

            is Screen.StudentsList -> {
                StudentsScreen(
                    onBack = {
                        currentScreen = Screen.TeacherDashboard
                    },
                    onAddNewStudent = {
                        currentScreen = Screen.AddEditStudent(student = null)
                    },
                    onEditStudent = { student ->
                        currentScreen = Screen.AddEditStudent(student = student)
                    },
                    onCreateParentAccount = { student ->
                        currentScreen = Screen.CreateParentAccount(student)
                    },
                    viewModel = studentsViewModel
                )
            }

            is Screen.CreateParentAccount -> {
                CreateParentAccountScreen(
                    student = screen.student,
                    onBack = {
                        currentScreen = Screen.StudentsList
                    },
                    onAccountCreated = {
                        currentScreen = Screen.StudentsList
                    }
                )
            }

            is Screen.Signup -> {
                SignupScreen(
                    onBack = {
                        currentScreen = Screen.Login
                    },
                    onSignupCompleted = { createdUser ->
                        loginViewModel.sessionManager.saveSession(createdUser)
                        createdUser.studentId?.let { studentViewModel.selectStudent(it) }
                        currentScreen = Screen.StudentOnlineHome(createdUser.fullName)
                    },
                    viewModel = signupViewModel
                )
            }

            is Screen.StudentHome -> {
                StudentHomeScreen(
                    studentName = screen.studentName,
                    onLogout = {
                        loginViewModel.logout {
                            currentScreen = Screen.Login
                        }
                    },
                    onOpenSubjects = {
                        currentScreen = Screen.StudentPortal
                    },
                    onSelectItem = { title, subtitle, color, icon ->
                        currentScreen = Screen.Placeholder(
                            title = title,
                            subtitle = subtitle,
                            accentColor = color,
                            icon = icon,
                            returnToTeacher = false
                        )
                    },
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme
                )
            }

            is Screen.StudentOnlineHome -> {
                StudentOnlineHomeScreen(
                    studentName = screen.studentName,
                    onLogout = {
                        loginViewModel.logout {
                            currentScreen = Screen.Login
                        }
                    },
                    onOpenSubjects = {
                        currentScreen = Screen.StudentPortal
                    },
                    onEnterSubscriptionCode = {
                        currentScreen = Screen.SubscriptionCode
                    },
                    onSelectItem = { title, subtitle, color, icon ->
                        currentScreen = Screen.Placeholder(
                            title = title,
                            subtitle = subtitle,
                            accentColor = color,
                            icon = icon,
                            returnToTeacher = false
                        )
                    },
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme
                )
            }

            is Screen.SubscriptionCode -> {
                SubscriptionCodeScreen(
                    onBack = {
                        currentScreen = Screen.StudentOnlineHome(loginViewModel.sessionManager.fullName.ifBlank { "الطالب" })
                    },
                    onSuccessActivated = {
                        currentScreen = Screen.StudentPortal
                    },
                    viewModel = studentViewModel
                )
            }

            is Screen.ParentHome -> {
                ParentHomeScreen(
                    parentName = screen.parentName,
                    studentName = screen.studentName,
                    onLogout = {
                        loginViewModel.logout {
                            currentScreen = Screen.Login
                        }
                    },
                    onOpenContent = {
                        currentScreen = Screen.StudentPortal
                    },
                    onSelectItem = { title, subtitle, color, icon ->
                        currentScreen = Screen.Placeholder(
                            title = title,
                            subtitle = subtitle,
                            accentColor = color,
                            icon = icon,
                            returnToTeacher = false
                        )
                    },
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme
                )
            }

            is Screen.AddEditStudent -> {
                AddEditStudentScreen(
                    studentToEdit = screen.student,
                    onBack = {
                        currentScreen = Screen.StudentsList
                    },
                    onStudentSaved = {
                        currentScreen = Screen.StudentsList
                    },
                    viewModel = studentsViewModel
                )
            }

            is Screen.SubjectsList -> {
                SubjectsScreen(
                    onBack = {
                        currentScreen = Screen.TeacherDashboard
                    },
                    onAddNewSubject = {
                        currentScreen = Screen.AddEditSubject(subject = null)
                    },
                    onEditSubject = { subject ->
                        currentScreen = Screen.AddEditSubject(subject = subject)
                    },
                    viewModel = subjectsViewModel
                )
            }

            is Screen.AddEditSubject -> {
                AddEditSubjectScreen(
                    subjectToEdit = screen.subject,
                    onBack = {
                        currentScreen = Screen.SubjectsList
                    },
                    onSubjectSaved = {
                        currentScreen = Screen.SubjectsList
                    },
                    viewModel = subjectsViewModel
                )
            }

            is Screen.GroupsList -> {
                GroupsScreen(
                    onBack = {
                        currentScreen = Screen.TeacherDashboard
                    },
                    onAddNewGroup = {
                        currentScreen = Screen.AddEditGroup(group = null)
                    },
                    onEditGroup = { group ->
                        currentScreen = Screen.AddEditGroup(group = group)
                    },
                    viewModel = groupsViewModel
                )
            }

            is Screen.AddEditGroup -> {
                AddEditGroupScreen(
                    groupToEdit = screen.group,
                    onBack = {
                        currentScreen = Screen.GroupsList
                    },
                    onGroupSaved = {
                        currentScreen = Screen.GroupsList
                    },
                    viewModel = groupsViewModel
                )
            }

            // Teacher Content Management
            is Screen.ContentSubjectsList -> {
                ContentScreen(
                    onBack = {
                        currentScreen = Screen.TeacherDashboard
                    },
                    onSelectSubject = { subject ->
                        activeTeacherSubject = subject
                        currentScreen = Screen.TeacherSubjectDetail(subject)
                    },
                    viewModel = contentViewModel
                )
            }

            is Screen.TeacherSubjectDetail -> {
                SubjectContentScreen(
                    subject = screen.subject,
                    onBack = {
                        currentScreen = Screen.ContentSubjectsList
                    },
                    onAddNewVideo = {
                        currentScreen = Screen.AddEditVideo(screen.subject.id, null)
                    },
                    onEditVideo = { videoItem ->
                        currentScreen = Screen.AddEditVideo(screen.subject.id, videoItem)
                    },
                    onAddNewQuiz = {
                        currentScreen = Screen.AddEditQuiz(screen.subject.id, null)
                    },
                    onEditQuiz = { quizItem ->
                        currentScreen = Screen.AddEditQuiz(screen.subject.id, quizItem)
                    },
                    onGenerateCode = {
                        currentScreen = Screen.GenerateCode(screen.subject.id, screen.subject.name)
                    },
                    viewModel = contentViewModel
                )
            }

            is Screen.AddEditVideo -> {
                val currentSubj = activeTeacherSubject ?: subjectsViewModel.subjects.value.firstOrNull { it.id == screen.subjectId }
                AddEditVideoScreen(
                    subjectId = screen.subjectId,
                    videoToEdit = screen.video,
                    onBack = {
                        if (currentSubj != null) currentScreen = Screen.TeacherSubjectDetail(currentSubj)
                        else currentScreen = Screen.ContentSubjectsList
                    },
                    onVideoSaved = {
                        if (currentSubj != null) currentScreen = Screen.TeacherSubjectDetail(currentSubj)
                        else currentScreen = Screen.ContentSubjectsList
                    },
                    viewModel = contentViewModel
                )
            }

            is Screen.AddEditQuiz -> {
                val currentSubj = activeTeacherSubject ?: subjectsViewModel.subjects.value.firstOrNull { it.id == screen.subjectId }
                AddEditQuizScreen(
                    subjectId = screen.subjectId,
                    quizToEdit = screen.quiz,
                    onBack = {
                        if (currentSubj != null) currentScreen = Screen.TeacherSubjectDetail(currentSubj)
                        else currentScreen = Screen.ContentSubjectsList
                    },
                    onQuizSaved = {
                        if (currentSubj != null) currentScreen = Screen.TeacherSubjectDetail(currentSubj)
                        else currentScreen = Screen.ContentSubjectsList
                    },
                    viewModel = contentViewModel
                )
            }

            is Screen.GenerateCode -> {
                val currentSubj = activeTeacherSubject ?: subjectsViewModel.subjects.value.firstOrNull { it.id == screen.subjectId }
                GenerateCodeScreen(
                    subjectId = screen.subjectId,
                    subjectName = screen.subjectName,
                    onBack = {
                        if (currentSubj != null) currentScreen = Screen.TeacherSubjectDetail(currentSubj)
                        else currentScreen = Screen.ContentSubjectsList
                    },
                    onCodeGenerated = {
                        if (currentSubj != null) currentScreen = Screen.TeacherSubjectDetail(currentSubj)
                        else currentScreen = Screen.ContentSubjectsList
                    },
                    viewModel = contentViewModel
                )
            }

            // Student View Portal
            is Screen.StudentPortal -> {
                StudentContentScreen(
                    onBack = {
                        when (loginViewModel.sessionManager.role) {
                            "student_online" -> currentScreen = Screen.StudentOnlineHome(loginViewModel.sessionManager.fullName.ifBlank { "الطالب" })
                            "student" -> currentScreen = Screen.StudentHome(loginViewModel.sessionManager.fullName.ifBlank { "الطالب" })
                            "parent" -> currentScreen = Screen.ParentHome(
                                parentName = loginViewModel.sessionManager.fullName.ifBlank { "ولي الأمر" },
                                studentName = "الطالب المسجل"
                            )
                            "teacher" -> currentScreen = Screen.TeacherDashboard
                            else -> currentScreen = Screen.RoleSelection
                        }
                    },
                    onSelectSubject = { subject ->
                        activeStudentSubject = subject
                        currentScreen = Screen.StudentSubjectDetail(subject)
                    },
                    viewModel = studentViewModel
                )
            }

            is Screen.StudentSubjectDetail -> {
                StudentSubjectScreen(
                    subject = screen.subject,
                    onBack = {
                        currentScreen = Screen.StudentPortal
                    },
                    onOpenVideo = { url, title ->
                        currentScreen = Screen.InAppViewer(url = url, title = title)
                    },
                    onOpenQuiz = { url, title ->
                        currentScreen = Screen.InAppViewer(url = url, title = title)
                    },
                    onEnterCode = {
                        currentScreen = Screen.StudentEnterCode(screen.subject)
                    },
                    viewModel = studentViewModel
                )
            }

            is Screen.StudentEnterCode -> {
                EnterCodeScreen(
                    subject = screen.subject,
                    onBack = {
                        currentScreen = Screen.StudentSubjectDetail(screen.subject)
                    },
                    onCodeActivated = {
                        currentScreen = Screen.StudentSubjectDetail(screen.subject)
                    },
                    viewModel = studentViewModel
                )
            }

            is Screen.InAppViewer -> {
                val fallbackSubj = activeStudentSubject ?: subjectsViewModel.subjects.value.firstOrNull()
                VideoPlayerScreen(
                    url = screen.url,
                    title = screen.title,
                    onBack = {
                        if (fallbackSubj != null) currentScreen = Screen.StudentSubjectDetail(fallbackSubj)
                        else currentScreen = Screen.StudentPortal
                    }
                )
            }

            is Screen.Placeholder -> {
                PlaceholderScreen(
                    title = screen.title,
                    subtitle = screen.subtitle,
                    accentColor = screen.accentColor,
                    icon = screen.icon,
                    onBack = {
                        currentScreen = if (screen.returnToTeacher) {
                            Screen.TeacherDashboard
                        } else {
                            Screen.RoleSelection
                        }
                    }
                )
            }

            else -> {}
        }
    }
}
