package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.util.bouncyClickable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MPK_HEADER_BANNER_URL = "https://guo-mpk.by/wp-content/uploads/2024/11/cropped-cropped-cropped-logo-na-sajt.png"

/**
 * Официальный баннер верхней шапки приложения «МПК Расписание».
 *
 * СТРОГОЕ ПРАВИЛО:
 * 1. Баннер — это ВСЁ широкое официальное изображение целиком (колонны слева + текст «ГОСУДАРСТВЕННОЕ УЧРЕЖДЕНИЕ ОБРАЗОВАНИЯ "МИНСКИЙ ПОЛИТЕХНИЧЕСКИЙ КОЛЛЕДЖ"»).
 * 2. Располагается сверху экрана во всю ширину (fillMaxWidth()), не зажимается в угол!
 * 3. Сразу под баннером расположена аккуратная панель:
 *    - Неразрывный GroupBadge («41О») с защитой переноса строк
 *    - Текущий день и дата
 *    - Кнопка ручной синхронизации
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
        val sdf = SimpleDateFormat("EEEE, d MMMM", Locale("ru"))
        val formatted = sdf.format(Date())
        formatted.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString() }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            // 1. Официальный широкий баннер МГПК во всю ширину экрана (fillMaxWidth)
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(MPK_HEADER_BANNER_URL)
                    .crossfade(true)
                    .build(),
                contentDescription = "ГУО Минский политехнический колледж",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                    .wrapContentHeight()
                    .testTag("mpk_main_header_banner"),
                contentScale = ContentScale.FillWidth,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                error = {
                    // Офлайн-резерв: стилизованная широкая плашка в фирменных цветах колледжа
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        color = Color(0xFF0B1B3D)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "ГУО «МИНСКИЙ ПОЛИТЕХНИЧЕСКИЙ КОЛЛЕДЖ»",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "ОФИЦИАЛЬНОЕ РАСПИСАНИЕ ЗАНЯТИЙ",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            )

            // 2. Панель под баннером: Неразрывный GroupBadge + Текущий день/дата + Кнопка синхронизации
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Левая часть: Выбор группы с защитой переноса
                GroupBadge(
                    groupName = currentGroup,
                    onGroupChanged = onGroupChanged
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Центральная часть: Текущий день и дата
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = currentDateText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Правая часть: Тактильная кнопка ручной синхронизации
                val infiniteTransition = rememberInfiniteTransition(label = "SyncRotation")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 900, easing = LinearEasing)
                    ),
                    label = "SyncIconRotation"
                )

                val amberColor = Color(0xFFFFB300)
                val buttonBgColor = when {
                    isSyncing -> MaterialTheme.colorScheme.primaryContainer
                    hasSyncError -> amberColor
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val iconColor = when {
                    isSyncing -> MaterialTheme.colorScheme.primary
                    hasSyncError -> Color(0xFF2E1C00)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Surface(
                    shape = CircleShape,
                    color = buttonBgColor,
                    shadowElevation = if (hasSyncError) 3.dp else 0.dp,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .bouncyClickable(
                            enabled = !isSyncing,
                            onClick = onSyncClicked
                        )
                        .testTag("sync_button")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = if (hasSyncError) "Ошибка синхронизации (повторить)" else "Синхронизировать расписание",
                            tint = iconColor,
                            modifier = Modifier
                                .size(20.dp)
                                .then(if (isSyncing) Modifier.rotate(rotation) else Modifier)
                        )
                    }
                }
            }
        }
    }
}
