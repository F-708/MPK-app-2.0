package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
// МПК (Минский политехнический колледж) — Официальные токены Design System
// =========================================================================

// 1. Акцентные и институциональные цвета
val ColorTopBar = Color(0xFF001737)       // Глубокий темно-синий/индиго (верхняя плашка с датой, выбранная дата)
val ColorBrandBlue = Color(0xFF0B3564)     // Фирменный классический темно-синий (логотип, плашка «МЕНЮ»)
val ColorActiveBlue = Color(0xFF0072CE)    // Насыщенный ярко-синий (активные переключатели-аккордеоны [-])

// 2. Темная тема / Меню (Navigation Drawer & Menu)
val ColorMenuBg = Color(0xFF232527)        // Основной темно-серый/графитовый фон выпадающего меню
val ColorMenuSubBg = Color(0xFF1C1E20)     // Углубленный темно-серый фон для подпунктов меню
val ColorMenuBorder = Color(0xFF35393D)    // Тонкие 1px разделители между строками меню
val ColorMenuText = Color(0xFFFFFFFF)      // Чистый белый цвет названий пунктов меню
val ColorMenuSubtext = Color(0xFFD1D5DB)   // Светло-серый цвет текста подпунктов
val ColorMenuIcon = Color(0xFF9CA3AF)      // Цвет неактивной иконки [+]

// 3. Светлая тема / Контентная область
val ColorBgMain = Color(0xFFFFFFFF)        // Основной белый фон контента
val ColorTextTitle = Color(0xFF111827)     // Почти черный для крупного заголовка «Расписание»
val ColorTextBody = Color(0xFF1F2937)      // Темно-серый для чисел календаря и текста
val ColorTextMuted = Color(0xFF6B7280)     // Серый для дней недели: ПН, ВТ, СР...
val ColorTextWeekdays = Color(0xFF4B5563)  // Темно-серый для шапки календаря
val ColorTextDisabled = Color(0xFFCBD5E1)  // Приглушенный светло-серый для дней соседних месяцев
val ColorBorderLight = Color(0xFFE2E8F0)   // 1px контур кнопок переключения месяцев
val ColorDividerLight = Color(0xFFE5E7EB)  // 1px разделитель светлой темы

// 4. Дополнительные и статусные цвета
val ColorSurfaceLight = Color(0xFFFFFFFF)
val ColorSurfaceVariantLight = Color(0xFFF8FAFC)
val ColorSuccess = Color(0xFF10B981)
val ColorWarning = Color(0xFFF59E0B)
val ColorError = Color(0xFFEF4444)

// Бейджи типов заданий
val ColorBadgeHW = Color(0xFF0072CE)       // ДЗ (в тон активного синего)
val ColorBadgeLab = Color(0xFF8B5CF6)      // Лаб
val ColorBadgePract = Color(0xFF0D9488)    // Практ
val ColorBadgeExam = Color(0xFFE11D48)     // КР
val ColorBadgeCourse = Color(0xFFF97316)   // Курсовая
val ColorBadgeDiploma = Color(0xFF0B3564)  // Диплом
