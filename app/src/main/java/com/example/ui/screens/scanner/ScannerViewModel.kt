package com.example.ui.screens.scanner

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.AttendanceRecord
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.ScanResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BannerNotice(
    val message: String,
    val isSuccess: Boolean,
    val isWarning: Boolean = false,
    val studentName: String? = null,
    val barcode: String? = null
)

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = AttendanceRepository(
        studentDao = database.studentDao(),
        attendanceDao = database.attendanceDao()
    )

    private val soundFeedbackHelper = SoundFeedbackHelper(application)

    val last5Attendances: StateFlow<List<AttendanceRecord>> = repository.last5Attendances
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _bannerNotice = MutableStateFlow<BannerNotice?>(null)
    val bannerNotice: StateFlow<BannerNotice?> = _bannerNotice.asStateFlow()

    private val _isFlashOn = MutableStateFlow(false)
    val isFlashOn: StateFlow<Boolean> = _isFlashOn.asStateFlow()

    private val _unknownBarcodeDialog = MutableStateFlow<String?>(null)
    val unknownBarcodeDialog: StateFlow<String?> = _unknownBarcodeDialog.asStateFlow()

    private var bannerClearJob: Job? = null

    fun toggleFlash() {
        _isFlashOn.value = !_isFlashOn.value
    }

    fun onBarcodeScanned(barcode: String) {
        viewModelScope.launch {
            val result = repository.processBarcode(barcode)
            handleScanResult(result, barcode)
        }
    }

    private fun handleScanResult(result: ScanResult, rawBarcode: String) {
        when (result) {
            is ScanResult.Success -> {
                // Play Beep + Vibration
                soundFeedbackHelper.playBeep()
                soundFeedbackHelper.vibrate()

                val msg = "تم تسجيل الحضور - ${result.student.name}"
                showToast(msg)
                setBanner(
                    BannerNotice(
                        message = msg,
                        isSuccess = true,
                        studentName = result.student.name,
                        barcode = result.student.barcode
                    )
                )
            }

            is ScanResult.AlreadyRecorded -> {
                // Light vibration
                soundFeedbackHelper.vibrate()

                val msg = "تم تسجيل الحضور مسبقًا اليوم (${result.student.name})"
                showToast(msg)
                setBanner(
                    BannerNotice(
                        message = "تم تسجيل الحضور مسبقًا اليوم للـطالب: ${result.student.name}",
                        isSuccess = false,
                        isWarning = true,
                        studentName = result.student.name,
                        barcode = result.student.barcode
                    )
                )
            }

            is ScanResult.NotFound -> {
                soundFeedbackHelper.vibrate()

                val msg = "باركود غير معروف ($rawBarcode) - يرجى إضافة الطالب أولاً"
                showToast(msg)
                setBanner(
                    BannerNotice(
                        message = msg,
                        isSuccess = false,
                        isWarning = false,
                        barcode = rawBarcode
                    )
                )
                // Offer quick addition dialog
                _unknownBarcodeDialog.value = rawBarcode
            }

            is ScanResult.Error -> {
                setBanner(
                    BannerNotice(
                        message = result.message,
                        isSuccess = false,
                        isWarning = false
                    )
                )
            }
        }
    }

    fun dismissUnknownDialog() {
        _unknownBarcodeDialog.value = null
    }

    fun registerNewStudent(
        name: String,
        barcode: String,
        groupName: String,
        parentPhone: String
    ) {
        viewModelScope.launch {
            repository.registerNewStudent(
                name = name,
                barcode = barcode,
                groupName = groupName,
                parentPhone = parentPhone
            )
            _unknownBarcodeDialog.value = null
            // Now record attendance immediately for the newly registered student
            onBarcodeScanned(barcode)
        }
    }

    private fun setBanner(notice: BannerNotice) {
        _bannerNotice.value = notice
        bannerClearJob?.cancel()
        bannerClearJob = viewModelScope.launch {
            delay(4000L)
            _bannerNotice.value = null
        }
    }

    private fun showToast(text: String) {
        Toast.makeText(getApplication(), text, Toast.LENGTH_SHORT).show()
    }

    override fun onCleared() {
        super.onCleared()
        soundFeedbackHelper.release()
    }
}
