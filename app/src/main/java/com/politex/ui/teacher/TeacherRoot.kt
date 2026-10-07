package com.politex.ui.teacher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorTextTitle

@Composable
fun TeacherRoot(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf(TeacherProfileStore.get(context)) }
    var skipped by remember { mutableStateOf(false) }

    TeacherTheme {
        if (TeacherOnboardingLogic.showOnboarding(profile, skipped)) {
            TeacherOnboardingScreen(
                onProfileSaved = { profile = TeacherProfileStore.get(context) },
                onSkipAll = {
                    skipped = true
                    profile = TeacherProfileStore.get(context)
                },
            )
        } else {
            // заглушка до экрана «Мой день» (делает агент A)
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (profile != null) "Профиль выбран: ${profile?.name}" else "Мой Политех — Преподавателю",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ColorTextTitle,
                    )
                    if (profile != null) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                TeacherProfileStore.set(context, null)
                                profile = null
                                skipped = false
                            },
                            modifier = Modifier.heightIn(min = LocalTeacherMetrics.current.hitTarget),
                        ) {
                            Text("Сменить", color = ColorBrandBlue, fontSize = LocalTeacherMetrics.current.buttonText)
                        }
                    }
                }
            }
        }
    }
}
