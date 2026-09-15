package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.ColorBrandBlue
import com.example.ui.theme.ColorDividerLight
import com.example.ui.theme.TextStyleCollegeBranding

private const val MPK_HEADER_BANNER_URL = "https://guo-mpk.by/wp-content/uploads/2024/11/cropped-cropped-cropped-logo-na-sajt.png"

/**
 * Шапка приложения «Мой Политех»: баннер колледжа на всю ширину.
 * Плашка даты и бейдж группы убраны — дата видна на экране расписания,
 * группа выбирается при первом входе и меняется в настройках.
 */
@Composable
fun MainHeaderBanner(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(MPK_HEADER_BANNER_URL)
                    .crossfade(true)
                    .build(),
                contentDescription = "Баннер Минского политехнического колледжа",
                modifier = Modifier
                    .height(44.dp)
                    .fillMaxWidth(),
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
        }

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
