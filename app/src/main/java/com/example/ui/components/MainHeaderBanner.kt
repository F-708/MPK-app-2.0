package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.ColorActiveBlue
import com.example.ui.theme.ColorBorderLight
import com.example.ui.theme.ColorBrandBlue
import com.example.ui.theme.ColorMenuBg
import com.example.ui.theme.ColorMenuBorder
import com.example.ui.theme.ColorMenuIcon
import com.example.ui.theme.ColorMenuSubBg
import com.example.ui.theme.ColorMenuSubtext
import com.example.ui.theme.ColorMenuText
import com.example.ui.theme.ColorTopBar
import com.example.ui.theme.TextStyleCollegeBranding
import com.example.ui.theme.TextStyleMenuBarTitle
import com.example.ui.theme.TextStyleMenuItem
import com.example.ui.theme.TextStyleMenuSubItem
import com.example.ui.theme.TextStyleTopDateBar
import com.example.ui.util.bouncyClickable
import com.example.ui.viewmodel.AppTab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MPK_HEADER_BANNER_URL = "https://guo-mpk.by/wp-content/uploads/2024/11/cropped-cropped-cropped-logo-na-sajt.png"

/**
 * Официальная шапка (Header) и аккордеон-меню сайта Минского политехнического колледжа (МПК).
 *
 * ТОЧНОЕ СООТВЕТСТВИЕ ДИЗАЙН-СИСТЕМЕ:
 * 1. Top Date Bar: высота 32px, фон #001737, слева белый текст даты (13px, Bold, #FFFFFF).
 * 2. Основная белая зона: высота 64px, фон #FFFFFF. Логотип-портик (36px, #0B3564) + название колледжа (11-12px ExtraBold #0B3564 ALL CAPS).
 * 3. Полоса меню: высота 44px, фон #0B3564. Гамбургер + надпись «МЕНЮ» (15px Bold ALL CAPS #FFFFFF) + GroupBadge + кнопка синхронизации.
 * 4. Меню-аккордеон: фон #232527, строки 48px, разделители 1px #35393D, квадрат 48x48px (серый [+] или #0072CE белый [-]), подпункты 42px #1C1E20.
 */
