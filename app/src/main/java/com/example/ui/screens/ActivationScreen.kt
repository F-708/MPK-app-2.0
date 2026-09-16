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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ColorBgMain
import com.example.ui.theme.ColorBorderLight
import com.example.ui.theme.ColorBrandBlue
import com.example.ui.theme.ColorBrandFill
import com.example.ui.theme.ColorSurfaceVariantLight
import com.example.ui.theme.ColorTextBody
import com.example.ui.theme.ColorTextMuted
import com.example.ui.theme.ColorTextTitle
import com.example.ui.util.bouncyClickable
import com.example.util.UnlockToken

/**
 * Экран активации: приложение не работает, пока не введён код разблокировки.
 *
 * Показывается только в обычной версии — админская сборка не блокируется.
 */
@Composable
fun ActivationScreen(
    onActivated: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun tryActivate() {
        if (UnlockToken.isValid(input)) {
            com.example.util.ActivationStore.activate(context)
            onActivated()
        } else {
            error = "Код не подходит или уже устарел. Код действует одну минуту — " +
                "запросите свежий и введите сразу."
            input = ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(ColorBrandFill, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "МП",
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "МОЙ ПОЛИТЕХ",
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = ColorTextTitle
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Приложение активируется по коду. Код выдаёт администратор " +
                "и он действует одну минуту.",
            style = TextStyle(fontSize = 13.sp, color = ColorTextMuted),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { new ->
                // Только цифры и не длиннее кода
                input = new.filter { it.isDigit() }.take(UnlockToken.DIGITS)
                error = null
            },
            singleLine = true,
            placeholder = {
                Text(
                    text = "0".repeat(UnlockToken.DIGITS),
                    style = TextStyle(fontSize = 22.sp, letterSpacing = 6.sp)
                )
            },
            textStyle = TextStyle(
                fontSize = 22.sp,
                letterSpacing = 6.sp,
                fontWeight = FontWeight.Bold,
                color = ColorTextTitle,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ColorBrandBlue,
                unfocusedBorderColor = ColorBorderLight,
                focusedContainerColor = ColorSurfaceVariantLight,
                unfocusedContainerColor = ColorSurfaceVariantLight,
                cursorColor = ColorBrandBlue
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.dp, ColorBorderLight)
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = error ?: "",
                style = TextStyle(fontSize = 12.sp, color = ColorBrandBlue),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        val canSubmit = input.length == UnlockToken.DIGITS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(
                    if (canSubmit) ColorBrandFill else ColorSurfaceVariantLight,
                    androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                )
                .bouncyClickable(enabled = canSubmit) { tryActivate() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "АКТИВИРОВАТЬ",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (canSubmit) androidx.compose.ui.graphics.Color.White else ColorTextMuted
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Код меняется каждую минуту. Если не успели ввести — попросите новый.",
            style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
            textAlign = TextAlign.Center
        )
    }
}
