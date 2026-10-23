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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import com.politex.ui.components.SubPageHeader
import com.politex.ui.theme.ColorActiveBlue
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorSuccessText
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
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
            .background(ColorBgMain)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        SubPageHeader(
            title = "Код доступа",
            onBack = onBack,
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
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

            Spacer(modifier = Modifier.height(32.dp))

            Box(contentAlignment = Alignment.Center) {
                Canvas(
                    modifier =
                    Modifier
                        .size(96.dp)
                        .testTag("access_code_ring"),
                ) {
                    val strokeWidth = 8.dp.toPx()
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
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

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier =
                Modifier
                    .size(Tokens.IconHit)
                    .background(ColorBrandFill, RoundedCornerShape(2.dp))
                    .bouncyClickable {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    }.testTag("access_code_copy_btn"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Скопировать",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (copied) {
                Spacer(modifier = Modifier.height(8.dp))
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
        }
    }
}
