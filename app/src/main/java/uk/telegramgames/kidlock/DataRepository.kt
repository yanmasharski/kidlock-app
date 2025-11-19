package uk.telegramgames.kidlock

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.Date

class DataRepository private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "kidlock_prefs"
        private const val KEY_PIN = "pin"
        private const val KEY_DAILY_LIMIT = "daily_limit_minutes"
        private const val KEY_CODES = "codes"
        private const val KEY_REMAINING_TIME = "remaining_time_minutes"
        private const val KEY_LAST_RESET_DATE = "last_reset_date"
        private const val KEY_ADDED_TIME = "added_time_minutes"
        private const val KEY_AUTOSTART_ENABLED = "autostart_enabled"

        @Volatile
        private var INSTANCE: DataRepository? = null

        fun getInstance(context: Context): DataRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = DataRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    // PIN
    fun getPin(): String {
        return prefs.getString(KEY_PIN, "0000") ?: "0000"
    }

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN, pin).apply()
    }

    // Daily time limit
    fun getDailyTimeLimitMinutes(): Int {
        return prefs.getInt(KEY_DAILY_LIMIT, 60)
    }

    fun setDailyTimeLimitMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_DAILY_LIMIT, minutes).apply()
    }

    // Codes
    fun getCodes(): List<Code> {
        val codesJson = prefs.getString(KEY_CODES, "") ?: ""
        Log.d("KidLock", "DataRepository.getCodes() - codesJson длина: ${codesJson.length}, содержимое: '$codesJson'")
        if (codesJson.isEmpty()) {
            Log.d("KidLock", "DataRepository.getCodes() - codesJson пустой, возвращаем emptyList()")
            return emptyList()
        }

        val codes = codesJson.split(";")
            .mapNotNull { Code.fromJsonString(it) }
        Log.d("KidLock", "DataRepository.getCodes() - распарсено ${codes.size} кодов: ${codes.map { "${it.value}(${it.addedTimeMinutes}мин, used=${it.isUsed})" }}")
        return codes
    }

    fun saveCodes(codes: List<Code>) {
        val codesJson = codes.joinToString(";") { it.toJsonString() }
        Log.d("KidLock", "DataRepository.saveCodes() - сохраняем ${codes.size} кодов: ${codes.map { "${it.value}(${it.addedTimeMinutes}мин, used=${it.isUsed})" }}")
        Log.d("KidLock", "DataRepository.saveCodes() - codesJson длина: ${codesJson.length}, содержимое: '$codesJson'")
        val success = prefs.edit().putString(KEY_CODES, codesJson).commit()
        if (!success) {
            Log.w("KidLock", "DataRepository.saveCodes() - commit не удался, пробуем apply")
            // Если commit не удался, пробуем apply
            prefs.edit().putString(KEY_CODES, codesJson).apply()
        } else {
            Log.d("KidLock", "DataRepository.saveCodes() - коды успешно сохранены через commit")
        }
    }

    fun addCode(code: Code) {
        val codes = getCodes().toMutableList()
        codes.add(code)
        saveCodes(codes)
    }

    fun updateCode(updatedCode: Code) {
        val codes = getCodes().toMutableList()
        val index = codes.indexOfFirst { it.value == updatedCode.value }
        if (index >= 0) {
            codes[index] = updatedCode
            saveCodes(codes)
        }
    }

    fun findCode(value: String): Code? {
        return getCodes().firstOrNull { it.value == value }
    }

    // Remaining time
    fun getRemainingTimeMinutes(): Int {
        return prefs.getInt(KEY_REMAINING_TIME, 0)
    }

    fun setRemainingTimeMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_REMAINING_TIME, minutes).apply()
    }

    fun addTimeMinutes(minutes: Int) {
        val current = getRemainingTimeMinutes()
        setRemainingTimeMinutes(current + minutes)
    }

    fun resetRemainingTime() {
        setRemainingTimeMinutes(0)
    }

    // Last reset date
    fun getLastResetDate(): Long {
        return prefs.getLong(KEY_LAST_RESET_DATE, 0)
    }

    fun setLastResetDate(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_RESET_DATE, timestamp).apply()
    }

    // Added time (time added through codes today)
    fun getAddedTimeMinutes(): Int {
        return prefs.getInt(KEY_ADDED_TIME, 0)
    }

    fun setAddedTimeMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_ADDED_TIME, minutes).apply()
    }

    fun addToAddedTime(minutes: Int) {
        val current = getAddedTimeMinutes()
        setAddedTimeMinutes(current + minutes)
    }

    fun resetAddedTime() {
        setAddedTimeMinutes(0)
    }

    // Autostart
    fun isAutostartEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTOSTART_ENABLED, false)
    }

    fun setAutostartEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOSTART_ENABLED, enabled).apply()
    }

    // Generate new codes
    fun generateCodes(minutesPerCode: Int, count: Int): List<Code> {
        Log.d("KidLock", "DataRepository.generateCodes() вызван: minutesPerCode=$minutesPerCode, count=$count")
        
        // Удаляем старые коды перед генерацией новых
        val oldCodesCount = getCodes().size
        Log.d("KidLock", "DataRepository.generateCodes() - удаляем старые коды: ${oldCodesCount} шт.")
        saveCodes(emptyList())
        
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val codes = mutableListOf<Code>()

        repeat(count) {
            var codeValue: String
            do {
                codeValue = (1..4).map { chars.random() }.joinToString("")
            } while (codes.any { it.value == codeValue })

            codes.add(Code(value = codeValue, addedTimeMinutes = minutesPerCode))
            Log.d("KidLock", "DataRepository.generateCodes() - сгенерирован код: $codeValue (${minutesPerCode} мин)")
        }

        Log.d("KidLock", "DataRepository.generateCodes() - сохраняем ${codes.size} новых кодов")
        saveCodes(codes)
        Log.d("KidLock", "DataRepository.generateCodes() - возвращаем ${codes.size} новых кодов: ${codes.map { it.value }}")
        return codes
    }

    // Reset daily data
    fun resetDailyData() {
        resetRemainingTime()
        resetAddedTime()
        setLastResetDate(TimeManager.getTodayStartTime())
    }

    // Initialize on first launch
    fun initializeIfNeeded() {
        if (getLastResetDate() == 0L) {
            setLastResetDate(TimeManager.getTodayStartTime())
        }
        // Убеждаемся, что PIN установлен (по умолчанию "0000")
        val currentPin = getPin()
        if (currentPin.isEmpty()) {
            setPin("0000")
        }
    }
}

