package uk.telegramgames.kidlock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class PinInputViewModel(application: Application) : AndroidViewModel(application) {
    private val dataRepository = DataRepository.getInstance(application)

    private val _pinInput = MutableLiveData<String>()
    val pinInput: LiveData<String> = _pinInput

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _isPinCorrect = MutableLiveData<Boolean>()
    val isPinCorrect: LiveData<Boolean> = _isPinCorrect

    init {
        _pinInput.value = ""
    }

    fun setPinInput(pin: String) {
        // InputFilter уже обработал фильтрацию и регистр, просто сохраняем значение
        _pinInput.value = pin
        _errorMessage.value = null
    }

    fun addDigit(digit: Char) {
        val current = _pinInput.value ?: ""
        val filteredDigit = digit.uppercaseChar().takeIf { it.isLetterOrDigit() } ?: return
        val newPin = current + filteredDigit
        _pinInput.value = newPin
        _errorMessage.value = null
    }

    fun removeDigit() {
        val current = _pinInput.value ?: ""
        if (current.isNotEmpty()) {
            _pinInput.value = current.dropLast(1)
            _errorMessage.value = null
        }
    }

    fun clearPin() {
        _pinInput.value = ""
        _errorMessage.value = null
    }

    fun verifyPin(): Boolean {
        val inputPin = (_pinInput.value ?: "").uppercase()
        val correctPin = dataRepository.getPin().uppercase()

        if (inputPin.isEmpty()) {
            _errorMessage.value = "Введите PIN-код"
            _isPinCorrect.value = false
            return false
        }

        if (inputPin == correctPin) {
            _isPinCorrect.value = true
            _errorMessage.value = null
            return true
        } else {
            _errorMessage.value = "Неверный PIN"
            _isPinCorrect.value = false
            clearPin()
            return false
        }
    }
}

