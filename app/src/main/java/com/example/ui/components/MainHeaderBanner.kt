package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.ui.theme.ColorBrandBlue
import com.example.ui.theme.ColorDangerFill
import com.example.ui.theme.ColorDividerLight
import com.example.ui.theme.ColorSurfaceHighlight
import com.example.ui.theme.ColorTextBody
import com.example.ui.theme.ColorTextMuted
import com.example.ui.theme.TextStyleCollegeBranding
import com.example.ui.util.bouncyClickable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MPK_HEADER_BANNER_URL = "https://guo-mpk.by/wp-content/uploads/2024/11/cropped-cropped-cropped-logo-na-sajt.png"

/**
 * Шапка приложения «Мой Политех».
 *
 * Компактная и светлая:
 * 1. Плашка даты — светлая, ниже статус-бара (тёмные иконки статус-бара всегда видны).
 * 2. Основная зона: логотип колледжа + бейдж группы + кнопка синхронизации.
 * Плашка «МЕНЮ» и аккордеон-меню удалены — навигация живёт в нижней панели.
 */
@Composable
fun MainHeaderBanner(
    currentGroup: String,
    isSyncing: Boolean,
    hasSyncError: Boolean = false,
    onGroupChanged: (String) -> Unit,
    onSyncClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        // 1. Компактная светлая плашка даты (26dp, светлый фон — иконки статус-бара видны)
        // =========================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorSurfaceHighlight)
                .statusBarsPadding()
                .height(26.dp)
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
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = ColorTextBody
                    ),
                    maxLines = 1
                )
                Text(
                    text = currentDayOfWeekName,
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = ColorTextMuted
                    ),
                    maxLines = 1
                )
            }
        }

        // =========================================================================
        // 2. ОСНОВНАЯ ЗОНА: логотип + бейдж группы + синхронизация
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color.White)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Логотип колледжа (сжимается при нехватке места)
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(MPK_HEADER_BANNER_URL)
                    .crossfade(true)
                    .build(),
                contentDescription = "Логотип МПК",
                modifier = Modifier
                    .height(44.dp)
                    .weight(1f),
                contentScale = ContentScale.Fit,
                error = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CollegePorticoIcon(
                            color = ColorBrandBlue,
                            modifier = Modifier.size(32.dp)
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

            // Бейдж группы и кнопка синхронизации
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GroupBadge(
                    groupName = currentGroup,
                    isDarkHeader = false,
                    onGroupChanged = onGroupChanged
                )

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
                    color = if (hasSyncError) ColorDangerFill else ColorBrandBlue,
                    border = BorderStroke(1.dp, ColorDividerLight),
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

        // Тонкий разделитель под шапкой
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ColorDividerLight)
        )
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
