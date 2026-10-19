package com.politex.ui.components

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorDangerFill
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import com.politex.util.GroupCatalog
import com.politex.util.GroupParser
import com.politex.util.MpkCurriculum
import com.politex.util.SubgroupPref

internal enum class FirstRunStep { GROUP, SUBGROUP }

internal val firstRunSubgroupOptions: List<Pair<SubgroupPref, String>> =
    listOf(
        SubgroupPref.FIRST to "Первая",
        SubgroupPref.SECOND to "Вторая",
        SubgroupPref.NEUTRAL to "Нейтрально",
    )

@Composable
fun FirstRunGroupDialog(
    onComplete: (group: String, subgroup: SubgroupPref) -> Unit,
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(FirstRunStep.GROUP) }
    var chosenGroup by rememberSaveable { mutableStateOf("") }
    var selectedCourse by rememberSaveable { mutableIntStateOf(1) }
    var subgroup by rememberSaveable { mutableStateOf(SubgroupPref.NEUTRAL) }
    var manualInput by rememberSaveable { mutableStateOf("") }
    var inputError by rememberSaveable { mutableStateOf(false) }
    val catalogCodes = remember { GroupCatalog.load(context).all() }

    AlertDialog(
        onDismissRequest = { },
        properties =
        DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        modifier =
        Modifier
            .padding(16.dp)
            .fillMaxSize()
            .clip(RoundedCornerShape(2.dp))
            .testTag("first_run_dialog"),
        confirmButton = {},
        containerColor = ColorBgMain,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                    Modifier
                        .size(32.dp)
                        .background(ColorBrandFill, RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (step == FirstRunStep.GROUP) "Выбор группы" else "Выбор подгруппы",
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = ColorTextTitle,
                        ),
                    )
                    Text(
                        text =
                        if (step == FirstRunStep.GROUP) {
                            "Шаг 1 из 2"
                        } else {
                            "Шаг 2 из 2 · группа $chosenGroup"
                        },
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontSize = 11.sp,
                            color = ColorTextMuted,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        text = {
            when (step) {
                FirstRunStep.GROUP ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            com.politex.ui.screens.GroupSchedulePicker(
                                selectedCourse = selectedCourse,
                                onCourseSelected = { selectedCourse = it },
                                catalogCodes = catalogCodes,
                                confirmLabel = "ДАЛЕЕ",
                                onGroupSelected = { group ->
                                    chosenGroup = group
                                    step = FirstRunStep.SUBGROUP
                                },
                            )
                        }
                        Text(
                            text = "Или введите группу вручную:",
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontSize = 12.sp,
                                color = ColorTextMuted,
                            ),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = manualInput,
                                onValueChange = {
                                    manualInput = it
                                    inputError = false
                                },
                                placeholder = { Text("Например: 43Э", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(2.dp),
                                isError = inputError,
                                colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ColorBrandBlue,
                                    unfocusedBorderColor = ColorBorderLight,
                                ),
                                modifier =
                                Modifier
                                    .weight(1f)
                                    .testTag("first_run_manual_input"),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                color = ColorBrandFill,
                                contentColor = Color.White,
                                modifier =
                                Modifier
                                    .height(Tokens.ButtonHeight)
                                    .bouncyClickable {
                                        val parsed = GroupParser.parse(manualInput)
                                        if (parsed != null) {
                                            chosenGroup = parsed.canonicalName
                                            step = FirstRunStep.SUBGROUP
                                        } else {
                                            inputError = true
                                        }
                                    }
                                    .testTag("first_run_manual_button"),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                ) {
                                    Text(
                                        text = "ВЫБРАТЬ",
                                        style =
                                        androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                        ),
                                    )
                                }
                            }
                        }
                        if (inputError) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Неверный формат группы",
                                color = ColorDangerFill,
                                style =
                                androidx.compose.ui.text
                                    .TextStyle(fontSize = 11.sp),
                            )
                        }
                    }
                FirstRunStep.SUBGROUP ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Если занятия идут по подгруппам — выберите свою. Если нет — оставьте «Нейтрально».",
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontSize = 13.sp,
                                color = ColorTextMuted,
                            ),
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "ПОДГРУППА",
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = Tokens.SectionHeader,
                                color = ColorTextMuted,
                            ),
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            firstRunSubgroupOptions.forEach { (pref, label) ->
                                FirstRunOptionChip(
                                    label = label,
                                    selected = subgroup == pref,
                                    tag = "first_run_subgroup_${pref.name.lowercase()}",
                                    modifier = Modifier.weight(1f),
                                    onClick = { subgroup = pref },
                                )
                            }
                        }
                        if (subgroup != SubgroupPref.NEUTRAL) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Маршруты и виджеты будут вести на кабинеты выбранной подгруппы.",
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontSize = 11.sp,
                                    color = ColorTextMuted,
                                ),
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ColorBrandFill,
                            border = BorderStroke(1.dp, ColorBrandBlue),
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = Tokens.ButtonHeight)
                                .bouncyClickable { onComplete(chosenGroup, subgroup) }
                                .testTag("first_run_done_button"),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 12.dp),
                            ) {
                                Text(
                                    text = "ГОТОВО",
                                    style =
                                    androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                    ),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
            }
        },
    )
}

