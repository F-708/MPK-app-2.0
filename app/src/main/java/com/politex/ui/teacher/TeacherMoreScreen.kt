package com.politex.ui.teacher

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.BuildConfig
import com.politex.data.model.CollegeBellSchedule
import com.politex.ui.components.AppCard
import com.politex.ui.components.BackButton
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.TextStylePageTitle
import com.politex.ui.util.bouncyClickable
import com.politex.util.Dow
import java.util.Calendar

// Экран «Ещё» teacher-приложения = КОПИЯ студенческого «Другое» (CollegeScreen.OtherMenu:223).
// Порядок/состав/стиль MenuCard — 1-в-1 как у студентов, минус «Моя специальность», «Расписание
// звонков» (стало вкладкой «График»), «База данных учащихся» (teacher flavor не admin).
// Сверху — строка профиля (якорь личности, тап → onPickProfile = сброс TeacherProfileStore).

@Composable
fun TeacherMoreScreen(
    profile: TeacherProfile?,
    onPickProfile: () -> Unit = {},
    // onOpenBells/onOpenOemHelp сохранены для совместимости сигнатуры с TeacherNav.kt (агент A);
    // в меню «Ещё» строк «Расписание звонков»/OEM-справки больше нет (решение консилиума §3.2/§10 п.5).
    onOpenBells: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenOemHelp: () -> Unit = {},
    // TR5: новые цели меню — подключает агент A подстраницами в TeacherNav (дефолты = no-op).
    onOpenGroupSchedule: () -> Unit = {},
    onOpenTeachers: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().background(ColorBgMain),
    ) {
        Header(title = "Ещё", subtitle = "Профиль, преподаватели и настройки")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.AccountCircle, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = profile?.name ?: "Выберите себя",
                    subtitle = if (profile != null) "Нажмите, чтобы сменить преподавателя" else "Найти себя в списке преподавателей",
                    onClick = onPickProfile,
                    testTag = "more_profile",
                )
            }
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.LocationOn, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Карта колледжа",
                    subtitle = "Планы этажей и поиск кабинета",
                    onClick = onOpenMap,
                    testTag = "more_map",
                )
            }
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.DateRange, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Найти группу",
                    subtitle = "Расписание любой группы колледжа",
                    onClick = onOpenGroupSchedule,
                    testTag = "more_group",
                )
            }
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Преподаватели",
                    subtitle = "Поиск по базе, избранное и фотографии",
                    onClick = onOpenTeachers,
                    testTag = "more_teachers",
                )
            }
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Настройки",
                    subtitle = "Профиль и уведомление «до звонка»",
                    onClick = onOpenSettings,
                    testTag = "more_settings",
                )
            }
        }
    }
}

// ——— Клоны приватных MenuCard/Header из CollegeScreen.kt (1-в-1, + необязательный testTag) ———

@Composable
private fun MenuCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String? = null,
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(ColorBrandFill, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ColorTextTitle),
                )
                Text(
                    text = subtitle,
                    style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                    maxLines = 2,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = ColorTextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun Header(
    title: String,
    subtitle: String,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text = title, style = TextStylePageTitle, maxLines = 1)
        Text(
            text = subtitle,
            style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ColorBrandBlue),
            maxLines = 1,
        )
    }
}

// ——— Звонки (подстраница TeacherNav.Bells; в меню «Ещё» строки нет — вкладка «График») ———

@Composable
fun TeacherBellsPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalTeacherMetrics.current
    val dow = remember { Dow.of(Calendar.getInstance()) }
    val bells = remember(dow) { CollegeBellSchedule.getBellsForDay(dow) }
    val dowTitle = remember(dow) { dowTitle(dow) }

    Column(modifier = modifier.fillMaxSize().background(ColorBgMain)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack = onBack, testTag = "bells_back")
            Spacer(Modifier.width(10.dp))
            Text("Звонки · $dowTitle", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ColorTextTitle))
        }
        Text(
            "Звонок по стандартному расписанию колледжа. Сокращённые дни и переносы приложение не определяет — сверяйтесь с объявлением учебной части.",
            style = TextStyle(fontSize = metrics.chipText, color = ColorTextMuted),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp).testTag("bells_honest_text"),
        )
        Spacer(Modifier.height(6.dp))
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            bells.forEach { bell ->
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${bell.lessonNumber}",
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ColorBrandBlue),
                            modifier = Modifier.width(34.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bell.title, style = TextStyle(fontSize = metrics.body, color = ColorTextBody))
                            if (bell.breakAfterMinutes > 0) {
                                Text("перемена ${bell.breakAfterMinutes} мин", style = TextStyle(fontSize = metrics.chipText, color = ColorTextMuted))
                            }
                        }
                        Text(bell.displayRange, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ColorTextTitle))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun dowTitle(dow: Int): String =
    when (dow) {
        1 -> "понедельник"
        2 -> "вторник"
        3 -> "среда"
        4 -> "четверг"
        5 -> "пятница"
        6 -> "суббота"
        else -> "воскресенье"
    }

// ——— OEM-справка (подстраница TeacherNav.OemHelp; в меню «Ещё» строки нет — §10 п.5) ———

@Composable
fun TeacherOemHelpPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalTeacherMetrics.current
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxSize().background(ColorBgMain).verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack = onBack, testTag = "oem_back")
            Spacer(Modifier.width(10.dp))
            Text("Чтобы приложение не закрывалось", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ColorTextTitle))
        }
        Text(
            "Экономия батареи телефона может останавливать фоновую синхронизацию — и расписание перестаёт обновляться. Настройте один раз:",
            style = TextStyle(fontSize = metrics.body, color = ColorTextBody),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        )
        OemCard(
            title = "Xiaomi / MIUI",
            steps = listOf(
                "Настройки → Приложения → Все приложения → «Мой Политех» → Автозапуск — включить.",
                "Настройки → Батарея → (три точки) → Экономия энергии — «Нет ограничений».",
            ),
        )
        OemCard(
            title = "Samsung / One UI",
            steps = listOf(
                "Настройки → Батарея → Спящие приложения — убрать «Мой Политех» из спящих/глубокого сна.",
                "Настройки → Батарея → Использование приложений → «Мой Политех» → Неограниченно.",
            ),
        )
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = ColorTopBar,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).heightIn(min = metrics.hitTarget).bouncyClickable {
                runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
            }.testTag("oem_open_battery"),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("Открыть настройки батареи", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = metrics.buttonText)
            }
        }
        Text(
            "Если после настройки обновления всё равно не приходят — напишите нам, укажите модель телефона.",
            style = TextStyle(fontSize = metrics.chipText, color = ColorTextMuted),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OemCard(
    title: String,
    steps: List<String>,
) {
    val metrics = LocalTeacherMetrics.current
    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = metrics.body, color = ColorTextTitle), modifier = Modifier.padding(bottom = 6.dp))
            steps.forEachIndexed { i, step ->
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text("${i + 1}.", style = TextStyle(fontSize = metrics.body, color = ColorBrandBlue, fontWeight = FontWeight.Bold), modifier = Modifier.width(22.dp))
                    Text(step, style = TextStyle(fontSize = metrics.body, color = ColorTextBody), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ——— «О программе»: версия + дисклеймер (используется TeacherSettingsScreen) ———

internal const val TEACHER_ABOUT_DISCLAIMER =
    "Неофициальное приложение. Данные из открытых источников guo-mpk.by. Может отставать от приказов о заменах."

internal fun teacherAboutVersion(): String = BuildConfig.VERSION_NAME
