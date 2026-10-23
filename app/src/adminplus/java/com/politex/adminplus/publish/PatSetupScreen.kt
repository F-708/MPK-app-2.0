package com.politex.adminplus.publish

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.ui.components.AppCard
import com.politex.ui.components.SubPageHeader
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorDangerFill
import com.politex.ui.theme.ColorErrorText
import com.politex.ui.theme.ColorSuccessBg
import com.politex.ui.theme.ColorSuccessText
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextDisabled
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed class PatStatus {
    object Idle : PatStatus()

    object Saved : PatStatus()

    object Removed : PatStatus()

    data class Invalid(
        val code: Int,
    ) : PatStatus()

    object Network : PatStatus()

    object Malformed : PatStatus()

    object SaveError : PatStatus()
}

internal fun patStatusText(status: PatStatus): String =
    when (status) {
        PatStatus.Idle -> ""
        PatStatus.Saved -> "Токен сохранён — публикация доступна"
        PatStatus.Removed -> "Токен удалён"
        is PatStatus.Invalid ->
            if (status.code == 403) {
                "Токен не подошёл (403): нужен доступ к репозиторию и права Contents: Read and write"
            } else {
                "Токен недействителен (код ${status.code}) — выпустите новый"
            }
        PatStatus.Network -> "Нет связи с GitHub — проверьте интернет и повторите"
        PatStatus.Malformed -> "Токен выглядит неполным — скопируйте его целиком"
        PatStatus.SaveError -> "Не удалось сохранить токен на устройстве — повторите"
    }

@Composable
fun PatSetupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<PatStatus>(PatStatus.Idle) }
    var hasToken by remember { mutableStateOf(GithubTokenStore.hasToken(context)) }

    LaunchedEffect(status) {
        if (status != PatStatus.Idle && status !is PatStatus.Saved) {
            delay(6000)
            if (status !is PatStatus.Saved) status = PatStatus.Idle
        }
    }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(ColorBgMain)
            .navigationBarsPadding()
            .imePadding()
            .testTag("pat_setup_screen"),
    ) {
        SubPageHeader(
            title = "Токен публикации",
            subtitle = if (hasToken) "Токен сохранён на этом устройстве" else "Нужен для отправки правок всем",
            onBack = onBack,
        )

        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "КАК ПОЛУЧИТЬ ТОКЕН (ОДИН РАЗ)",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = Tokens.SectionHeader, color = ColorBrandBlue),
                    )
                    val steps =
                        listOf(
                            "1. Откройте github.com → Settings → Developer settings",
                            "2. Fine-grained personal access tokens → Generate new token",
                            "3. Repository access: Only select repositories → moy-polytech",
                            "4. Permissions → Repository permissions → Contents: Read and write",
                            "5. Нажмите Generate token и скопируйте токен (github_pat_…)",
                        )
                    steps.forEach { step ->
                        Text(text = step, style = TextStyle(fontSize = 12.sp, color = ColorTextTitle))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Токен хранится зашифрованным только на этом устройстве и передаётся только в GitHub. В логах и коде он не появляется.",
                        style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                    )
                }
            }

            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    status = PatStatus.Idle
                },
                label = { Text("Fine-grained PAT") },
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(2.dp),
                colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = ColorSurfaceVariantLight,
                    unfocusedContainerColor = ColorSurfaceVariantLight,
                    focusedIndicatorColor = ColorBrandBlue,
                    unfocusedIndicatorColor = ColorBorderLight,
                ),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Tokens.HitTarget)
                    .testTag("pat_token_input"),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hasToken) {
                    PatButton(
                        label = "Удалить",
                        danger = true,
                        enabled = !busy,
                        testTag = "pat_delete",
                        modifier = Modifier.weight(1f),
                    ) {
                        GithubTokenStore.clear(context)
                        hasToken = false
                        input = ""
                        status = PatStatus.Removed
                    }
                }
                PatButton(
                    label = if (busy) "Проверка…" else "Проверить и сохранить",
                    enabled = !busy && input.isNotBlank(),
                    testTag = "pat_save",
                    modifier = Modifier.weight(1f),
                    showProgress = busy,
                ) {
                    busy = true
                    status = PatStatus.Idle
                    scope.launch(Dispatchers.IO) {
                        val outcome = checkAndSave(context, input)
                        withContext(Dispatchers.Main) {
                            busy = false
                            status = outcome
                            if (outcome == PatStatus.Saved) {
                                hasToken = true
                                input = ""
                            }
                        }
                    }
                }
            }

            val statusText = patStatusText(status)
            if (statusText.isNotBlank()) {
                val isError = status !is PatStatus.Saved && status !is PatStatus.Removed
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (isError) ColorSurfaceHighlight else ColorSuccessBg,
                            RoundedCornerShape(2.dp),
                        ).padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("pat_status"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = statusText,
                        style =
                        TextStyle(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = if (isError) ColorErrorText else ColorSuccessText,
                        ),
                    )
                }
            }
        }
    }
}

internal suspend fun checkAndSave(
    context: Context,
    input: String,
): PatStatus {
    val token = input.trim()
    if (!GithubTokenStore.isPlausibleToken(token)) return PatStatus.Malformed
    return when (val check = AdminPlusPublishDeps.publisherFactory(context).verifyToken(token)) {
        TokenCheck.Valid -> if (GithubTokenStore.save(context, token)) PatStatus.Saved else PatStatus.SaveError
        is TokenCheck.Invalid -> PatStatus.Invalid(check.code)
        TokenCheck.NetworkError -> PatStatus.Network
    }
}

@Composable
private fun PatButton(
    label: String,
    testTag: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    showProgress: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor =
        when {
            !enabled -> ColorTextDisabled
            danger -> Color.White
            else -> Color.White
        }
    val container =
        when {
            !enabled -> ColorSurfaceHighlight
            danger -> ColorDangerFill
            else -> ColorBrandFill
        }
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = container,
        border =
        androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                !enabled -> ColorBorderLight
                danger -> ColorDangerFill
                else -> ColorBrandBlue
            },
        ),
        modifier =
        modifier
            .heightIn(min = Tokens.ButtonHeight)
            .bouncyClickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp).testTag("pat_progress"),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = if (danger) Icons.Default.Delete else Icons.Default.Save,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label.uppercase(),
                maxLines = 1,
                softWrap = false,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = Tokens.ButtonText, color = contentColor),
            )
        }
    }
}
