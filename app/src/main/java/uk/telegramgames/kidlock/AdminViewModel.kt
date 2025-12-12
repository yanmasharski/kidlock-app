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

    private val _isBlockingEnabled = MutableLiveData<Boolean>()
    val isBlockingEnabled: LiveData<Boolean> = _isBlockingEnabled

    private val _canEnableBlocking = MutableLiveData<Boolean>()
    val canEnableBlocking: LiveData<Boolean> = _canEnableBlocking

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
        
        // Загружаем состояние блокировки, но проверяем разрешения
        val blockingEnabled = dataRepository.isBlockingEnabled()
        _isBlockingEnabled.value = blockingEnabled
        
        // Проверяем разрешения и устанавливаем canEnableBlocking
        val isAccessibilityEnabled = ScreenTimeAccessibilityService.isServiceEnabled(
            getApplication()
        )
        val isUsageStatsGranted = usageStatsHelper.hasUsageStatsPermission()
        val allPermissionsGranted = isAccessibilityEnabled && isUsageStatsGranted
        _canEnableBlocking.value = allPermissionsGranted
        
        // Если разрешения не предоставлены, отключаем блокировку
        if (!allPermissionsGranted && blockingEnabled) {
            dataRepository.setBlockingEnabled(false)
            _isBlockingEnabled.value = false
        }
    }

    fun setDailyLimitMinutes(minutes: Int) {
        if (minutes < 0) {
            _message.value = getApplication<Application>().getString(R.string.limit_negative_error)
            return
        }
        dataRepository.setDailyTimeLimitMinutes(minutes)
        _dailyLimitMinutes.value = minutes
        val formattedTime = TimeManager.formatMinutes(getApplication(), minutes)
        _message.value = getApplication<Application>().getString(R.string.limit_set_format, formattedTime)
        updateRemainingTime()
    }

    fun generateCodes(count: Int, minutesPerCode: Int = 30) {
        Log.d("KidLock", "AdminViewModel.generateCodes() вызван: count=$count, minutesPerCode=$minutesPerCode")
        if (count <= 0 || count > 100) {
            Log.w("KidLock", "AdminViewModel.generateCodes() - неверное количество: $count")
            _message.value = getApplication<Application>().getString(R.string.code_count_error)
            return
        }
        if (minutesPerCode < 0) {
            Log.w("KidLock", "AdminViewModel.generateCodes() - отрицательное время: $minutesPerCode")
            _message.value = getApplication<Application>().getString(R.string.time_negative_error)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            Log.d("KidLock", "AdminViewModel.generateCodes() - начинаем генерацию кодов в IO потоке")
            val newCodes = dataRepository.generateCodes(minutesPerCode, count)
            Log.d("KidLock", "AdminViewModel.generateCodes() - сгенерировано ${newCodes.size} кодов: ${newCodes.map { it.value }}")
            // Загружаем коды на главном потоке для обновления UI
            withContext(Dispatchers.Main) {
                loadCodes()
                _message.value = getApplication<Application>().getString(R.string.codes_generated_format, newCodes.size, minutesPerCode)
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
        _message.value = getApplication<Application>().getString(R.string.code_deleted)
    }

    fun changePin(newPin: String) {
        if (newPin.length != 6 || !newPin.all { it.isDigit() }) {
            _message.value = getApplication<Application>().getString(R.string.pin_format_error)
            return
        }
        dataRepository.setPin(newPin)
        _message.value = getApplication<Application>().getString(R.string.pin_changed)
    }

    fun unlock() {
        dataRepository.resetRemainingTime()
        dataRepository.resetAddedTime()
        updateRemainingTime()
        _message.value = getApplication<Application>().getString(R.string.time_unlocked)
    }

    fun updateRemainingTime() {
        viewModelScope.launch(Dispatchers.IO) {
            val dailyLimit = dataRepository.getDailyTimeLimitMinutes()
            val addedTime = dataRepository.getAddedTimeMinutes()

            // Проверяем, нужно ли сбросить дневной лимит
            dataRepository.ensureDailyResetIfNeeded()

            val remaining = usageStatsHelper.getRemainingTimeMinutes(
                dataRepository.getDailyTimeLimitMinutes(),
                dataRepository.getAddedTimeMinutes()
            )

            _remainingTimeMinutes.postValue(remaining)
        }
    }

    fun checkPermissions() {
        val isAccessibilityEnabled = ScreenTimeAccessibilityService.isServiceEnabled(
            getApplication()
        )
        val isUsageStatsGranted = usageStatsHelper.hasUsageStatsPermission()
        
        _isAccessibilityServiceEnabled.value = isAccessibilityEnabled
        _isUsageStatsPermissionGranted.value = isUsageStatsGranted
        
        // Проверяем, все ли разрешения предоставлены
        val allPermissionsGranted = isAccessibilityEnabled && isUsageStatsGranted
        _canEnableBlocking.value = allPermissionsGranted
        
        // Если разрешения не предоставлены, автоматически отключаем блокировку
        if (!allPermissionsGranted) {
            val currentBlockingState = dataRepository.isBlockingEnabled()
            if (currentBlockingState) {
                // Отключаем блокировку, если она была включена
                dataRepository.setBlockingEnabled(false)
                _isBlockingEnabled.value = false
            }
        }
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
        _message.value = if (enabled) {
            getApplication<Application>().getString(R.string.autostart_enabled)
        } else {
            getApplication<Application>().getString(R.string.autostart_disabled)
        }
    }

    fun setBlockingEnabled(enabled: Boolean) {
        // Проверяем разрешения перед включением
        val isAccessibilityEnabled = _isAccessibilityServiceEnabled.value ?: false
        val isUsageStatsGranted = _isUsageStatsPermissionGranted.value ?: false
        val allPermissionsGranted = isAccessibilityEnabled && isUsageStatsGranted
        
        // Если пытаемся включить без разрешений, не позволяем
        if (enabled && !allPermissionsGranted) {
            _message.value = getApplication<Application>().getString(R.string.blocking_requires_permissions)
            return
        }
        
        dataRepository.setBlockingEnabled(enabled)
        _isBlockingEnabled.value = enabled
        _message.value = if (enabled) {
            getApplication<Application>().getString(R.string.blocking_enabled)
        } else {
            getApplication<Application>().getString(R.string.blocking_disabled)
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

