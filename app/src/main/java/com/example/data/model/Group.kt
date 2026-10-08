package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "groups",
    foreignKeys = [
        ForeignKey(
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["subjectId"])]
)
data class Group(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectId: Long,
    val grade: String,          // الصف (مثل: "الثالث الثانوي")
    val center: String,         // السنتر (مثل: "سنتر الروبي")
    val days: String,           // أيام الأسبوع مفصولة بفواصل (مثل: "السبت, الثلاثاء")
    val time: String,           // الساعة (مثل: "04:00 م")
    val price: Double,          // السعر الشهري (مثل: 300.0)
    val notes: String = ""      // ملاحظات اختيارية
) {
    // Convenient title for display in other screens
    val displayName: String
        get() = "$grade - $center"
}
