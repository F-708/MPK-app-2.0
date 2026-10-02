package com.politex.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.model.GroupInfo
import com.politex.data.repository.TaskRepository
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.TextStylePageTitle
import com.politex.ui.util.bouncyClickable

/**
 * Ф4: вкладка «Уроки» — хаб трёх разделов: Материальная база / Задания / Оценки.
 * Переключатель — три чипа в одну строку (паттерн BellScheduleType из BellsScreen):
 * большие хитбоксы 44dp, выбранный залит ColorTopBar, порядок стабилен и подсознателен
 * (материалы → задания → оценки: от «взять» к «сделать» к «проверить»).
 */
enum class LessonsSection(
    val title: String,
) {
    MATERIALS("Материалы"),
    TASKS("Задания"),
    GRADES("Оценки"),
}

@Composable
fun LessonsHubScreen(
    groupInfo: GroupInfo,
    taskRepository: TaskRepository,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    // Задания — привычное место пользователя: стартуем с них, выбор переживает смерть вкладки
    var section by rememberSaveable { mutableStateOf(LessonsSection.TASKS) }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(ColorBgMain),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text = "Уроки", style = TextStylePageTitle, maxLines = 1)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LessonsSection.entries.forEach { s ->
                    val isSelected = section == s
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, if (isSelected) ColorTopBar else ColorBorderLight),
                            color = if (isSelected) ColorTopBar else ColorBgMain,
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .bouncyClickable { section = s },
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = s.title,
                                    softWrap = false,
                                    maxLines = 1,
                                    style =
                                    androidx.compose.ui.text.TextStyle(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = if (isSelected) Color.White else ColorTextBody,
                                    ),
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier =
                            Modifier
                                .width(28.dp)
                                .height(3.dp)
                                .background(
                                    if (isSelected) ColorBrandBlue else Color.Transparent,
                                    RoundedCornerShape(2.dp),
                                ),
                        )
                    }
                }
            }
        }

        when (section) {
            LessonsSection.TASKS ->
                TasksScreen(
                    groupInfo = groupInfo,
                    taskRepository = taskRepository,
                    modifier = Modifier.fillMaxSize(),
                    snackbarHostState = snackbarHostState,
                )
            LessonsSection.MATERIALS ->
                MaterialBaseScreen(
                    groupInfo = groupInfo,
                    modifier = Modifier.fillMaxSize(),
                )
            LessonsSection.GRADES ->
                GradesScreen(
                    groupInfo = groupInfo,
                    modifier = Modifier.fillMaxSize(),
                )
        }
    }
}
