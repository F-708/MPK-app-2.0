package com.politex.ui.teacher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.BuildConfig
import com.politex.R
import com.politex.data.model.CollegeBellSchedule
import com.politex.ui.components.AppCard
import com.politex.ui.components.BackButton
import com.politex.ui.theme.ColorActiveBlue
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.TextStylePageTitle
import com.politex.ui.util.bouncyClickable
import com.politex.util.Dow
import java.util.Calendar

// Экран «Ещё» (SPEC §6.1, фичи 11/13/14/17/18). Секции как в студенческих настройках (AppCard).

// Контракт с T4-B (уведомления): ОДИН prefs-файл, ключи ниже. Мы только читаем/пишем.
private const val PREFS = "mpk_teacher_profile"
private const val KEY_SUMMARY = "notif_summary_enabled"
private const val KEY_CHANGES = "notif_changes_enabled"
private const val KEY_REMINDER = "notif_reminder_enabled"
private const val KEY_REMINDER_MIN = "notif_reminder_minutes"

@Composable
fun TeacherMoreScreen(
    profile: TeacherProfile?,
    onPickProfile: () -> Unit = {},
    onOpenBells: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenOemHelp: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val metrics = LocalTeacherMetrics.current
    val context = LocalContext.current
    val email = stringResource(R.string.teacher_feedback_email)
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    Column(
        modifier = modifier.fillMaxSize().background(ColorBgMain).verticalScroll(rememberScrollState()),
    ) {
        Text("Ещё", style = TextStylePageTitle, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp))

        // ——— Профиль ———
        SectionLabel("ПРОФИЛЬ")
        AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column {
                MoreRow(
                    title = profile?.name ?: "Выберите себя",
                    subtitle = if (profile != null) "Нажмите, чтобы сменить" else "Найти себя в списке преподавателей",
                    metrics = metrics.body,
                    tag = "more_profile",
                    onClick = onPickProfile,
                    chevron = true,
                )
                MoreDivider()
                MoreRow(
                    title = "Не нашли себя или заметили неточность?",
                    subtitle = "Напишите нам — $email",
                    metrics = metrics.body,
                    tag = "more_feedback",
                    onClick = { openMailto(context, email) },
                )
            }
        }

        // ——— Уведомления ———
        SectionLabel("УВЕДОМЛЕНИЯ")
        NotifSection(prefs = prefs, metrics = metrics)

        // ——— Звонки / Карта ———
        SectionLabel("СПРАВОЧНИК")
        AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column {
                MoreRow(
                    title = "Расписание звонков",
                    subtitle = "По стандартному расписанию колледжа",
                    metrics = metrics.body,
                    tag = "more_bells",
                    onClick = onOpenBells,
                    chevron = true,
                )
                MoreDivider()
                MoreRow(
                    title = "Карта колледжа",
                    subtitle = "Кабинеты, корпуса, маршруты",
                    metrics = metrics.body,
                    tag = "more_map",
                    onClick = onOpenMap,
                    chevron = true,
                )
            }
        }

        // ——— OEM-справка ———
        SectionLabel("СТАБИЛЬНОСТЬ")
        AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            MoreRow(
                title = "Как запретить системе убивать приложение",
                subtitle = "Xiaomi (MIUI), Samsung (One UI) — автозапуск и батарея",
                metrics = metrics.body,
                tag = "more_oem",
                onClick = onOpenOemHelp,
                chevron = true,
            )
        }

        // ——— О программе ———
        SectionLabel("О ПРОГРАММЕ")
        AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Мой Политех — Преподавателю", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = metrics.body, color = ColorTextTitle))
                Text("Версия ${BuildConfig.VERSION_NAME}", style = TextStyle(fontSize = metrics.chipText, color = ColorTextMuted), modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Неофициальное приложение. Данные из открытых источников guo-mpk.by. Может отставать от приказов о заменах.",
                    style = TextStyle(fontSize = metrics.chipText, color = ColorTextBody),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Удалить мои контакты: $email",
                    style = TextStyle(fontSize = metrics.chipText, color = ColorActiveBlue),
                    modifier = Modifier.heightIn(min = metrics.hitTarget).bouncyClickable { openMailto(context, email) }.testTag("more_delete_contacts"),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NotifSection(
    prefs: android.content.SharedPreferences,
    metrics: TeacherMetrics,
) {
    var summary by remember { mutableStateOf(prefs.getBoolean(KEY_SUMMARY, true)) }
    var changes by remember { mutableStateOf(prefs.getBoolean(KEY_CHANGES, true)) }
    var reminder by remember { mutableStateOf(prefs.getBoolean(KEY_REMINDER, true)) }
    var minutes by remember { mutableIntStateOf(prefs.getInt(KEY_REMINDER_MIN, 10)) }

    fun putBool(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    AppCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column {
            ToggleRow("Утренняя сводка", "Один раз около 07:15", summary, metrics, "notif_summary") {
                summary = it; putBool(KEY_SUMMARY, it)
            }
            MoreDivider()
            ToggleRow("Изменения расписания", "Когда пара изменилась в доке", changes, metrics, "notif_changes") {
                changes = it; putBool(KEY_CHANGES, it)
            }
            MoreDivider()
            ToggleRow("Напоминание до пары", "Смена кабинета или первая пара", reminder, metrics, "notif_reminder") {
                reminder = it; putBool(KEY_REMINDER, it)
            }
            if (reminder) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("За", style = TextStyle(fontSize = metrics.chipText, color = ColorTextMuted))
                    listOf(5, 10, 20).forEach { m ->
                        val selected = minutes == m
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) ColorTopBar else ColorBorderLight),
                            color = if (selected) ColorTopBar else ColorBgMain,
                            modifier = Modifier.heightIn(min = 40.dp).bouncyClickable {
                                minutes = m; prefs.edit().putInt(KEY_REMINDER_MIN, m).apply()
                            }.testTag("notif_min_$m"),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                                Text("$m мин", style = TextStyle(fontSize = metrics.chipText, color = if (selected) Color.White else ColorTextBody))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    metrics: TeacherMetrics,
    tag: String,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = metrics.hitTarget).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = metrics.body, color = ColorTextTitle))
            Text(subtitle, style = TextStyle(fontSize = metrics.chipText, color = ColorTextMuted))
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ColorBrandBlue,
                uncheckedTrackColor = ColorBorderLight,
                uncheckedThumbColor = ColorTextMuted,
            ),
            modifier = Modifier.testTag("${tag}_switch"),
        )
    }
}

@Composable
private fun MoreRow(
    title: String,
    subtitle: String,
    metrics: androidx.compose.ui.unit.TextUnit,
    tag: String,
    onClick: () -> Unit,
    chevron: Boolean = false,
) {
    val m = LocalTeacherMetrics.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = m.hitTarget).bouncyClickable(onClick = onClick).testTag(tag).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = metrics, color = ColorTextTitle))
            Text(subtitle, style = TextStyle(fontSize = m.chipText, color = ColorTextMuted))
        }
        if (chevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = ColorTextMuted, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun MoreDivider() {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(ColorBorderLight))
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ColorTextMuted),
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}

private fun openMailto(context: Context, email: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))) }
}

// ——— Звонки (SPEC фича 13): сетка дня + честный текст, БЕЗ студенческих баннеров ———

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

// ——— OEM-справка (SPEC фича 18) ———

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

