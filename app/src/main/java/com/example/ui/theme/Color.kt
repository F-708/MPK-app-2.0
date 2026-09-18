package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

data class MpkPalette(

    val topBar: Color,
    val brandBlue: Color,
    val activeBlue: Color,
    val brandFill: Color,
    val activeFill: Color,

    val menuBg: Color,
    val menuSubBg: Color,
    val menuBorder: Color,
    val menuText: Color,
    val menuSubtext: Color,
    val menuIcon: Color,

    val bgMain: Color,
    val surfaceVariant: Color,
    val surfaceHighlight: Color,
    val textTitle: Color,
    val textBody: Color,
    val textMuted: Color,
    val textWeekdays: Color,
    val textDisabled: Color,
    val borderLight: Color,
    val dividerLight: Color,

    val success: Color,
    val successBg: Color,
    val successBorder: Color,
    val successText: Color,
    val warning: Color,
    val error: Color,
    val errorText: Color,
    val dangerFill: Color,

    val badgeHW: Color,
    val badgeLab: Color,
    val badgePract: Color,
    val badgeExam: Color,
    val badgeCourse: Color,
    val badgeDiploma: Color
)

val LightPalette = MpkPalette(
    topBar = Color(0xFF001737),
    brandBlue = Color(0xFF0B3564),
    activeBlue = Color(0xFF0072CE),
    brandFill = Color(0xFF0B3564),
    activeFill = Color(0xFF0072CE),
    menuBg = Color(0xFF232527),
    menuSubBg = Color(0xFF1C1E20),
    menuBorder = Color(0xFF35393D),
    menuText = Color(0xFFFFFFFF),
    menuSubtext = Color(0xFFD1D5DB),
    menuIcon = Color(0xFF9CA3AF),
    bgMain = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF8FAFC),
    surfaceHighlight = Color(0xFFEDF2F7),
    textTitle = Color(0xFF111827),
    textBody = Color(0xFF1F2937),
    textMuted = Color(0xFF6B7280),
    textWeekdays = Color(0xFF4B5563),
    textDisabled = Color(0xFFCBD5E1),
    borderLight = Color(0xFFE2E8F0),
    dividerLight = Color(0xFFE5E7EB),
    success = Color(0xFF16A34A),
    successBg = Color(0xFFF0FDF4),
    successBorder = Color(0xFFBBF7D0),
    successText = Color(0xFF166534),
    warning = Color(0xFFF59E0B),
    error = Color(0xFFEF4444),
    errorText = Color(0xFFDC2626),
    dangerFill = Color(0xFFDC2626),
    badgeHW = Color(0xFF0072CE),
    badgeLab = Color(0xFF8B5CF6),
    badgePract = Color(0xFF0D9488),
    badgeExam = Color(0xFFE11D48),
    badgeCourse = Color(0xFFF97316),
    badgeDiploma = Color(0xFF0B3564)
)

internal var paletteState by mutableStateOf(LightPalette)

val ColorTopBar: Color get() = paletteState.topBar
val ColorBrandBlue: Color get() = paletteState.brandBlue
val ColorActiveBlue: Color get() = paletteState.activeBlue
val ColorBrandFill: Color get() = paletteState.brandFill
val ColorActiveFill: Color get() = paletteState.activeFill

val ColorMenuBg: Color get() = paletteState.menuBg
val ColorMenuSubBg: Color get() = paletteState.menuSubBg
val ColorMenuBorder: Color get() = paletteState.menuBorder
val ColorMenuText: Color get() = paletteState.menuText
val ColorMenuSubtext: Color get() = paletteState.menuSubtext
val ColorMenuIcon: Color get() = paletteState.menuIcon

val ColorBgMain: Color get() = paletteState.bgMain
val ColorSurfaceLight: Color get() = paletteState.bgMain
val ColorSurfaceVariantLight: Color get() = paletteState.surfaceVariant
val ColorSurfaceHighlight: Color get() = paletteState.surfaceHighlight
val ColorTextTitle: Color get() = paletteState.textTitle
val ColorTextBody: Color get() = paletteState.textBody
val ColorTextMuted: Color get() = paletteState.textMuted
val ColorTextWeekdays: Color get() = paletteState.textWeekdays
val ColorTextDisabled: Color get() = paletteState.textDisabled
val ColorBorderLight: Color get() = paletteState.borderLight
val ColorDividerLight: Color get() = paletteState.dividerLight

val ColorSuccess: Color get() = paletteState.success
val ColorSuccessBg: Color get() = paletteState.successBg
val ColorSuccessBorder: Color get() = paletteState.successBorder
val ColorSuccessText: Color get() = paletteState.successText
val ColorWarning: Color get() = paletteState.warning
val ColorError: Color get() = paletteState.error
val ColorErrorText: Color get() = paletteState.errorText
val ColorDangerFill: Color get() = paletteState.dangerFill

val ColorBadgeHW: Color get() = paletteState.badgeHW
val ColorBadgeLab: Color get() = paletteState.badgeLab
val ColorBadgePract: Color get() = paletteState.badgePract
val ColorBadgeExam: Color get() = paletteState.badgeExam
val ColorBadgeCourse: Color get() = paletteState.badgeCourse
val ColorBadgeDiploma: Color get() = paletteState.badgeDiploma
