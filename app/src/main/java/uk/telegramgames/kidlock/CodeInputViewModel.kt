package uk.telegramgames.kidlock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class CodeInputViewModel(application: Application) : AndroidViewModel(application) {
    private val dataRepository = DataRepository.getInstance(application)
    private val usageStatsHelper = UsageStatsHelper(application)

    private val _remainingTimeMinutes = MutableLiveData<Int>()
    val remainingTimeMinutes: LiveData<Int> = _remainingTimeMinutes

    private val _codeInput = MutableLiveData<String>()
    val codeInput: LiveData<String> = _codeInput

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _isCodeValid = MutableLiveData<Boolean>()
    val isCodeValid: LiveData<Boolean> = _isCodeValid

    private val _shouldOpenAdmin = MutableLiveData<Boolean>()
    val shouldOpenAdmin: LiveData<Boolean> = _shouldOpenAdmin

    init {
        updateRemainingTime()
    }

    fun setCodeInput(input: String) {
        _codeInput.value = input
        _message.value = null
    }

    fun addCharToCode(char: Char) {
        val current = _codeInput.value ?: ""
        if (current.length < 6) {
            val newCode = current + char
            _codeInput.value = newCode
            
            // Автоматическая проверка при вводе 6-го символа
            if (newCode.length == 6) {
                activateCode()
            }
        }
    }

    fun removeCharFromCode() {
        val current = _codeInput.value ?: ""
        if (current.isNotEmpty()) {
            _codeInput.value = current.dropLast(1)
        }
    }

    fun clearCode() {
        _codeInput.value = ""
        _message.value = null
    }

    fun activateCode() {
        val codeValue = _codeInput.value ?: ""
        if (codeValue.length != 6) {
            _message.value = getApplication<Application>().getString(R.string.code_length_error)
            _isCodeValid.value = false
            return
        }

        // Проверяем, нужно ли сбросить дневной лимит ПЕРЕД добавлением времени
        dataRepository.ensureDailyResetIfNeeded()

        // Сначала проверяем, является ли это кодом доступа
        val code = dataRepository.findCode(codeValue)
        if (code != null) {
            // Это код доступа
            if (code.isUsed) {
                _message.value = getApplication<Application>().getString(R.string.code_already_used_error)
                _isCodeValid.value = false
                return
            }

            // Активация кода доступа
            val updatedCode = code.copy(
                isUsed = true,
                usedDate = Date()
            )
            dataRepository.updateCode(updatedCode)

            // Добавляем время (только в addedTimeMinutes, оставшееся время рассчитывается через UsageStatsHelper)
            if (code.addedTimeMinutes > 0) {
                dataRepository.addToAddedTime(code.addedTimeMinutes)
            }

            _message.value = getApplication<Application>().getString(R.string.code_activated_format, code.addedTimeMinutes)
            _isCodeValid.value = true
            clearCode()
            updateRemainingTime()
            return
        }

        // Если код не найден среди кодов доступа, проверяем, не является ли это PIN
        val pin = dataRepository.getPin()
        if (codeValue == pin) {
            // Это PIN-код, открываем админ-панель
            _shouldOpenAdmin.value = true
            clearCode()
            return
        }

        // Код не найден ни среди кодов доступа, ни как PIN
        _message.value = getApplication<Application>().getString(R.string.code_not_found_error)
        _isCodeValid.value = false
    }

    fun updateRemainingTime() {
        viewModelScope.launch(Dispatchers.IO) {
            // Проверяем, нужно ли сбросить дневной лимит
            dataRepository.ensureDailyResetIfNeeded()

            val remaining = usageStatsHelper.getRemainingTimeMinutes(
                dataRepository.getDailyTimeLimitMinutes(),
                dataRepository.getAddedTimeMinutes()
            )

            _remainingTimeMinutes.postValue(remaining)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun clearShouldOpenAdmin() {
        _shouldOpenAdmin.value = false
    }
}

