package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("سنتر لوبيتال", appName)
  }

  @Test
  fun `test default accounts login`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.database.AppDatabase.getDatabase(context)
    val repo = com.example.data.repository.UserRepository(db.userDao())

    // 1. Admin login
    val admin = repo.login("admin", "admin123")
    org.junit.Assert.assertNotNull("Admin should not be null", admin)
    assertEquals("teacher", admin?.role)

    // 2. Student login
    val student = repo.login("student", "student123")
    org.junit.Assert.assertNotNull("Student should not be null", student)
    assertEquals("student", student?.role)

    // 3. Parent login
    val parent = repo.login("parent", "parent123")
    org.junit.Assert.assertNotNull("Parent should not be null", parent)
    assertEquals("parent", parent?.role)

    // 4. Case-insensitivity and whitespace trim
    val adminWithSpaces = repo.login("  ADMIN  ", " admin123 ")
    org.junit.Assert.assertNotNull("Admin with spaces should login", adminWithSpaces)
    assertEquals("teacher", adminWithSpaces?.role)
  }
}