@Composable
fun MainHeaderBanner(
    currentGroup: String,
    isSyncing: Boolean,
    hasSyncError: Boolean = false,
    onGroupChanged: (String) -> Unit,
    onSyncClicked: () -> Unit,
    onSelectTab: ((AppTab) -> Unit)? = null,
    onOpenCalendarArchive: (() -> Unit)? = null,
    onOpenPasteDialog: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    var expandedMenuSection by remember { mutableStateOf<String?>("SCHEDULE") }

    val currentDateText = remember {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT)
        sdf.format(Date())
    }

    val currentDayOfWeekName = remember {
        val sdf = SimpleDateFormat("EEEE", Locale("ru"))
        val day = sdf.format(Date())
        day.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        // =========================================================================
        // 1. TOP DATE BAR (Высота 32px, фон #001737, белый текст 13px Bold)
        // =========================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorTopBar)
                .statusBarsPadding()
                .height(32.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentDateText,
                    style = TextStyleTopDateBar,
                    maxLines = 1
                )
                Text(
                    text = currentDayOfWeekName,
                    style = TextStyleTopDateBar.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = ColorMenuSubtext
                    ),
                    maxLines = 1
                )
            }
        }

        // =========================================================================
        // 2. ОСНОВНАЯ БЕЛАЯ ЗОНА (Высота 64px, фон #FFFFFF, логотип + 2-строчный текст)
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(Color.White)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // Логотип колледжа / баннер
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(MPK_HEADER_BANNER_URL)
                    .crossfade(true)
                    .build(),
                contentDescription = "Логотип МПК",
                modifier = Modifier
                    .height(44.dp)
                    .weight(1f, fill = false),
                contentScale = ContentScale.Fit,
                error = {
                    // Векторный портик колледжа + брендинг
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CollegePorticoIcon(
                            color = ColorBrandBlue,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "ГОСУДАРСТВЕННОЕ УЧРЕЖДЕНИЕ ОБРАЗОВАНИЯ",
                                style = TextStyleCollegeBranding,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "«МИНСКИЙ ПОЛИТЕХНИЧЕСКИЙ КОЛЛЕДЖ»",
                                style = TextStyleCollegeBranding.copy(fontSize = 12.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            )
        }

        // =========================================================================
        // 3. ПОЛОСА МЕНЮ (Высота 44px, фон #0B3564, гамбургер + «МЕНЮ» + GroupBadge)
        // =========================================================================
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            color = ColorBrandBlue,
            shape = RoundedCornerShape(0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Левая часть: Кнопка-триггер меню «МЕНЮ»
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .bouncyClickable { isMenuOpen = !isMenuOpen }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                        .testTag("menu_button_bar")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Меню",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "МЕНЮ",
                        style = TextStyleMenuBarTitle,
                        maxLines = 1
                    )
                }

                // Правая часть: GroupBadge («41О») и кнопка синхронизации
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GroupBadge(
                        groupName = currentGroup,
                        isDarkHeader = true,
                        onGroupChanged = onGroupChanged
                    )

                    // Кнопка синхронизации с анимацией вращения
                    val infiniteTransition = rememberInfiniteTransition(label = "SyncRotation")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 900, easing = LinearEasing)
                        ),
                        label = "SyncRotationAngle"
                    )

                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = if (hasSyncError) Color(0xFFE53935) else ColorTopBar,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF35393D)),
                        modifier = Modifier
                            .size(30.dp)
                            .bouncyClickable(
                                enabled = !isSyncing,
                                onClick = onSyncClicked
                            )
                            .testTag("sync_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Синхронизация",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(16.dp)
                                    .then(if (isSyncing) Modifier.rotate(rotation) else Modifier)
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 4. МЕНЮ-АККОРДЕОН (Dark Menu Drawer: фон #232527, строки 48px, разделители 1px #35393D)
        // =========================================================================
        AnimatedVisibility(
            visible = isMenuOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ColorMenuBg)
                    .testTag("mpk_accordion_menu")
            ) {
                // Раздел 1: РАСПИСАНИЕ
                MpkMenuSection(
                    title = "РАСПИСАНИЕ",
                    isExpanded = expandedMenuSection == "SCHEDULE",
                    onToggle = {
                        expandedMenuSection = if (expandedMenuSection == "SCHEDULE") null else "SCHEDULE"
                    },
                    subItems = listOf(
                        "Расписание занятий" to {
                            onSelectTab?.invoke(AppTab.SCHEDULE)
                            isMenuOpen = false
                        },
                        "Архив расписания (календарь)" to {
                            onOpenCalendarArchive?.invoke()
                            isMenuOpen = false
                        },
                        "Вставить текст расписания" to {
                            onOpenPasteDialog?.invoke()
                            isMenuOpen = false
                        }
                    )
                )

                // Раздел 2: ЗАДАНИЯ
                MpkMenuSection(
                    title = "УЧЕБНЫЕ ЗАДАНИЯ",
                    isExpanded = expandedMenuSection == "TASKS",
                    onToggle = {
                        expandedMenuSection = if (expandedMenuSection == "TASKS") null else "TASKS"
                    },
                    subItems = listOf(
                        "Все задания и дедлайны" to {
                            onSelectTab?.invoke(AppTab.TASKS)
                            isMenuOpen = false
                        },
                        "Курсовые и дипломные проекты" to {
                            onSelectTab?.invoke(AppTab.TASKS)
                            isMenuOpen = false
                        }
                    )
                )

                // Раздел 3: ЗВОНКИ
                MpkMenuSection(
                    title = "РАСПИСАНИЕ ЗВОНКОВ",
                    isExpanded = expandedMenuSection == "BELLS",
                    onToggle = {
                        expandedMenuSection = if (expandedMenuSection == "BELLS") null else "BELLS"
                    },
                    subItems = listOf(
                        "Основной график звонков" to {
                            onSelectTab?.invoke(AppTab.BELLS)
                            isMenuOpen = false
                        },
                        "Информационный час (Четверг)" to {
                            onSelectTab?.invoke(AppTab.BELLS)
                            isMenuOpen = false
                        }
                    )
                )

                // Раздел 4: НАСТРОЙКИ
                MpkMenuSection(
                    title = "НАСТРОЙКИ И ДИАГНОСТИКА",
                    isExpanded = expandedMenuSection == "SETTINGS",
                    onToggle = {
                        expandedMenuSection = if (expandedMenuSection == "SETTINGS") null else "SETTINGS"
                    },
                    subItems = listOf(
                        "Параметры и тема оформления" to {
                            onSelectTab?.invoke(AppTab.SETTINGS)
                            isMenuOpen = false
                        },
                        "Диагностика сети МГПК" to {
                            onSelectTab?.invoke(AppTab.SETTINGS)
                            isMenuOpen = false
                        }
                    )
                )
            }
        }
    }
}

