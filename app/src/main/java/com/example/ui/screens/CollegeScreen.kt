package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GroupInfo
import com.example.ui.theme.ColorBadgeCourse
import com.example.ui.theme.ColorBgMain
import com.example.ui.theme.ColorBorderLight
import com.example.ui.theme.ColorBrandBlue
import com.example.ui.theme.ColorBrandFill
import com.example.ui.theme.ColorDividerLight
import com.example.ui.theme.ColorSurfaceHighlight
import com.example.ui.theme.ColorSurfaceVariantLight
import com.example.ui.theme.ColorTextBody
import com.example.ui.theme.ColorTextMuted
import com.example.ui.theme.ColorTextTitle
import com.example.ui.theme.ColorTopBar
import com.example.ui.theme.TextStylePageTitle
import com.example.ui.util.bouncyClickable
import com.example.util.MpkCurriculum

/**
 * Справочная вкладка «Колледж»:
 * 1. «Моя специальность» — полная информация о специальности выбранной группы:
 *    код, квалификация, рабочие профессии и предметы по курсам.
 * 2. «Преподаватели» — поиск по базе преподавателей колледжа.
 */
@Composable
fun CollegeScreen(
    groupInfo: GroupInfo,
    modifier: Modifier = Modifier
) {
    var section by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain)
    ) {
        // Заголовок страницы
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Колледж",
                style = TextStylePageTitle,
                maxLines = 1
            )
            Text(
                text = "Специальности и преподаватели",
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = ColorBrandBlue
                ),
                maxLines = 1
            )
        }

        // Сегмент-переключатель разделов
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SectionChip(
                title = "МОЯ СПЕЦИАЛЬНОСТЬ",
                icon = { Icon(Icons.Default.School, null, tint = if (section == 0) Color.White else ColorBrandBlue, modifier = Modifier.size(14.dp)) },
                selected = section == 0,
                onClick = { section = 0 },
                modifier = Modifier.weight(1f)
            )
            SectionChip(
                title = "ПРЕПОДАВАТЕЛИ",
                icon = { Icon(Icons.Default.Person, null, tint = if (section == 1) Color.White else ColorBrandBlue, modifier = Modifier.size(14.dp)) },
                selected = section == 1,
                onClick = { section = 1 },
                modifier = Modifier.weight(1f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(1.dp)
                .background(ColorDividerLight)
        )

        when (section) {
            0 -> MySpecialtySection(groupInfo)
            else -> TeachersSection()
        }
    }
}

@Composable
private fun SectionChip(
    title: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (selected) ColorTopBar else ColorBorderLight),
        color = if (selected) ColorTopBar else ColorBgMain,
        modifier = modifier
            .height(40.dp)
            .bouncyClickable(onClick = onClick)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            icon()
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                softWrap = false,
                maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (selected) Color.White else ColorTextBody
                )
            )
        }
    }
}

/**
 * Раздел «Моя специальность»: карточка специальности + предметы по курсам.
 */
@Composable
private fun MySpecialtySection(groupInfo: GroupInfo) {
    val specialty = remember(groupInfo.specialtyCode) {
        MpkCurriculum.getSpecialty(groupInfo.specialtyCode)
    }
    val courses = specialty?.subjectsByCourse?.keys?.sorted() ?: emptyList()
    var selectedCourse by rememberSaveable(groupInfo.specialtyCode) {
        mutableIntStateOf(groupInfo.course.coerceIn(courses.minOrNull() ?: 1, courses.maxOrNull() ?: 1))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (specialty == null) {
            item {
                EmptyCard(text = "Специальность для группы ${groupInfo.canonicalName} не найдена в справочнике.")
            }
            return@LazyColumn
        }

        // Карточка специальности
        item {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBgMain,
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("specialty_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(ColorBrandFill, RoundedCornerShape(2.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Группа ${groupInfo.canonicalName}",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = ColorTextTitle
                                )
                            )
                            Text(
                                text = specialty.shortName,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 12.sp,
                                    color = ColorTextMuted
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    InfoRow("Специальность", specialty.fullName)
                    if (specialty.cipher.isNotBlank()) {
                        InfoRow("Код специальности", specialty.cipher)
                    }
                    if (specialty.qualification.isNotBlank()) {
                        InfoRow("Квалификация", specialty.qualification)
                    }

                    // Рабочие профессии
                    if (specialty.workerProfessions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "РАБОЧИЕ ПРОФЕССИИ",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = ColorBrandBlue
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        specialty.workerProfessions.forEach { profession ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .size(4.dp)
                                        .background(ColorBrandBlue, RoundedCornerShape(1.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = profession,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 12.sp,
                                        color = ColorTextBody
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Переключатель курсов
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                courses.forEach { course ->
                    val isSelected = course == selectedCourse
                    val isCurrent = course == groupInfo.course
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) ColorTopBar else if (isCurrent) ColorBrandBlue else ColorBorderLight
                        ),
                        color = if (isSelected) ColorTopBar else ColorBgMain,
                        modifier = Modifier
                            .weight(1f)
                            .bouncyClickable { selectedCourse = course }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "$course КУРС",
                                softWrap = false,
                                maxLines = 1,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = if (isSelected || isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White else ColorTextBody
                                )
                            )
                        }
                    }
                }
            }
        }

        // Предметы выбранного курса
        val subjects = specialty.subjectsByCourse[selectedCourse].orEmpty()
        item {
            Text(
                text = "ПРЕДМЕТЫ ${selectedCourse}-ГО КУРСА (${subjects.size})",
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = ColorBrandBlue
                )
            )
        }
        items(subjects) { subject ->
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorSurfaceVariantLight,
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = subject,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        color = ColorTextBody
                    ),
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label.uppercase(),
            style = androidx.compose.ui.text.TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = ColorTextMuted
            )
        )
        Text(
            text = value,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 13.sp,
                color = ColorTextBody
            )
        )
    }
}

/**
 * Раздел «Преподаватели»: поиск по базе.
 * База подключается из CSV; пока данных нет — аккуратная заглушка.
 */
@Composable
private fun TeachersSection() {
    var searchQuery by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text("Поиск преподавателя...")
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = ColorBrandBlue, modifier = Modifier.size(18.dp))
            },
            singleLine = true,
            shape = RoundedCornerShape(2.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = ColorSurfaceVariantLight,
                unfocusedContainerColor = ColorSurfaceVariantLight,
                focusedIndicatorColor = ColorBrandBlue,
                unfocusedIndicatorColor = ColorBorderLight
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("teachers_search_input")
        )

        // База преподавателей ещё не загружена
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorSurfaceHighlight,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = ColorBrandBlue,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "База преподавателей подключается",
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = ColorTextTitle
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Список всех преподавателей колледжа с поиском появится после загрузки данных.",
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        color = ColorTextMuted
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorSurfaceVariantLight,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 13.sp,
                color = ColorTextMuted
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )
    }
}
