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
    val isSyncing: Boolean = false,
    /** Первые символы тела последнего ответа сервера — видно, пришёл .doc или HTML-страница */
    val responsePreview: String = "",
    /** Что парсер думает о формате последнего документа: doc-utf16 / doc-cp1251 / docx / html / text */
    val parseFormat: String = "",
    /** Какая стратегия парсинга сработала: grid / cells / line / docx-xml */
    val parseStrategy: String = "",
    /** Сколько текстовых фрагментов извлечено из документа */
    val parseRuns: Int = 0,
    /** Найден ли в документе заголовок блока целевой группы */
    val groupFound: Boolean = false,
    /** Ошибки разбора (имена исключений по стратегиям) — пусто, если всё чисто */
    val parseError: String = "",
    /** Первые символы текста, извлечённого из документа */
    val textPreview: String = ""
)
