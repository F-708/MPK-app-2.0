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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
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
 * Настройка виджета «До звонка» при добавлении: выбор цвета фона.
 */
class BellCountdownConfigActivity : ComponentActivity() {

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

        var selected by mutableStateOf(WidgetStyle.LIGHT)
        var mode by mutableStateOf(CountdownMode.GROUP_LESSONS)

        setContent {
            MyApplicationTheme {
                Surface(color = ColorBgMain, modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Виджет «До звонка»",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorTextTitle
                        )
                        Text(
                            text = "Выберите цвет фона виджета",
                            fontSize = 14.sp,
                            color = ColorTextMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        WidgetStyle.entries.forEach { style ->
                            val isSelected = selected == style
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) ColorBrandBlue else ColorBorderLight
                                ),
                                color = ColorBgMain,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .bouncyClickable { selected = style }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(
                                                Color(style.backgroundColor),
                                                RoundedCornerShape(2.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "12",
                                            color = Color(style.mainColor),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = style.title,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = ColorTextBody
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "До чего считать",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorTextTitle
                        )
                        Text(
                            text = "Когда обратный отсчёт должен заканчиваться",
                            fontSize = 13.sp,
                            color = ColorTextMuted,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        CountdownMode.entries.forEach { option ->
                            val isSelected = mode == option
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) ColorBrandBlue else ColorBorderLight
                                ),
                                color = ColorBgMain,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .bouncyClickable { mode = option }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = option.title,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = ColorTextBody
                                    )
                                    Text(
                                        text = option.description,
                                        fontSize = 12.sp,
                                        color = ColorTextMuted
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ColorBrandFill,
                            modifier = Modifier
                                .fillMaxWidth()
                                .bouncyClickable {
                                    WidgetStyle.save(this@BellCountdownConfigActivity, appWidgetId, selected)
                                    CountdownMode.save(this@BellCountdownConfigActivity, appWidgetId, mode)
                                    // Сразу отрисуем виджет выбранным стилем
                                    val manager = AppWidgetManager.getInstance(this@BellCountdownConfigActivity)
                                    BellCountdownWidgetProvider.updateWidgets(this@BellCountdownConfigActivity, manager, intArrayOf(appWidgetId))
                                    WidgetAlarm.scheduleNext(this@BellCountdownConfigActivity)

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
