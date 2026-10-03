package com.politex.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * V15: единые токены дизайн-системы «Мой Политех» (решение UX-совета волны V).
 *
 * Правило двух ролей цвета:
 *  - PRIMARY ACTION («Добавить», «Сохранить», «Сменить») = ColorBrandFill + белый текст;
 *  - SELECTED STATE (чип/сегмент/фильтр/хаб)             = ColorTopBar + белый текст.
 *
 * Все интерактивы — не ниже HitTarget; кнопки/поля/иконки фиксированных высот,
 * чтобы Materials/Tasks/Grades/Settings/Dialogs выглядели одной системой.
 */
object Tokens {
    /** Минимум любого интерактива (конвенция проекта, доступность). */
    val HitTarget = 44.dp

    /** Все primary/secondary кнопки («ДОБАВИТЬ», «СОХРАНИТЬ», «СМЕНИТЬ»). */
    val ButtonHeight = 44.dp

    /** Поля поиска. */
    val SearchHeight = 44.dp

    /** Экранные фильтры/хабы/сегменты-переключатели (шапка Tasks, хаб Уроков). */
    val ChipScreen = 40.dp

    /** Чипы внутри панелей и диалогов (категории оценок, категория/время задания). */
    val ChipForm = 32.dp

    /** Все IconButton (back, ✕, чекбокс, шестерёнка). */
    val IconHit = 44.dp

    /** CAPS-текст кнопок. */
    val ButtonText = 12.sp

    /** Текст чипов (Bold selected / Medium unselected). */
    val ChipText = 11.sp

    /** Заголовки секций/категорий: CAPS, ColorBrandBlue. */
    val SectionHeader = 12.sp
}
