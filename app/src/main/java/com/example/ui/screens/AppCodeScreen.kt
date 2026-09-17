package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.util.bouncyClickable
import com.example.util.UnlockToken
import kotlinx.coroutines.delay

// Тёмно-золотая палитра админских экранов (объявлена локально — так же,
// как в CollegeScreen.kt и StudentsScreens.kt)
private val ADMIN_BG = Color(0xFF2A2313)
private val ADMIN_BG_DEEP = Color(0xFF1B160C)
private val ADMIN_GOLD = Color(0xFFE8C55A)

/**
 * Экран администратора «Код приложения» — на весь экран, без баннера и вкладок.
 *
 * Показывается вместо основного интерфейса (см. MainScreen), потому что это
 * экран, который читают с другого телефона: всё лишнее на нём мешает.
 * Остаётся только код, стрелка назад и полоса, показывающая, сколько кода ещё жить.
 */
@Composable
fun AppCodeScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var code by remember { mutableStateOf(UnlockToken.currentCode()) }
    var progress by remember { mutableFloatStateOf(0f) }

    // Обновляем полосу 20 раз в секунду — плавно, но без перерисовки каждый кадр.
    // Код перечитываем, когда минута перевалила.
    LaunchedEffect(Unit) {
        while (true) {
            delay(50)
            val now = System.currentTimeMillis()
            progress = (now % 60_000L) / 60_000f
            val fresh = UnlockToken.currentCode(now)
            if (fresh != code) code = fresh
        }
    }

    // Чем меньше кода осталось, тем сильнее он гаснет — видно, что время уходит,
    // без единой строчки текста.
    val remaining = 1f - progress
    val codeAlpha = 0.55f + 0.45f * (remaining * remaining)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ADMIN_BG)
    ) {
        // Стрелка назад — единственный элемент управления
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, ADMIN_GOLD.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                    .bouncyClickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = ADMIN_GOLD,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Код по центру всего оставшегося пространства
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(bottom = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ADMIN_GOLD.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                    .padding(vertical = 44.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = code,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 58.sp,
                        letterSpacing = 10.sp,
                        color = ADMIN_GOLD.copy(alpha = codeAlpha)
                    )
                )
            }
        }

        // Полоса жизни кода: заполняется слева направо и истекает вместе с минутой
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(ADMIN_BG_DEEP)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(ADMIN_GOLD)
            )
        }
        Spacer(modifier = Modifier.height(0.dp))
    }
}
