package com.politex.ui.dataeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.ui.components.SearchBar
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import com.politex.util.FuzzySearch
import com.politex.util.MpkCurriculum

object SubjectPickerLogic {
    fun parse(subjects: String): List<String> =
        subjects
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }

    fun catalog(current: List<String>): List<String> =
        (MpkCurriculum.ALL_SUBJECTS + current)
            .distinct()
            .sortedWith(compareBy({ FuzzySearch.normalize(it) }, { it }))

    fun filter(
        catalog: List<String>,
        query: String,
    ): List<String> {
        val q = FuzzySearch.normalize(query.trim())
        if (q.isBlank()) return catalog
        return catalog.filter { FuzzySearch.normalize(it).contains(q) }
    }

    fun join(items: List<String>): String = items.joinToString(", ")
}

@Composable
fun SubjectPickerSheet(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val selected = remember { mutableStateListOf<String>().apply { addAll(SubjectPickerLogic.parse(initial)) } }
    val catalog = remember { SubjectPickerLogic.catalog(selected.toList()) }
    var query by remember { mutableStateOf("") }
    val visible = remember(catalog, query) { SubjectPickerLogic.filter(catalog, query) }

    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .testTag("subject_picker_scrim"),
    ) {
        Surface(
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .testTag("subject_picker_sheet"),
            shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
            color = ColorBgMain,
            border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorderLight),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Предметы преподавателя",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ColorTextTitle),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "выбрано: ${selected.size}",
                        style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = ColorBrandBlue),
                        modifier = Modifier.testTag("subject_picker_count"),
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ColorBorderLight))
                SearchBar(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Поиск предмета",
                    testTag = "subject_picker_search",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (visible.isEmpty()) {
                    Text(
                        text = "Ничего не найдено",
                        style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).testTag("subject_picker_empty"),
                    )
                } else {
                    Column(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .testTag("subject_picker_list"),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        visible.forEach { subject ->
                            val checked = subject in selected
                            Row(
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Tokens.HitTarget)
                                    .background(
                                        if (checked) ColorSurfaceHighlight else Color.Transparent,
                                        RoundedCornerShape(2.dp),
                                    ).bouncyClickable {
                                        if (checked) selected.remove(subject) else selected.add(subject)
                                    }.padding(horizontal = 8.dp)
                                    .testTag("subject_picker_item_$subject"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier =
                                    Modifier
                                        .size(22.dp)
                                        .background(
                                            if (checked) ColorBrandFill else ColorSurfaceHighlight,
                                            RoundedCornerShape(2.dp),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (checked) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = subject,
                                    style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ColorTextTitle),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PickerButton(
                        label = "Отмена",
                        filled = false,
                        testTag = "subject_picker_cancel",
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                    )
                    PickerButton(
                        label = "Готово",
                        filled = true,
                        testTag = "subject_picker_confirm",
                        modifier = Modifier.weight(1f),
                    ) {
                        val ordered = catalog.filter { it in selected } + selected.filter { it !in catalog }
                        onConfirm(SubjectPickerLogic.join(ordered.distinct()))
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerButton(
    label: String,
    filled: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (filled) ColorBrandFill else ColorBgMain,
        border = androidx.compose.foundation.BorderStroke(1.dp, ColorBrandBlue),
        modifier =
        modifier
            .heightIn(min = Tokens.ButtonHeight)
            .bouncyClickable(onClick = onClick)
            .testTag(testTag),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                text = label.uppercase(),
                style =
                TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = Tokens.ButtonText,
                    color = if (filled) Color.White else ColorBrandBlue,
                ),
            )
        }
    }
}
