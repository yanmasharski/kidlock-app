package uk.telegramgames.kidlock

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat

class ScreenTimeAccessibilityService : AccessibilityService() {

    private lateinit var dataRepository: DataRepository
    private lateinit var usageStatsHelper: UsageStatsHelper
    private val handler = Handler(Looper.getMainLooper())
    private var periodicCheckRunnable: Runnable? = null
    private val CHECK_INTERVAL_MS = 5_000L // 5 секунд - более частая проверка
    @Volatile
    private var lastKnownPackageName: String? = null
    @Volatile
    private var lastBlockTime: Long = 0
    private val MIN_BLOCK_INTERVAL_MS = 500L // Минимальный интервал между блокировками (500мс)
    private val activityManager: ActivityManager by lazy {
        getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
    private val usageStatsManager: UsageStatsManager? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        } else {
            null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        dataRepository = DataRepository.getInstance(this)
        usageStatsHelper = UsageStatsHelper(this)
        createNotificationChannel()
        startPeriodicCheck()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        // ВАЖНО: Сначала проверяем KidLock - он всегда должен быть разрешен
        // Разрешаем работу самого KidLock (даже если время истекло)
        if (packageName == this.packageName) {
            lastKnownPackageName = packageName
            return
        }

        // Если это системное приложение (лаунчер), очищаем lastKnownPackageName
        if (isSystemPackage(packageName)) {
            lastKnownPackageName = null // Очищаем, так как мы на домашнем экране
            return
        }

        // Сохраняем последнее известное приложение для периодической проверки
        lastKnownPackageName = packageName

        // Проверяем оставшееся время
        val dailyLimit = dataRepository.getDailyTimeLimitMinutes()
        val addedTime = dataRepository.getAddedTimeMinutes()
        val hasTime = usageStatsHelper.hasRemainingTime(dailyLimit, addedTime)

        if (!hasTime) {
            // Блокируем запуск приложения - возвращаемся на домашний экран
            // Проверка KidLock уже выполнена в начале функции, так что он не будет заблокирован
            blockAppLaunch()
            // Также принудительно закрываем приложение, если оно уже запущено
            forceCloseApp(packageName)
        }
    }

    private fun isSystemPackage(packageName: String): Boolean {
        return packageName.startsWith("com.android") ||
                packageName.startsWith("android") ||
                packageName == "com.google.android.tv.settings" ||
                packageName == "com.google.android.leanbacklauncher"
    }

    private fun blockAppLaunch() {
        // Защита от слишком частых блокировок
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < MIN_BLOCK_INTERVAL_MS) {
            return
        }
        lastBlockTime = now

        // Дополнительная проверка: убеждаемся, что мы не блокируем KidLock
        val currentPackage = getCurrentPackageName()
        if (currentPackage == this.packageName) {
            // Если текущее приложение - KidLock, не блокируем
            return
        }

        // Очищаем lastKnownPackageName, так как мы блокируем приложение и возвращаемся на домашний экран
        lastKnownPackageName = null

        // Возвращаемся на домашний экран
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(homeIntent)

