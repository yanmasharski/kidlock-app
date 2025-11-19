package uk.telegramgames.kidlock

import java.util.Calendar

object TimeManager {
    private const val MILLIS_PER_MINUTE = 60 * 1000L

    /**
     * Проверяет, нужно ли сбросить дневной лимит (прошла полночь)
     */
    fun shouldResetDailyLimit(lastResetDate: Long): Boolean {
        val calendar = Calendar.getInstance()
        val today = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return lastResetDate < today
    }

    /**
     * Получает время начала текущего дня (полночь)
     */
    fun getTodayStartTime(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * Конвертирует минуты в миллисекунды
     */
    fun minutesToMillis(minutes: Int): Long {
        return minutes * MILLIS_PER_MINUTE
    }

    /**
     * Конвертирует миллисекунды в минуты
     */
    fun millisToMinutes(millis: Long): Int {
        return (millis / MILLIS_PER_MINUTE).toInt()
    }

    /**
     * Форматирует минуты в строку "X ч Y мин"
     */
    fun formatMinutes(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours} ч ${mins} мин"
            hours > 0 -> "${hours} ч"
            else -> "${mins} мин"
        }
    }
}

