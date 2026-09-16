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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ColorBgMain
import com.example.ui.theme.ColorBorderLight
import com.example.ui.theme.ColorSurfaceVariantLight
import com.example.ui.theme.ColorTextBody
import com.example.ui.theme.ColorTextMuted
import com.example.ui.theme.ColorTextTitle
import com.example.util.UnlockToken
import kotlinx.coroutines.delay

// Тёмно-золотая палитра админских экранов (объявлена локально — так же,
// как в CollegeScreen.kt и StudentsScreens.kt)
private val ADMIN_ACCENT = androidx.compose.ui.graphics.Color(0xFF7A5C00)
private val ADMIN_BG = androidx.compose.ui.graphics.Color(0xFF2A2313)

/**
 * Вкладка администратора «Как подключить приложение».
 *
 * Показывает код разблокировки, действующий прямо сейчас. Код считается из
 * текущей минуты, поэтому читается тут же, а не берётся из базы или сервера.
 */
@Composable
fun HowToConnectScreen(modifier: Modifier = Modifier) {
    var code by remember { mutableStateOf(UnlockToken.currentCode()) }
    var secondsLeft by remember { mutableIntStateOf(UnlockToken.secondsUntilChange()) }

    // Раз в секунду обновляем и остаток времени, и сам код при смене минуты
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            code = UnlockToken.currentCode()
            secondsLeft = UnlockToken.secondsUntilChange()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "КАК ПОДКЛЮЧИТЬ ПРИЛОЖЕНИЕ",
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = ADMIN_ACCENT
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Студент открывает «Мой Политех» — приложение просит код. " +
                "Продиктуйте ему код ниже, он действует одну минуту. " +
                "Пока код не введён, приложение не работает.",
            style = TextStyle(fontSize = 13.sp, color = ColorTextBody)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Сам код
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ADMIN_BG, RoundedCornerShape(2.dp))
                .border(1.dp, ADMIN_ACCENT, RoundedCornerShape(2.dp))
                .padding(vertical = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = buildAnnotatedString {
                    code.forEachIndexed { index, ch ->
                        if (index > 0) withStyle(SpanStyle(color = ColorTextMuted)) { append("  ") }
                        append(ch)
                    }
                },
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    letterSpacing = 4.sp,
                    color = androidx.compose.ui.graphics.Color.White
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Обновится через $secondsLeft с",
            style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Section("ЧТО НУЖНО ЗНАТЬ")
        Bullet("Код действует одну минуту и меняется сам. Старый код после этого не работает — отзывать отдельно ничего не надо.")
        Bullet("Код одинаков на всех телефонах: он считается из времени, интернет не нужен ни вам, ни студенту.")
        Bullet("Активация сохраняется при обновлении приложения, но слетает при переустановке — тогда понадобится новый код.")
        Bullet("Если код попал не в те руки — просто не давайте следующий. Дальше он всё равно не подойдёт.")
        Bullet("У кого-то код не подходит — почти всегда разошлись часы на телефоне. Пусть включит автоматическую дату и время.")

        Spacer(modifier = Modifier.height(20.dp))

        Section("КОМУ УЖЕ ВЫДАЛИ")
        Surface2 {
            Text(
                text = "Список активаций не ведётся: приложение никуда не сообщает о себе. " +
                    "Кто активирован — знаете только вы, по тому, кому выдавали код.",
                style = TextStyle(fontSize = 13.sp, color = ColorTextBody)
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = ADMIN_ACCENT
        )
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(bottom = 10.dp)) {
        Text(
            text = "•",
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ADMIN_ACCENT)
        )
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            text = text,
            style = TextStyle(fontSize = 13.sp, color = ColorTextBody)
        )
    }
}

@Composable
private fun Surface2(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorSurfaceVariantLight, RoundedCornerShape(2.dp))
            .border(1.dp, ColorBorderLight, RoundedCornerShape(2.dp))
            .padding(12.dp)
    ) {
        content()
    }
}
