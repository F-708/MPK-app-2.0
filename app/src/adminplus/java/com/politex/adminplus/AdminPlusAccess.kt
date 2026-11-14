package com.politex.adminplus

/**
 * Политика доступа к owner-возможностям сборки adminplus.
 *
 * Заказчик (2026-11-12): adminplus — личное приложение владельца, код доступа
 * не нужен. Гейт остаётся только у публичной сборки admin (см. `OwnerRoot`).
 *
 * Единая точка правды: `AdminPlusRoot` спрашивает политику здесь, а не
 * дублирует условие по `BuildConfig.FLAVOR`. Тест — `AdminPlusGateTest`.
 */
object AdminPlusAccess {
    /** В adminplus гейт не требуется: приложение личное. */
    const val GATE_REQUIRED: Boolean = false
}
