package uk.telegramgames.kidlock

data class AppSettings(
    val pin: String = "0000",
    val dailyTimeLimitMinutes: Int = 60
)

