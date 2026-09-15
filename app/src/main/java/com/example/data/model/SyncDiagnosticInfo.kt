package com.example.data.model

/**
 * Диагностическая информация о последней попытке сетевой синхронизации расписания.
 */
data class SyncDiagnosticInfo(
    val lastSyncTime: String = "Не выполнялась",
    val checkedUrl: String = "https://guo-mpk.by/raspisanie/",
    val httpStatusCode: Int = 0,
    val receivedBytes: Long = 0,
    val lessonsFound: Int = 0,
    val statusMessage: String = "Ожидание синхронизации",
    val isSuccess: Boolean = false,
    val isSyncing: Boolean = false
)
