package com.politex.adminplus

/**
 * Политика доступа к owner-возможностям сборки adminplus.
 *
 * Самый привилегированный флейвор: редактор данных преподавателей и PAT для
 * публикации правок в публичный GitHub-репозиторий. Поэтому код доступа нужен
 * всегда, независимо от того, что owner-код в `src/owner` включает гейт только
 * для `admin` (см. `OwnerRoot`).
 *
 * Единая точка правды: `AdminPlusRoot` спрашивает политику здесь, а не
 * дублирует условие по `BuildConfig.FLAVOR`. Тест — `AdminPlusGateTest`.
 */
object AdminPlusAccess {
    /** В adminplus гейт обязателен всегда. */
    const val GATE_REQUIRED: Boolean = true
}