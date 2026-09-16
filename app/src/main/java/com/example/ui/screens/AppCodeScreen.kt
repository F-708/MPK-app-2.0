package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
private val ADMIN_GOLD = Color(0xFFE8C55A)
private val ADMIN_HINT = Color(0xFF8A7A55)

/**
 * Экран администратора «Код приложения».
 *
 * Только код и стрелка назад — читается с другого телефона, поэтому всё лишнее
 * убрано, а фон тёмный, как у плашки с кодом в остальных админских экранах.
 */
@Composable
fun AppCodeScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var code by remember { mutableStateOf(UnlockToken.currentCode()) }

    // Раз в секунду перечитываем код: он меняется на границе минуты
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            code = UnlockToken.currentCode()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ADMIN_BG)
    ) {
        // Стрелка назад — единственный элемент интерфейса
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, ADMIN_GOLD, RoundedCornerShape(2.dp))
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

        // Код по центру всего оставшегося экрана
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ADMIN_GOLD, RoundedCornerShape(2.dp))
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = code,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 56.sp,
                            letterSpacing = 8.sp,
                            color = ADMIN_GOLD
                        )
                    )
                }
                Spacer(modifier = Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Обновится через ${UnlockToken.secondsUntilChange()} с",
                        style = TextStyle(fontSize = 11.sp, color = Color(0xFF8A7A55))
                    )
                }
            }
        }
    }
}