/**
 * Строка раздела меню-аккордеона по строгой спецификации Design System МПК:
 * - Высота: 48px, разделитель снизу: 1px solid #35393D.
 * - Слева: отступ 16px, текст белыми заглавными буквами (#FFFFFF, 14px SemiBold).
 * - Справа: вертикальный разделитель 1px solid #35393D и квадрат 48px x 48px.
 * - Состояние "Не раскрыто": квадрат темный, по центру серый [+] (#9CA3AF).
 * - Состояние "Раскрыто": квадрат залит цветом #0072CE, по центру белый [-] (#FFFFFF).
 * - Вложенные пункты: фон #1C1E20, отступ 32px, текст #D1D5DB, высота строки 42px.
 */
@Composable
fun MpkMenuSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    subItems: List<Pair<String, () -> Unit>>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Главная строка пункта меню
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(ColorMenuBg)
                .clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Название раздела
            Text(
                text = title,
                style = TextStyleMenuItem,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Вертикальный 1px разделитель
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(ColorMenuBorder)
            )

            // Строгий квадрат 48x48px переключателя [+]/[-]
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(if (isExpanded) ColorActiveBlue else ColorMenuBg)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isExpanded) "—" else "+",
                    color = if (isExpanded) Color.White else ColorMenuIcon,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        // Тонкий 1px горизонтальный разделитель снизу
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ColorMenuBorder)
        )

        // Вложенные подпункты
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ColorMenuSubBg)
            ) {
                subItems.forEach { (subTitle, action) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clickable(onClick = action)
                            .padding(start = 32.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = subTitle.uppercase(Locale.ROOT),
                            style = TextStyleMenuSubItem,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ColorMenuBorder.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}

/**
 * Векторная отрисовка классического портика с колоннами МПК.
 */
@Composable
fun CollegePorticoIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Треугольный фронтон (крыша портика)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, h * 0.35f)
            lineTo(w * 0.5f, 0f)
            lineTo(w, h * 0.35f)
            close()
        }
        drawPath(path, color = color)

        // Архитрав (балка под фронтоном)
        drawRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(0f, h * 0.35f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.08f)
        )

        // 4 колонны
        val colWidth = w * 0.12f
        val colGap = (w - (4 * colWidth)) / 3f
        val colTop = h * 0.43f
        val colHeight = h * 0.45f

        for (i in 0..3) {
            val left = i * (colWidth + colGap)
            drawRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(left, colTop),
                size = androidx.compose.ui.geometry.Size(colWidth, colHeight)
            )
        }

        // База (стилобат/основание)
        drawRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(0f, h * 0.88f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.12f)
        )
    }
}
