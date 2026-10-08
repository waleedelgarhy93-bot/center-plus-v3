package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AccessCodeDao
import com.example.data.dao.AttendanceDao
import com.example.data.dao.ExamDao
import com.example.data.dao.ExamGradeDao
import com.example.data.dao.GroupDao
import com.example.data.dao.PaymentDao
import com.example.data.dao.QuizDao
import com.example.data.dao.AppSettingsDao
import com.example.data.dao.SettingsDao
import com.example.data.dao.StudentDao
import com.example.data.dao.StudentSubjectAccessDao
import com.example.data.dao.SubjectDao
import com.example.data.dao.UserDao
import com.example.data.dao.VideoDao
import com.example.data.model.AccessCode
import com.example.data.model.AppSettings
import com.example.data.model.Attendance
import com.example.data.model.Exam
import com.example.data.model.ExamGrade
import com.example.data.model.Group
import com.example.data.model.Payment
import com.example.data.model.Quiz
import com.example.data.model.Student
import com.example.data.model.StudentSubjectAccess
import com.example.data.model.Subject
import com.example.data.model.User
import com.example.data.model.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        User::class,
        Student::class,
        Attendance::class,
        Subject::class,
        Group::class,
        Video::class,
        Quiz::class,
        AccessCode::class,
        StudentSubjectAccess::class,
        AppSettings::class,
        Exam::class,
        ExamGrade::class,
        Payment::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun studentDao(): StudentDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun subjectDao(): SubjectDao
    abstract fun groupDao(): GroupDao
    abstract fun videoDao(): VideoDao
    abstract fun quizDao(): QuizDao
    abstract fun accessCodeDao(): AccessCodeDao
    abstract fun studentSubjectAccessDao(): StudentSubjectAccessDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun examDao(): ExamDao
    abstract fun examGradeDao(): ExamGradeDao
    abstract fun paymentDao(): PaymentDao
    fun settingsDao(): AppSettingsDao = appSettingsDao()

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lhopital_center_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialData(database)
                    }
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        if (database.userDao().getCount() == 0 ||
                            database.subjectDao().getCount() == 0 ||
                            database.studentDao().getCount() == 0
                        ) {
                            populateInitialData(database)
                        } else {
                            val userDao = database.userDao()
                            if (userDao.getUserByUsername("mohamed_online") == null) {
                                userDao.insertUser(
                                    User(
                                        username = "mohamed_online",
                                        password = "password123",
                                        role = "student_online",
                                        fullName = "محمد علي أحمد",
                                        phone = "01099887766",
                                        studentId = 8L,
                                        isActive = true,
                                        createdAt = "2026-10-01"
                                    )
                                )
                            }
                            val accessCodeDao = database.accessCodeDao()
                            if (accessCodeDao.getCodeByString("ONL-RWD-5500") == null) {
                                accessCodeDao.insertCode(
                                    AccessCode(
                                        code = "ONL-RWD-5500",
                                        cardNumber = "5500",
                                        subjectId = 1L,
                                        usedByStudentId = null,
                                        usedAt = null,
                                        isOnline = true
                                    )
                                )
                            }
                            // Ensure student 1 has card 3118 for student and parent test accounts
                            try {
                                val s1 = database.studentDao().getStudentById(1L)
                                if (s1 != null && (s1.cardNumber != "3118" || s1.barcode != "3118")) {
                                    database.studentDao().updateStudent(s1.copy(cardNumber = "3118", barcode = "3118"))
                                }
                            } catch (_: Exception) {}
                            val settingsDao = database.appSettingsDao()
                            if (settingsDao.getCount() == 0) {
                                settingsDao.insertSettings(
                                    AppSettings(
                                        id = 1L,
                                        centerName = "",
                                        teacherName = "",
                                        phone = "",
                                        logoPath = null,
                                        themeColor = "blue",
                                        isSetupComplete = false,
                                        whatsappEnabled = true,
                                        attendanceMethod = "camera"
                                    )
                                )
                            }
                            val examDao = database.examDao()
                            if (examDao.getCount() == 0) {
                                examDao.insertExams(
                                    listOf(
                                        Exam(
                                            title = "اختبار الفصل الأول - الجبر والهندسة الفراغية",
                                            subjectId = 1L,
                                            date = "2026-10-10",
                                            maxGrade = 20,
                                            notes = "شامل قوانين المصفوفات والمحددات"
                                        )
                                    )
                                )
                            }
                            val examGradeDao = database.examGradeDao()
                            if (examGradeDao.getCount() == 0) {
                                examGradeDao.insertGrades(
                                    listOf(
                                        ExamGrade(
                                            examId = 1L,
                                            studentId = 1L,
                                            grade = 18.5,
                                            createdAt = "2026-10-10"
                                        ),
                                        ExamGrade(
                                            examId = 1L,
                                            studentId = 2L,
                                            grade = 19.0,
                                            createdAt = "2026-10-10"
                                        ),
                                        ExamGrade(
                                            examId = 1L,
                                            studentId = 3L,
                                            grade = 16.0,
                                            createdAt = "2026-10-10"
                                        )
                                    )
                                )
                            }
                            val paymentDao = database.paymentDao()
                            if (paymentDao.getCount() == 0) {
                                paymentDao.insertPayments(
                                    listOf(
                                        Payment(
                                            studentId = 1L,
                                            subjectId = 1L,
                                            amount = 300.0,
                                            month = "2026-09",
                                            status = "paid",
                                            paidAt = "2026-09-05 04:15 م",
                                            createdAt = "2026-09-01"
                                        ),
                                        Payment(
                                            studentId = 1L,
                                            subjectId = 1L,
                                            amount = 300.0,
                                            month = "2026-10",
                                            status = "paid",
                                            paidAt = "2026-10-02 05:20 م",
                                            createdAt = "2026-10-01"
                                        ),
                                        Payment(
                                            studentId = 1L,
                                            subjectId = 1L,
                                            amount = 300.0,
                                            month = "2026-11",
                                            status = "unpaid",
                                            paidAt = null,
                                            createdAt = "2026-11-01"
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
            }

            private suspend fun populateInitialData(database: AppDatabase) {
                val userDao = database.userDao()
                val studentDao = database.studentDao()
                val subjectDao = database.subjectDao()
                val groupDao = database.groupDao()
                val videoDao = database.videoDao()
                val quizDao = database.quizDao()
                val accessCodeDao = database.accessCodeDao()
                val settingsDao = database.appSettingsDao()

                // 0. Seed Settings
                if (settingsDao.getCount() == 0) {
                    settingsDao.insertSettings(
                        AppSettings(
                            id = 1L,
                            centerName = "",
                            teacherName = "",
                            phone = "",
                            logoPath = null,
                            themeColor = "blue",
                            isSetupComplete = false,
                            whatsappEnabled = true,
                            attendanceMethod = "camera"
                        )
                    )
                }

                // 1. Seed Users (Teacher, Students, Parent)
                if (userDao.getCount() == 0) {
                    userDao.insertUsers(
                        listOf(
                            User(
                                username = "admin",
                                password = "admin123",
                                role = "teacher",
                                fullName = "أ. وليد الجارحي",
                                phone = "01000000000",
                                studentId = null,
                                isActive = true,
                                createdAt = "2026-10-01"
                            ),
                            User(
                                username = "student",
                                password = "student123",
                                role = "student",
                                fullName = "أحمد محمود سالم",
                                phone = "01012345678",
                                studentId = 1L,
                                isActive = true,
                                createdAt = "2026-10-01"
                            ),
                            User(
                                username = "student1",
                                password = "pass123",
                                role = "student",
                                fullName = "أحمد محمود سالم",
                                phone = "01012345678",
                                studentId = 1L,
                                isActive = true,
                                createdAt = "2026-10-01"
                            ),
                            User(
                                username = "parent",
                                password = "parent123",
                                role = "parent",
                                fullName = "ولي أمر الطالب أحمد محمود",
                                phone = "01012345678",
                                studentId = 1L,
                                isActive = true,
                                createdAt = "2026-10-01"
                            ),
                            User(
                                username = "parent1",
                                password = "parent123",
                                role = "parent",
                                fullName = "ولي أمر الطالب أحمد محمود",
                                phone = "01012345678",
                                studentId = 1L,
                                isActive = true,
                                createdAt = "2026-10-01"
                            ),
                            User(
                                username = "mohamed_online",
                                password = "password123",
                                role = "student_online",
                                fullName = "محمد علي أحمد",
                                phone = "01099887766",
                                studentId = 8L,
                                isActive = true,
                                createdAt = "2026-10-01"
                            )
                        )
                    )
                }

                // 2. Seed Subjects if empty
                var mathId = 1L
                var physicsId = 2L
                var chemistryId = 3L

                if (subjectDao.getCount() == 0) {
                    mathId = subjectDao.insertSubject(
                        Subject(
                            name = "رياضة",
                            description = "جبر، تفاضل وتكامل، استاتيكا وديناميكا",
                            icon = "functions",
                            color = "#EF4444" // أحمر
                        )
                    )
                    physicsId = subjectDao.insertSubject(
                        Subject(
                            name = "فيزياء",
                            description = "كهربية، مغناطيسية، موجات وفيزياء حديثة",
                            icon = "science",
                            color = "#3B82F6" // أزرق
                        )
                    )
                    chemistryId = subjectDao.insertSubject(
                        Subject(
                            name = "كيمياء",
                            description = "كيمياء عضوية وغير عضوية والاتزان الكيميائي",
                            icon = "biotech",
                            color = "#10B981" // أخضر
                        )
                    )
                } else {
                    val allSubjects = database.subjectDao().getSubjectById(1)
                    if (allSubjects != null) mathId = allSubjects.id
                }

                // 3. Seed Groups if empty
                if (groupDao.getCount() == 0) {
                    groupDao.insertGroups(
                        listOf(
                            Group(
                                subjectId = mathId,
                                grade = "الثالث الثانوي",
                                center = "سنتر الروبي",
                                days = "السبت, الثلاثاء",
                                time = "04:00 م",
                                price = 300.0,
                                notes = "مجموعة متميزة في سنتر الروبي - فرع وسط البلد"
                            ),
                            Group(
                                subjectId = physicsId,
                                grade = "الثالث الثانوي",
                                center = "سنتر أبو سيدهم",
                                days = "الأحد, الأربعاء",
                                time = "05:30 م",
                                price = 350.0,
                                notes = "مجموعة الفيزياء المكثفة"
                            ),
                            Group(
                                subjectId = chemistryId,
                                grade = "الثالث الثانوي",
                                center = "سنتر سمالوط",
                                days = "الاثنين, الخميس",
                                time = "06:00 م",
                                price = 320.0,
                                notes = "مجموعة الكيمياء العامة والتدريبات"
                            )
                        )
                    )
                }

                // 4. Seed Students if empty
                if (studentDao.getCount() == 0) {
                    val initialStudents = listOf(
                        Student(
                            id = 1L,
                            name = "أحمد محمود سالم",
                            barcode = "3118",
                            cardNumber = "3118",
                            parentPhone = "01012345678",
                            groupName = "الثالث الثانوي - سنتر الروبي"
                        ),
                        Student(
                            id = 2L,
                            name = "سارة محمد إبراهيم",
                            barcode = "STU102",
                            cardNumber = "3119",
                            parentPhone = "01098765432",
                            groupName = "الثالث الثانوي - سنتر أبو سيدهم"
                        ),
                        Student(
                            id = 3L,
                            name = "عمر وليد الجارحي",
                            barcode = "STU103",
                            cardNumber = "3120",
                            parentPhone = "01123456789",
                            groupName = "الثالث الثانوي - سنتر سمالوط"
                        ),
                        Student(
                            id = 4L,
                            name = "مريم حسن عبد الله",
                            barcode = "STU104",
                            cardNumber = "3121",
                            parentPhone = "01234567890",
                            groupName = "الثالث الثانوي - سنتر الروبي"
                        ),
                        Student(
                            id = 5L,
                            name = "يوسف خالد كمال",
                            barcode = "STU105",
                            cardNumber = "3122",
                            parentPhone = "01512345678",
                            groupName = "الثالث الثانوي - سنتر أبو سيدهم"
                        ),
                        Student(
                            id = 6L,
                            name = "طالب تجريبي (باركود 123456)",
                            barcode = "123456",
                            cardNumber = "123456",
                            parentPhone = "01000000000",
                            groupName = "الثالث الثانوي - سنتر الروبي"
                        ),
                        Student(
                            id = 7L,
                            name = "كارت Code128 تجريبي",
                            barcode = "1001",
                            cardNumber = "1001",
                            parentPhone = "01001122334",
                            groupName = "الثالث الثانوي - سنتر سمالوط"
                        ),
                        Student(
                            id = 8L,
                            name = "محمد علي أحمد",
                            barcode = "ONL-1001",
                            cardNumber = "5500",
                            parentPhone = "01099887766",
                            groupName = "طالب أونلاين",
                            notes = "طالب أونلاين تجريبي",
                            type = "online"
                        )
                    )
                    studentDao.insertStudents(initialStudents)
                }

                // 5. Seed Videos for Subject "رياضة" (mathId)
                if (videoDao.getCount() == 0) {
                    val v1Id = videoDao.insertVideo(
                        Video(
                            subjectId = mathId,
                            title = "مقدمة التفاضل والتكامل وقواعد الاشتقاق",
                            url = "https://drive.google.com/file/d/1Bia5A_example_drive_vid1/preview",
                            orderIndex = 1,
                            isPaid = false,
                            description = "شرح أساسيات النهايات والاشتقاق للدوال المثلثية والأسية"
                        )
                    )
                    val v2Id = videoDao.insertVideo(
                        Video(
                            subjectId = mathId,
                            title = "تطبيقات على القيم العظمى والصغرى وحساب الحجوم",
                            url = "https://drive.google.com/file/d/1Bia5A_example_drive_vid2/preview",
                            orderIndex = 2,
                            isPaid = true,
                            description = "أهم المسائل المتقدمة في المعدلات الزمنية المرتبطة والتكامل المحدد"
                        )
                    )
                    videoDao.insertVideo(
                        Video(
                            subjectId = mathId,
                            title = "الهندسة الفراغية - معادلة الخط المستقيم والمستوى",
                            url = "https://drive.google.com/file/d/1Bia5A_example_drive_vid3/preview",
                            orderIndex = 3,
                            isPaid = true,
                            description = "شرح تفصيلي للمتجهات والضرب القياسي والاتجاهي ومعادلة الكرة"
                        )
                    )

                    // 6. Seed Quizzes
                    if (quizDao.getCount() == 0) {
                        quizDao.insertQuizzes(
                            listOf(
                                Quiz(
                                    subjectId = mathId,
                                    videoId = v1Id,
                                    title = "اختبار تشخيصي في قواعد الاشتقاق",
                                    url = "https://docs.google.com/forms/d/e/1FAIpQLSf_sample_form_1/viewform",
                                    isPaid = false,
                                    date = "2026-10-01",
                                    description = "10 أسئلة اختيار من متعدد على قواعد الاشتقاق الأساسية"
                                ),
                                Quiz(
                                    subjectId = mathId,
                                    videoId = v2Id,
                                    title = "امتحان شامل على التفاضل والتكامل",
                                    url = "https://docs.google.com/forms/d/e/1FAIpQLSf_sample_form_2/viewform",
                                    isPaid = true,
                                    date = "2026-10-05",
                                    description = "امتحان إلكتروني بوقت محدد يحاكي امتحانات الثانوية العامة"
                                )
                            )
                        )
                    }

                    // 7. Seed Access Codes: "RWD-3118" and "ONL-RWD-5500"
                    if (accessCodeDao.getCodeByString("RWD-3118") == null) {
                        accessCodeDao.insertCode(
                            AccessCode(
                                code = "RWD-3118",
                                cardNumber = "3118",
                                subjectId = mathId,
                                usedByStudentId = null,
                                usedAt = null,
                                isOnline = false
                            )
                        )
                    }
                    if (accessCodeDao.getCodeByString("ONL-RWD-5500") == null) {
                        accessCodeDao.insertCode(
                            AccessCode(
                                code = "ONL-RWD-5500",
                                cardNumber = "5500",
                                subjectId = mathId,
                                usedByStudentId = null,
                                usedAt = null,
                                isOnline = true
                            )
                        )
                    }

                    // 8. Seed Exams
                    val examDao = database.examDao()
                    if (examDao.getCount() == 0) {
                        examDao.insertExams(
                            listOf(
                                Exam(
                                    title = "اختبار الفصل الأول - الجبر والهندسة الفراغية",
                                    subjectId = mathId,
                                    date = "2026-10-10",
                                    maxGrade = 20,
                                    notes = "شامل قوانين المصفوفات والمحددات"
                                ),
                                Exam(
                                    title = "امتحان شهري - الحركة الخطية وقوانين نيوتن",
                                    subjectId = physicsId,
                                    date = "2026-10-18",
                                    maxGrade = 30,
                                    notes = "اختبار نصف الفصل"
                                )
                            )
                        )
                    }

                    // 9. Seed Trial Exam Grades for Exam 1
                    val examGradeDao = database.examGradeDao()
                    if (examGradeDao.getCount() == 0) {
                        examGradeDao.insertGrades(
                            listOf(
                                ExamGrade(
                                    examId = 1L,
                                    studentId = 1L,
                                    grade = 18.5,
                                    createdAt = "2026-10-10"
                                ),
                                ExamGrade(
                                    examId = 1L,
                                    studentId = 2L,
                                    grade = 19.0,
                                    createdAt = "2026-10-10"
                                ),
                                ExamGrade(
                                    examId = 1L,
                                    studentId = 3L,
                                    grade = 16.0,
                                    createdAt = "2026-10-10"
                                )
                            )
                        )
                    }

                    // 10. Seed Payments
                    val paymentDao = database.paymentDao()
                    if (paymentDao.getCount() == 0) {
                        paymentDao.insertPayments(
                            listOf(
                                Payment(
                                    studentId = 1L,
                                    subjectId = mathId,
                                    amount = 300.0,
                                    month = "2026-09",
                                    status = "paid",
                                    paidAt = "2026-09-05 04:15 م",
                                    createdAt = "2026-09-01"
                                ),
                                Payment(
                                    studentId = 1L,
                                    subjectId = mathId,
                                    amount = 300.0,
                                    month = "2026-10",
                                    status = "paid",
                                    paidAt = "2026-10-02 05:20 م",
                                    createdAt = "2026-10-01"
                                ),
                                Payment(
                                    studentId = 1L,
                                    subjectId = mathId,
                                    amount = 300.0,
                                    month = "2026-11",
                                    status = "unpaid",
                                    paidAt = null,
                                    createdAt = "2026-11-01"
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}
