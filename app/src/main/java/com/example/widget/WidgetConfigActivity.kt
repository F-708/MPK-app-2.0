package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ColorBgMain
import com.example.ui.theme.ColorBorderLight
import com.example.ui.theme.ColorBrandBlue
import com.example.ui.theme.ColorBrandFill
import com.example.ui.theme.ColorTextBody
import com.example.ui.theme.ColorTextMuted
import com.example.ui.theme.ColorTextTitle
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.bouncyClickable

/**
 * Общий экран настройки для всех виджетов приложения.
 *
 * Раньше настройка была только у «До звонка» и только про цвет. Теперь у каждого
 * виджета при добавлении спрашивается тема, а если у виджета есть особенности —
 * ещё и они (например, до чего считать отсчёт или показывать ли кабинет).
 *
 * Какой именно виджет настраивается, определяется по его appWidgetId: система
 * сама сообщает провайдера, поэтому отдельный экран на каждый виджет не нужен.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val kind = WidgetKind.of(this, appWidgetId)

        setContent {
            MyApplicationTheme {
                Surface(color = ColorBgMain, modifier = Modifier.fillMaxSize()) {
                    var style by remember { mutableStateOf(WidgetStyle.LIGHT) }
                    var countdownMode by remember { mutableStateOf(CountdownMode.GROUP_LESSONS) }
                    var showRoom by remember { mutableStateOf(true) }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                    ) {
                        Text(
                            text = kind.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorTextTitle
                        )
                        Text(
                            text = kind.hint,
                            fontSize = 13.sp,
                            color = ColorTextMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))
                        SectionTitle("Тема оформления")

                        WidgetStyle.entries.forEach { option ->
                            val isSelected = style == option
                            OptionRow(
                                selected = isSelected,
                                title = option.title,
                                description = null,
                                onClick = { style = option },
                                leading = {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(Color(option.backgroundColor), RoundedCornerShape(2.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "12",
                                            color = Color(option.mainColor),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            )
                        }

                        if (kind == WidgetKind.COUNTDOWN) {
                            Spacer(modifier = Modifier.height(18.dp))
                            SectionTitle("До чего считать")
                            CountdownMode.entries.forEach { option ->
                                OptionRow(
                                    selected = countdownMode == option,
                                    title = option.title,
                                    description = option.description,
                                    onClick = { countdownMode = option }
                                )
                            }
                        }

                        if (kind.showRoomOption) {
                            Spacer(modifier = Modifier.height(18.dp))
                            SectionTitle("Дополнительно")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Показывать кабинет",
                                        fontSize = 14.sp,
                                        color = ColorTextBody
                                    )
                                    Text(
                                        text = "Если не нужен — строки станут короче",
                                        fontSize = 12.sp,
                                        color = ColorTextMuted
                                    )
                                }
                                Switch(checked = showRoom, onCheckedChange = { showRoom = it })
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ColorBrandFill,
                            modifier = Modifier
                                .fillMaxWidth()
                                .bouncyClickable {
                                    WidgetStyle.save(this@WidgetConfigActivity, appWidgetId, style)
                                    WidgetOptions.setShowRoom(this@WidgetConfigActivity, appWidgetId, showRoom)
                                    if (kind == WidgetKind.COUNTDOWN) {
                                        CountdownMode.save(this@WidgetConfigActivity, appWidgetId, countdownMode)
                                    }
                                    WidgetUpdateHelper.updateOne(this@WidgetConfigActivity, appWidgetId)
                                    WidgetAlarm.scheduleNext(this@WidgetConfigActivity)

                                    setResult(
                                        RESULT_OK,
                                        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                    )
                                    finish()
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "ДОБАВИТЬ ВИДЖЕТ",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Какой виджет настраивается — определяется по провайдеру, запустившему экран. */
enum class WidgetKind(
    val title: String,
    val hint: String,
    val providerClass: Class<*>,
    val showRoomOption: Boolean
) {
    COUNTDOWN(
        "Виджет «До звонка»",
        "Сколько осталось до звонка. Тема и до чего считать отсчёт",
        BellCountdownWidgetProvider::class.java,
        false
    ),
    NOW_NEXT(
        "Виджет «Сейчас и дальше»",
        "Что идёт сейчас и что будет следующим",
        NowNextWidgetProvider::class.java,
        true
    ),
    TODAY(
        "Виджет «Расписание на сегодня»",
        "Весь день списком: время, предмет, кабинет",
        TodayScheduleWidgetProvider::class.java,
        true
    ),
    BELLS(
        "Виджет «Звонки»",
        "Расписание звонков с подсветкой текущего урока",
        BellsWidgetProvider::class.java,
        false
    );

    companion object {
        fun of(context: android.content.Context, appWidgetId: Int): WidgetKind {
            val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)
            val providerName = info?.provider?.className
            return entries.firstOrNull { it.providerClass.name == providerName } ?: COUNTDOWN
        }
    }
}

/** Дополнительные переключатели виджета, кроме темы. */
object WidgetOptions {

    private const val PREF_SHOW_ROOM = "widget_show_room_"

    fun showRoom(context: android.content.Context, appWidgetId: Int): Boolean =
        WidgetUpdateHelper.getPrefs(context).getBoolean(PREF_SHOW_ROOM + appWidgetId, true)

    fun setShowRoom(context: android.content.Context, appWidgetId: Int, value: Boolean) {
        WidgetUpdateHelper.getPrefs(context).edit()
            .putBoolean(PREF_SHOW_ROOM + appWidgetId, value)
            .apply()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = ColorTextTitle
    )
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun OptionRow(
    selected: Boolean,
    title: String,
    description: String?,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (selected) ColorBrandBlue else ColorBorderLight),
        color = ColorBgMain,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .bouncyClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                leading()
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = ColorTextBody
                )
                if (description != null) {
                    Text(text = description, fontSize = 12.sp, color = ColorTextMuted)
                }
            }
        }
    }
}
