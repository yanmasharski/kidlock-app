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

    fun addDigit(digit: Char) {
        val current = _pinInput.value ?: ""
        if (current.length < 4) {
            val newPin = current + digit
            _pinInput.value = newPin
            _errorMessage.value = null
            
            // Автоматическая проверка при вводе 4-й цифры
            if (newPin.length == 4) {
                verifyPin()
            }
        }
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
        val inputPin = _pinInput.value ?: ""
        val correctPin = dataRepository.getPin()

        if (inputPin.length != 4) {
            _errorMessage.value = "PIN должен состоять из 4 цифр"
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