@Composable
private fun FirstRunOptionChip(
    label: String,
    selected: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (selected) ColorTopBar else ColorBorderLight),
        color = if (selected) ColorTopBar else ColorBgMain,
        modifier =
        modifier
            .heightIn(min = Tokens.HitTarget)
            .bouncyClickable(onClick = onClick)
            .testTag(tag),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
        ) {
            Text(
                text = label,
                style =
                androidx.compose.ui.text.TextStyle(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (selected) Color.White else ColorTextBody,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
    }
}

@Composable
fun GroupSelectionDialog(
    currentGroupName: String,
    onDismissRequest: () -> Unit,
    showClose: Boolean = true,
    onGroupSelected: (String) -> Unit,
) {
    var selectedCourse by remember {
        val currentInfo = GroupParser.parse(currentGroupName)

        mutableIntStateOf(currentInfo?.course ?: 0)
    }

    var manualInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    val allPresetGroups = remember { GroupParser.generateCommonGroups() }
    val filteredGroups =
        remember(selectedCourse) {
            allPresetGroups.filter { it.course == selectedCourse }
        }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier =
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp)),
        confirmButton = {},
        containerColor = ColorBgMain,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                        Modifier
                            .size(32.dp)
                            .background(ColorBrandFill, RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Выбор группы",
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = ColorTextTitle,
                        ),
                    )
                }
                if (showClose) {

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(Tokens.HitTarget),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = ColorTextMuted,
                        )
                    }
                }
            }
        },
        text = {

            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Выберите курс:",
                    style =
                    androidx.compose.ui.text.TextStyle(
                        color = ColorTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (1..4).forEach { course ->
                        val isSelected = selectedCourse == course
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, if (isSelected) ColorTopBar else ColorBorderLight),
                            color = if (isSelected) ColorTopBar else ColorBgMain,
                            modifier =
                            Modifier
                                .weight(1f)
                                .height(Tokens.ChipForm)
                                .bouncyClickable { selectedCourse = course },
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "$course КУРС",
                                    style =
                                    androidx.compose.ui.text.TextStyle(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        color = if (isSelected) Color.White else ColorTextBody,
                                    ),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (selectedCourse == 0) "Сначала выберите курс:" else "Группы $selectedCourse курса:",
                    style =
                    androidx.compose.ui.text.TextStyle(
                        color = ColorTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (selectedCourse != 0) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                    ) {
                        items(filteredGroups) { group ->
                            val isCurrent = group.canonicalName.equals(currentGroupName, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                color = if (isCurrent) ColorSurfaceHighlight else ColorBgMain,
                                border =
                                BorderStroke(
                                    1.dp,
                                    if (isCurrent) ColorTopBar else ColorBorderLight,
                                ),
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .bouncyClickable {
                                        onGroupSelected(group.canonicalName)
                                    },
                            ) {
                                Column(
                                    modifier =
                                    Modifier
                                        .padding(vertical = 8.dp, horizontal = 4.dp)
                                        .fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = group.canonicalName,
                                        style =
                                        androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = if (isCurrent) ColorBrandBlue else ColorTextTitle,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val spec = MpkCurriculum.getSpecialty(group.specialtyCode)
                                    Text(
                                        text = spec?.shortName ?: "",
                                        style =
                                        androidx.compose.ui.text
                                            .TextStyle(fontSize = 10.sp, color = ColorTextMuted),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Или введите свою группу:",
                    style =
                    androidx.compose.ui.text.TextStyle(
                        color = ColorTextMuted,
                        fontSize = 12.sp,
                    ),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = {
                            manualInput = it
                            inputError = null
                        },

                        placeholder = { Text("Например: 11А", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(2.dp),
                        isError = inputError != null,
                        colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorBrandBlue,
                            unfocusedBorderColor = ColorBorderLight,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorBrandFill,
                        contentColor = Color.White,
                        modifier =
                        Modifier
                            .height(Tokens.ButtonHeight)
                            .bouncyClickable {
                                val parsed = GroupParser.parse(manualInput)
                                if (parsed != null) {
                                    onGroupSelected(parsed.canonicalName)
                                } else {
                                    inputError = "Неверный формат группы"
                                }
                            },
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        ) {
                            Text(
                                text = "ВЫБРАТЬ",
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                ),
                            )
                        }
                    }
                }

                if (inputError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = inputError ?: "",
                        color = ColorDangerFill,
                        style =
                        androidx.compose.ui.text
                            .TextStyle(fontSize = 11.sp),
                    )
                }
            }
        },
    )
}
