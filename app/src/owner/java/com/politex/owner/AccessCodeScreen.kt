package com.politex.owner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.ui.components.AppCard
import com.politex.ui.components.BrandActionButton
import com.politex.ui.components.SubPageHeader
import com.politex.ui.theme.ColorActiveBlue
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorSuccessText
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import kotlinx.coroutines.delay

@Composable
fun AccessCodeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    var nowSeconds by remember { mutableStateOf(AccessClock.nowSeconds()) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowSeconds = AccessClock.nowSeconds()
        }
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    val code = remember(nowSeconds) { AccessCode.generateCode(nowSeconds) }
    val secondsLeft = remember(nowSeconds) { AccessCode.secondsLeftInStep(nowSeconds) }
    val ringSweep by animateFloatAsState(
        targetValue = secondsLeft / AccessCode.STEP_SECONDS.toFloat(),
        animationSpec = tween(300),
        label = "accessCodeRingSweep",
    )

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(ColorBgMain),
    ) {
        SubPageHeader(
            title = "Код доступа",
            subtitle = "Вход в версию для администрации",
            onBack = onBack,
        )

        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            AnimatedContent(
                targetState = code,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
                label = "accessCodeDigits",
                modifier = Modifier.testTag("access_code_digits"),
            ) { value ->
                Text(
                    text = value,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp,
                    color = ColorTextTitle,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Обновляется каждые 30 секунд",
                style = TextStyle(fontSize = 13.sp, color = ColorTextMuted),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Box(contentAlignment = Alignment.Center) {
                Canvas(
                    modifier =
                    Modifier
                        .size(96.dp)
                        .testTag("access_code_ring"),
                ) {
                    val strokeWidth = 8.dp.toPx()
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f)
                    drawArc(
                        color = ColorSurfaceHighlight,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = ColorActiveBlue,
                        startAngle = -90f,
                        sweepAngle = 360f * ringSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
                Text(
                    text = "$secondsLeft с",
                    style =
                    TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = ColorBrandBlue,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            AppCard(
                modifier = Modifier.fillMaxWidth(),
                color = ColorSurfaceHighlight,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Код действителен ~30 секунд после смены",
                        style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                    )
                    Text(
                        text = "Основан на времени этого устройства",
                        style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            BrandActionButton(
                icon = Icons.Default.ContentCopy,
                label = "Скопировать",
                testTag = "access_code_copy_btn",
                filled = true,
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                },
            )

            if (copied) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Скопировано",
                    style =
                    TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ColorSuccessText,
                    ),
                    modifier = Modifier.testTag("access_code_copy_status"),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
