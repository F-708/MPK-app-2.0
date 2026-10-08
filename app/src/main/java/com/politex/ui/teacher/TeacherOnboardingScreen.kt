package com.politex.ui.teacher

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.R
import com.politex.data.repository.Teacher
import com.politex.ui.components.AppCard
import com.politex.ui.components.SearchBar
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TeacherOnboardingScreen(
    onProfileSaved: () -> Unit,
    onSkipAll: () -> Unit,
) {
    val context = LocalContext.current
    val metrics = LocalTeacherMetrics.current
    var step by remember { mutableStateOf(0) }

    val skip: () -> Unit = {
        if (step >= 2) onSkipAll() else step++
    }

    TeacherTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    color = ColorTopBar,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onSkipAll,
                    modifier = Modifier.heightIn(min = metrics.hitTarget),
                ) {
                    Text("Пропустить", color = ColorBrandBlue, fontSize = metrics.buttonText)
                }
            }
            when (step) {
                0 -> WelcomeStep(
                    body = metrics.body,
                    onNext = { step = 1 },
                    onSkip = skip,
                )
                1 -> SearchStep(
                    body = metrics.body,
                    onChosen = { teacher ->
                        TeacherProfileStore.set(context, TeacherOnboardingLogic.profileOf(teacher))
                        onProfileSaved()
                    },
                    onSkip = skip,
                )
                else -> WidgetStep(
                    body = metrics.body,
                    onDone = onSkipAll,
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    body: androidx.compose.ui.unit.TextUnit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val metrics = LocalTeacherMetrics.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Добро пожаловать!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Приложение знает ваше расписание с сайта колледжа. Замены из чата учебной части в нём не появятся — пометьте их одной кнопкой, и виджет покажет правильно.",
                    fontSize = body,
                    color = ColorTextBody,
                )
                Text(
                    "Неофициальное приложение. Данные из открытых источников guo-mpk.by",
                    fontSize = metrics.chipText,
                    color = ColorTextMuted,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        OnboardButton("Продолжить", onNext)
        OnboardButton("Пропустить", onSkip, filled = false)
    }
}

@Composable
private fun SearchStep(
    body: androidx.compose.ui.unit.TextUnit,
    onChosen: (Teacher) -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    val metrics = LocalTeacherMetrics.current
    val teachers by produceState(initialValue = emptyList<Teacher>(), key1 = context) {
        value = withContext(Dispatchers.IO) { com.politex.AppGraph.teachersRepository.loadTeachers() }
    }
    var query by remember { mutableStateOf("") }
    val filtered = remember(teachers, query) { TeacherOnboardingLogic.filterTeachers(teachers, query) }
    var confirming by remember { mutableStateOf<Teacher?>(null) }

    confirming?.let { selected ->
        val candidates = remember(teachers, selected) {
            TeacherOnboardingLogic.confirmCandidates(selected, teachers)
        }
        var index by remember(selected) { mutableStateOf(0) }
        val candidate = candidates[index]
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (candidates.size > 1) {
                Text(
                    "Найдено ${candidates.size} преподавателя с этой фамилией — выберите себя",
                    fontSize = metrics.chipText,
                    color = ColorTextMuted,
                )
            }
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(candidate.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
                    if (candidate.position.isNotBlank()) {
                        Text(candidate.position, fontSize = body, color = ColorTextBody)
                    }
                    if (candidate.subjects.isNotBlank()) {
                        Text(candidate.subjects, fontSize = metrics.chipText, color = ColorTextMuted)
                    }
                    Text("Это вы?", fontSize = body, fontWeight = FontWeight.SemiBold, color = ColorTextTitle)
                }
            }
            OnboardButton("Да, это я", onClick = { onChosen(candidate) })
            OnboardButton("Нет", filled = false, onClick = {
                if (index < candidates.size - 1) index++ else confirming = null
            })
            Spacer(Modifier.weight(1f))
            OnboardButton("Пропустить", onSkip, filled = false)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Найдите себя в списке", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
        SearchBar(
            value = query,
            onValueChange = { query = it },
            placeholder = "Фамилия, имя или предмет",
            testTag = "teacher_onboarding_search",
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { it.name + it.position + it.room }) { t ->
                AppCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = metrics.hitTarget)
                        .clickable { confirming = t },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(t.name, fontSize = body, fontWeight = FontWeight.SemiBold, color = ColorTextTitle)
                        if (t.position.isNotBlank()) {
                            Text(t.position, fontSize = metrics.chipText, color = ColorTextMuted)
                        }
                        if (t.subjects.isNotBlank()) {
                            // без overflow текст обрывался на 2-й строке с висячей запятой
                            Text(t.subjects, fontSize = metrics.chipText, color = ColorTextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item {
                val email = stringResource(R.string.teacher_feedback_email)
                Text(
                    "Не нашли себя? Напишите нам",
                    fontSize = body,
                    color = ColorBrandBlue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = metrics.hitTarget)
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")),
                                )
                            }
                        },
                )
            }
        }
        OnboardButton("Пропустить", onSkip, filled = false)
    }
}


@Composable
private fun WidgetStep(
    body: androidx.compose.ui.unit.TextUnit,
    onDone: () -> Unit,
) {
    val metrics = LocalTeacherMetrics.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppCard(color = ColorBgMain, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .heightIn(min = metrics.hitTarget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Добавьте виджет", fontSize = metrics.chipText, fontWeight = FontWeight.SemiBold, color = ColorBrandBlue)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Долгое нажатие на домашний экран → «Виджеты» → «Мой Политех» — расписание всегда под рукой",
                    fontSize = body,
                    color = ColorTextBody,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        OnboardButton("Готово", onDone)
    }
}

@Composable
private fun OnboardButton(
    label: String,
    onClick: () -> Unit,
    filled: Boolean = true,
) {
    val metrics = LocalTeacherMetrics.current
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = metrics.hitTarget),
    ) {
        Text(
            label,
            fontSize = metrics.buttonText,
            fontWeight = FontWeight.SemiBold,
            color = if (filled) ColorBrandBlue else ColorTextMuted,
        )
    }
}
