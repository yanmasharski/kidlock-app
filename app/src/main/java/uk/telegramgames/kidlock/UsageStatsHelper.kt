package uk.telegramgames.kidlock

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi

class UsageStatsHelper(private val context: Context) {
    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /**
     * Проверяет, предоставлено ли разрешение PACKAGE_USAGE_STATS
     */
    fun hasUsageStatsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            val mode = appOps?.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } else {
            true
        }
    }

    /**
     * Открывает настройки для предоставления разрешения PACKAGE_USAGE_STATS
     */
    fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    /**
     * Получает статистику использования всех приложений за сегодня
     * @return время использования в миллисекундах
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun getTodayUsageTimeMillis(): Long {
        if (usageStatsManager == null) return 0L

        val todayStart = TimeManager.getTodayStartTime()
        val now = System.currentTimeMillis()

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            todayStart,
            now
        ) ?: return 0L

        // Суммируем время использования всех приложений
        var totalTime = 0L
        for (usageStats in usageStatsList) {
            // Исключаем само приложение KidLock из подсчета
            if (usageStats.packageName != context.packageName) {
                totalTime += usageStats.totalTimeInForeground
            }
        }

        return totalTime
    }

    /**
     * Получает оставшееся время на основе дневного лимита и использованного времени
     * @param dailyLimitMinutes дневной лимит в минутах
     * @param addedTimeMinutes дополнительное время, добавленное через коды
     * @return оставшееся время в минутах
     */
    fun getRemainingTimeMinutes(
        dailyLimitMinutes: Int,
        addedTimeMinutes: Int
    ): Int {
        if (!hasUsageStatsPermission()) {
            return 0
        }

        val totalAllowedMinutes = dailyLimitMinutes + addedTimeMinutes
        val usedTimeMinutes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            TimeManager.millisToMinutes(getTodayUsageTimeMillis())
        } else {
            0
        }

        val remaining = totalAllowedMinutes - usedTimeMinutes
        return remaining.coerceAtLeast(0)
    }

    /**
     * Проверяет, есть ли оставшееся время
     */
    fun hasRemainingTime(
        dailyLimitMinutes: Int,
        addedTimeMinutes: Int
    ): Boolean {
        return getRemainingTimeMinutes(dailyLimitMinutes, addedTimeMinutes) > 0
    }
}

