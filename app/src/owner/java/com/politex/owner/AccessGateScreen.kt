package com.politex.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorErrorText
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.TextStylePageTitle
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable

@Composable
fun AccessGateScreen(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var code by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(ColorBgMain)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
            Modifier
                .size(72.dp)
                .background(ColorBrandFill, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "КОД ДОСТУПА",
            style = TextStylePageTitle,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Запросите код у владельца приложения",
            style = TextStyle(fontSize = 13.sp, color = ColorTextMuted),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { value ->
                code = value.filter { it.isDigit() }.take(6)
                showError = false
            },
            singleLine = true,
            shape = RoundedCornerShape(2.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                letterSpacing = 10.sp,
                color = ColorTextTitle,
                textAlign = TextAlign.Center,
            ),
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .heightIn(min = 64.dp)
                .testTag("access_code_input")
                .focusRequester(focusRequester),
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (showError) {
            Text(
                text = "Код неверен или устарел — запросите новый",
                style =
                TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = ColorErrorText,
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("access_code_error"),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        val canSubmit = code.length == 6
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = if (canSubmit) ColorBrandFill else ColorSurfaceHighlight,
            modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Tokens.ButtonHeight)
                .bouncyClickable(enabled = canSubmit) {
                    if (AccessCode.isCurrentCodeValid(code)) {
                        AccessUnlockStore.unlock(context)
                        onUnlocked()
                    } else {
                        showError = true
                    }
                }.testTag("access_code_submit"),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(vertical = 13.dp),
            ) {
                Text(
                    text = "ВОЙТИ",
                    style =
                    TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp,
                        color = if (canSubmit) Color.White else ColorTextMuted,
                    ),
                )
            }
        }
    }
}
