package com.politex.ui.dataeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.content.OwnerOverridesStore
import com.politex.data.repository.Teacher
import com.politex.ui.components.CenteredLoader
import com.politex.ui.components.EmptyState
import com.politex.ui.components.SearchBar
import com.politex.ui.components.SubPageHeader
import com.politex.ui.teacher.TeacherOnboardingLogic
import com.politex.ui.theme.ColorAccentGold
import com.politex.ui.theme.ColorAccentGoldBg
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.util.bouncyClickable
import com.politex.ui.util.rememberTeachers

@Composable
fun TeacherEditorListScreen(
    onBack: () -> Unit,
    onEditTeacher: (String) -> Unit,
) {
    val context = LocalContext.current
    val teachers = rememberTeachers()
    val overridesRevision = OwnerOverridesStore.revision.intValue
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(teachers, query) { TeacherOnboardingLogic.teacherSearch(teachers, query) }
    val overrideMarks =
        remember(overridesRevision) {
            val list = OwnerOverridesStore.listTeachers(context)
            val keys = list.map { it.key }.toHashSet()
            list.mapTo(keys) { OwnerOverridesStore.keyOf(it.fio) }
        }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(ColorBgMain)
            .testTag("teacher_editor_list"),
    ) {
        SubPageHeader(
            title = "Правка преподавателей",
            subtitle = "Изменения видны сразу в справочнике",
            onBack = onBack,
        )
        SearchBar(
            value = query,
            onValueChange = { query = it },
            placeholder = "Фамилия, имя или предмет",
            testTag = "teacher_editor_search",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        when {
            teachers.isEmpty() -> CenteredLoader()

            results.isEmpty() ->
                EmptyState(
                    title = "Никого не нашли по «${query.trim()}»",
                    hint = "Попробуйте фамилию",
                    icon = Icons.Default.Edit,
                    modifier = Modifier.fillMaxSize(),
                )

            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(results) { t ->
                        TeacherEditorRow(
                            teacher = t,
                            hasOverride = OwnerOverridesStore.keyOf(t.name) in overrideMarks,
                            onClick = { onEditTeacher(t.name) },
                        )
                    }
                }
        }
    }
}

@Composable
private fun TeacherEditorRow(
    teacher: Teacher,
    hasOverride: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, if (hasOverride) ColorAccentGold else ColorBorderLight),
        modifier =
        Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
            .testTag("teacher_editor_row_${teacher.name}"),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = teacher.name,
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ColorTextTitle),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details =
                    listOf(teacher.position, teacher.subjects)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (hasOverride) {
                Text(
                    text = "ПРАВКА",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp, color = ColorAccentGold),
                    modifier =
                    Modifier
                        .background(ColorAccentGoldBg, RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = ColorTextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
