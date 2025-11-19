package uk.telegramgames.kidlock

import android.app.Application
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val dataRepository = DataRepository.getInstance(application)
    private val usageStatsHelper = UsageStatsHelper(application)

    private val _dailyLimitMinutes = MutableLiveData<Int>()
    val dailyLimitMinutes: LiveData<Int> = _dailyLimitMinutes

    private val _remainingTimeMinutes = MutableLiveData<Int>()
    val remainingTimeMinutes: LiveData<Int> = _remainingTimeMinutes

    private val _codes = MutableLiveData<List<Code>>()
    val codes: LiveData<List<Code>> = _codes

    private val _isAccessibilityServiceEnabled = MutableLiveData<Boolean>()
    val isAccessibilityServiceEnabled: LiveData<Boolean> = _isAccessibilityServiceEnabled

    private val _isUsageStatsPermissionGranted = MutableLiveData<Boolean>()
    val isUsageStatsPermissionGranted: LiveData<Boolean> = _isUsageStatsPermissionGranted

    private val _isAutostartEnabled = MutableLiveData<Boolean>()
    val isAutostartEnabled: LiveData<Boolean> = _isAutostartEnabled

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    init {
        loadSettings()
        loadCodes()
        checkPermissions()
        updateRemainingTime()
    }

    fun loadSettings() {
        _dailyLimitMinutes.value = dataRepository.getDailyTimeLimitMinutes()
        _isAutostartEnabled.value = dataRepository.isAutostartEnabled()
    }

    fun setDailyLimitMinutes(minutes: Int) {
        if (minutes < 0) {
            _message.value = "Лимит не может быть отрицательным"
            return
        }
        dataRepository.setDailyTimeLimitMinutes(minutes)
        _dailyLimitMinutes.value = minutes
        _message.value = "Лимит установлен: ${TimeManager.formatMinutes(minutes)}"
        updateRemainingTime()
    }

    fun generateCodes(count: Int, minutesPerCode: Int = 30) {
        Log.d("KidLock", "AdminViewModel.generateCodes() вызван: count=$count, minutesPerCode=$minutesPerCode")
        if (count <= 0 || count > 100) {
            Log.w("KidLock", "AdminViewModel.generateCodes() - неверное количество: $count")
            _message.value = "Количество кодов должно быть от 1 до 100"
            return
        }
        if (minutesPerCode < 0) {
            Log.w("KidLock", "AdminViewModel.generateCodes() - отрицательное время: $minutesPerCode")
            _message.value = "Время не может быть отрицательным"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            Log.d("KidLock", "AdminViewModel.generateCodes() - начинаем генерацию кодов в IO потоке")
            val newCodes = dataRepository.generateCodes(minutesPerCode, count)
            Log.d("KidLock", "AdminViewModel.generateCodes() - сгенерировано ${newCodes.size} кодов: ${newCodes.map { it.value }}")
            // Загружаем коды на главном потоке для обновления UI
            withContext(Dispatchers.Main) {
                Log.d("KidLock", "AdminViewModel.generateCodes() - переключаемся на Main поток, вызываем loadCodes()")
                loadCodes()
                _message.value = "Сгенерировано ${newCodes.size} кодов по ${minutesPerCode} минут"
                Log.d("KidLock", "AdminViewModel.generateCodes() - завершено, сообщение установлено")
            }
        }
    }

    fun loadCodes() {
        Log.d("KidLock", "AdminViewModel.loadCodes() вызван")
        val codes = dataRepository.getCodes()
        Log.d("KidLock", "AdminViewModel.loadCodes() - загружено ${codes.size} кодов из репозитория: ${codes.map { "${it.value}(${it.addedTimeMinutes}мин, used=${it.isUsed})" }}")
        _codes.postValue(codes)
        Log.d("KidLock", "AdminViewModel.loadCodes() - значение LiveData обновлено через postValue")
    }

    fun deleteCode(code: Code) {
        val codes = _codes.value?.toMutableList() ?: return
        codes.removeAll { it.value == code.value }
        dataRepository.saveCodes(codes)
        loadCodes()
        _message.value = "Код удален"
    }

    fun changePin(newPin: String) {
        if (newPin.length != 4 || !newPin.all { it.isDigit() }) {
            _message.value = "PIN должен состоять из 4 цифр"
            return
        }
        dataRepository.setPin(newPin)
        _message.value = "PIN изменен"
    }

    fun unlock() {
        dataRepository.resetRemainingTime()
        dataRepository.resetAddedTime()
        updateRemainingTime()
        _message.value = "Время разблокировано"
    }

    fun updateRemainingTime() {
        viewModelScope.launch(Dispatchers.IO) {
            val dailyLimit = dataRepository.getDailyTimeLimitMinutes()
            val addedTime = dataRepository.getAddedTimeMinutes()

            // Проверяем, нужно ли сбросить дневной лимит
            val lastReset = dataRepository.getLastResetDate()
            if (TimeManager.shouldResetDailyLimit(lastReset)) {
                dataRepository.resetDailyData()
            }

            val remaining = usageStatsHelper.getRemainingTimeMinutes(
                dataRepository.getDailyTimeLimitMinutes(),
                dataRepository.getAddedTimeMinutes()
            )

            _remainingTimeMinutes.postValue(remaining)
        }
    }

    fun checkPermissions() {
        _isAccessibilityServiceEnabled.value = ScreenTimeAccessibilityService.isServiceEnabled(
            getApplication()
        )
        _isUsageStatsPermissionGranted.value = usageStatsHelper.hasUsageStatsPermission()
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        getApplication<Application>().startActivity(intent)
    }

    fun openUsageStatsSettings() {
        usageStatsHelper.requestUsageStatsPermission()
    }

    fun setAutostartEnabled(enabled: Boolean) {
        dataRepository.setAutostartEnabled(enabled)
        _isAutostartEnabled.value = enabled
        _message.value = if (enabled) "Автозапуск включен" else "Автозапуск выключен"
    }

    fun clearMessage() {
        _message.value = null
    }
}

