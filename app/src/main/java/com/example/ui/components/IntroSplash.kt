package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.provider.Settings
import com.example.ui.theme.ColorActiveBlue
import com.example.ui.theme.ColorBrandBlue
import com.example.ui.theme.ColorTextMuted
import kotlinx.coroutines.delay

@Composable
fun IntroSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reducedMotion = remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    var appearing by remember { mutableStateOf(false) }
    var progressPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (reducedMotion) {
            delay(400)
            onFinished()
            return@LaunchedEffect
        }
        appearing = true
        delay(350)
        progressPlaying = true
        delay(1000)
        onFinished()
    }

    val emblemScale by animateFloatAsState(
        targetValue = if (appearing) 1f else 0.6f,
        animationSpec = tween(durationMillis = 350),
        label = "IntroScale"
    )
    val emblemAlpha by animateFloatAsState(
        targetValue = if (appearing) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "IntroAlpha"
    )
    val barProgress by animateFloatAsState(
        targetValue = if (progressPlaying) 1f else 0.05f,
        animationSpec = tween(durationMillis = 950, easing = LinearEasing),
        label = "IntroBar"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(emblemScale)
                    .alpha(emblemAlpha)
            ) {
                CollegePorticoIcon(color = ColorBrandBlue, modifier = Modifier.fillMaxSize())
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "МОЙ ПОЛИТЕХ",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                color = ColorBrandBlue,
                modifier = Modifier.alpha(emblemAlpha)
            )

            Text(
                text = "Минский государственный политехнический колледж",
                fontSize = 11.sp,
                color = ColorTextMuted,
                modifier = Modifier
                    .alpha(emblemAlpha)
                    .padding(horizontal = 32.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 64.dp, vertical = 64.dp)
                .fillMaxWidth()
                .height(3.dp)
                .background(Color(0xFFE2E8F0))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barProgress)
                    .height(3.dp)
                    .background(ColorActiveBlue)
            )
        }
    }
}

@Composable
fun IntroSplashOverlay(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(true) }
    if (visible) {
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = modifier
        ) {
            IntroSplash(onFinished = { visible = false })
        }
    }
}