        // Показываем уведомление
        showBlockNotification()
    }

    private fun showBlockNotification() {
        val channelId = "screen_time_block"
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.time_limit_reached))
            .setContentText(getString(R.string.time_limit_reached_message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "screen_time_block",
                getString(R.string.time_limit_notifications),
                NotificationManager.IMPORTANCE_HIGH
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPeriodicCheck()
    }

    /**
     * Запускает периодическую проверку и сворачивание приложений
     */
    private fun startPeriodicCheck() {
        periodicCheckRunnable = object : Runnable {
            override fun run() {
                checkAndMinimizeApps()
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }
        handler.post(periodicCheckRunnable!!)
    }

    /**
     * Останавливает периодическую проверку
     */
    private fun stopPeriodicCheck() {
        periodicCheckRunnable?.let {
            handler.removeCallbacks(it)
            periodicCheckRunnable = null
        }
    }

    /**
     * Проверяет текущее приложение и сворачивает его, если время истекло
     */
    private fun checkAndMinimizeApps() {
        val dailyLimit = dataRepository.getDailyTimeLimitMinutes()
        val addedTime = dataRepository.getAddedTimeMinutes()
        val hasTime = usageStatsHelper.hasRemainingTime(dailyLimit, addedTime)

        // Если время есть, ничего не делаем
        if (hasTime) {
            return
        }

        // Получаем текущее приложение (используем несколько методов для надежности)
        val currentPackageName = getCurrentPackageName() ?: return

        // ВАЖНО: Разрешаем KidLock (даже если время истекло) - он должен быть доступен всегда
        if (currentPackageName == this.packageName) {
            return
        }

        // Разрешаем системные приложения (лаунчер) - не сворачиваем их, чтобы избежать бесконечного цикла
        if (isSystemPackage(currentPackageName)) {
            lastKnownPackageName = null // Очищаем, так как мы на домашнем экране
            return
        }

        // Принудительно закрываем приложение и возвращаемся на домашний экран
        forceCloseApp(currentPackageName)
        minimizeCurrentApp()
    }

    /**
     * Получает имя пакета текущего приложения
     */
    private fun getCurrentPackageName(): String? {
        return try {
            // Метод 1: Пробуем получить через rootInActiveWindow (наиболее надежный способ для AccessibilityService)
            val fromAccessibility = rootInActiveWindow?.packageName?.toString()
            if (fromAccessibility != null && !isSystemPackage(fromAccessibility)) {
                return fromAccessibility
            }

            // Метод 2: Используем UsageStatsManager для определения текущего приложения (более надежно для некоторых приложений)
            val fromUsageStats = getCurrentPackageFromUsageStats()
            if (fromUsageStats != null && !isSystemPackage(fromUsageStats)) {
                return fromUsageStats
            }

            // Метод 3: Используем ActivityManager для получения запущенных задач
            val fromActivityManager = getCurrentPackageFromActivityManager()
            if (fromActivityManager != null && !isSystemPackage(fromActivityManager)) {
                return fromActivityManager
            }

            // Метод 4: Используем последнее известное приложение
            lastKnownPackageName
        } catch (e: Exception) {
            // Если произошла ошибка, используем последнее известное приложение
            lastKnownPackageName
        }
    }

    /**
     * Получает текущее приложение через UsageStatsManager
     */
    private fun getCurrentPackageFromUsageStats(): String? {
        val manager = usageStatsManager ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null
        }

        return try {
            val time = System.currentTimeMillis()
            val stats = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                time - 1000, // Последняя секунда
                time
            ) ?: return null

            // Находим приложение с наибольшим временем в foreground
            var mostRecent: UsageStats? = null
            for (usageStats in stats) {
                if (mostRecent == null || usageStats.lastTimeUsed > mostRecent.lastTimeUsed) {
                    mostRecent = usageStats
                }
            }

            mostRecent?.packageName
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Получает текущее приложение через ActivityManager
     * Примечание: getRunningTasks требует специального разрешения и может не работать на новых версиях Android
     */
    private fun getCurrentPackageFromActivityManager(): String? {
        return try {
            // На Android 5.0+ getRunningTasks требует разрешения и может не работать
            // Используем только для старых версий Android
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                @Suppress("DEPRECATION")
                val runningTasks = activityManager.getRunningTasks(1)
                if (runningTasks.isNotEmpty()) {
                    return runningTasks[0].topActivity?.packageName
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Принудительно закрывает приложение
     * Примечание: killBackgroundProcesses работает только для фоновых процессов.
     * Для приложений в foreground используем возврат на домашний экран через minimizeCurrentApp()
     */
    private fun forceCloseApp(packageName: String) {
        try {
            // Пытаемся закрыть фоновые процессы приложения
            // Это может помочь, если приложение пытается остаться в памяти
            activityManager.killBackgroundProcesses(packageName)
        } catch (e: Exception) {
            // Игнорируем ошибки - приложение может быть уже закрыто или нет прав
            // Основное закрытие происходит через minimizeCurrentApp()
        }
    }

    /**
     * Сворачивает текущее приложение, возвращаясь на домашний экран
     */
    private fun minimizeCurrentApp() {
        try {
            // Используем глобальное действие HOME для возврата на домашний экран
            performGlobalAction(GLOBAL_ACTION_HOME)
            // Очищаем lastKnownPackageName после возврата на домашний экран
            handler.postDelayed({
                lastKnownPackageName = null
            }, 500) // Небольшая задержка, чтобы убедиться, что мы на домашнем экране
        } catch (e: Exception) {
            // Если не получилось, используем Intent
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(homeIntent)
            // Очищаем lastKnownPackageName после возврата на домашний экран
            handler.postDelayed({
                lastKnownPackageName = null
            }, 500)
        }
    }

    companion object {
        fun isServiceEnabled(context: android.content.Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val serviceName = "${context.packageName}/${ScreenTimeAccessibilityService::class.java.name}"
            return enabledServices.contains(serviceName)
        }
    }
}

