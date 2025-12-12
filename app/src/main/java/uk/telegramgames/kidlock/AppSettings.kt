package uk.telegramgames.kidlock

data class AppSettings(
    val pin: String = "000000",
    val dailyTimeLimitMinutes: Int = 60
)

