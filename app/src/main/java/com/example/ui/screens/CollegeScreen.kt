package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
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

private enum class CollegePage { MENU, SPECIALTY, TEACHERS }

/**
 * Справочная вкладка «Колледж»: список разделов с под-экранами.
 */
@Composable
fun CollegeScreen(
    groupInfo: GroupInfo,
    modifier: Modifier = Modifier
) {
    var page by rememberSaveable { mutableStateOf(CollegePage.MENU) }
    BackHandler(enabled = page != CollegePage.MENU) { page = CollegePage.MENU }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain)
    ) {
        when (page) {
            CollegePage.MENU -> CollegeMenu(
                groupInfo = groupInfo,
                onOpenSpecialty = { page = CollegePage.SPECIALTY },
                onOpenTeachers = { page = CollegePage.TEACHERS }
            )
            CollegePage.SPECIALTY -> MySpecialtyPage(
                groupInfo = groupInfo,
                onBack = { page = CollegePage.MENU }
            )
            CollegePage.TEACHERS -> TeachersPage(
                onBack = { page = CollegePage.MENU }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Список разделов
// ---------------------------------------------------------------------------

@Composable
private fun CollegeMenu(
    groupInfo: GroupInfo,
    onOpenSpecialty: () -> Unit,
    onOpenTeachers: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        Header(title = "Колледж", subtitle = "Специальности, преподаватели и полезные ссылки")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.School, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Моя специальность",
                    subtitle = "Группа ${groupInfo.canonicalName}: код, квалификация, предметы по курсам",
                    onClick = onOpenSpecialty
                )
            }
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Преподаватели",
                    subtitle = "Поиск по базе, избранное и фотографии",
                    onClick = onOpenTeachers
                )
            }
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.Language, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Сайт колледжа",
                    subtitle = "guo-mpk.by — официальная информация",
                    onClick = {
                        try {
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://guo-mpk.by/")).let {
                                ctx.startActivity(it)
                            }
                        } catch (_: Exception) {
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MenuCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(ColorBrandFill, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) { icon() }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ColorTextTitle
                    )
                )
                Text(
                    text = subtitle,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        color = ColorTextMuted
                    ),
                    maxLines = 2
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = ColorTextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Под-экран «Моя специальность»
// ---------------------------------------------------------------------------

@Composable
private fun MySpecialtyPage(groupInfo: GroupInfo, onBack: () -> Unit) {
    val specialty = remember(groupInfo.specialtyCode) {
        MpkCurriculum.getSpecialty(groupInfo.specialtyCode)
    }
    val courses = specialty?.subjectsByCourse?.keys?.sorted() ?: emptyList()
    var selectedCourse by rememberSaveable(groupInfo.specialtyCode) {
        mutableIntStateOf(groupInfo.course.coerceIn(courses.minOrNull() ?: 1, courses.maxOrNull() ?: 1))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Шапка под-экрана с кнопкой «Назад»
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBgMain,
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier = Modifier
                    .size(36.dp)
                    .bouncyClickable(onClick = onBack)
                    .testTag("specialty_back_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = ColorBrandBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Моя специальность",
                    style = TextStylePageTitle.copy(fontSize = 24.sp),
                    maxLines = 1
                )
                Text(
                    text = "Группа ${groupInfo.canonicalName}",
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = ColorBrandBlue
                    ),
                    maxLines = 1
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ColorDividerLight)
        )

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

            // Отсек 1: специальность, код (крупно и хорошо видно), квалификация
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
                        Text(
                            text = "СПЕЦИАЛЬНОСТЬ",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = ColorTextMuted
                            )
                        )
                        Text(
                            text = specialty.fullName,
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = ColorTextTitle
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Код специальности — крупный заметный блок
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ColorSurfaceHighlight,
                            border = BorderStroke(1.dp, ColorBrandBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = "КОД СПЕЦИАЛЬНОСТИ",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = ColorTextMuted
                                    )
                                )
                                Text(
                                    text = specialty.cipher,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp,
                                        color = ColorBrandBlue
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (specialty.qualification.isNotBlank()) {
                            InfoRow("Квалификация", specialty.qualification)
                        }
                    }
                }
            }

            // Отсек 2: рабочие профессии
            if (specialty.workerProfessions.isNotEmpty()) {
                item { SectionLabel("РАБОЧИЕ ПРОФЕССИИ") }
                items(specialty.workerProfessions) { profession ->
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorSurfaceVariantLight,
                        border = BorderStroke(1.dp, ColorBorderLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = profession,
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 13.sp,
                                color = ColorTextBody
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Отсек 3: предметы по курсам (вкладки по количеству курсов)
            item { SectionLabel("ПРЕДМЕТЫ ПО КУРСАМ") }
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
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Под-экран «Преподаватели»
// ---------------------------------------------------------------------------

@Composable
private fun TeachersPage(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { com.example.data.repository.TeachersRepository(context) }
    val teachers = remember { repository.loadTeachers() }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var favoritesVersion by remember { mutableStateOf(0) } // перерисовка при клике по звезде
    val favorites = remember(favoritesVersion) { repository.favorites() }

    val filtered = remember(teachers, searchQuery, favorites) {
        val q = searchQuery.trim().lowercase()
        val base = if (q.isBlank()) teachers else teachers.filter {
            it.name.lowercase().contains(q) || it.subject.lowercase().contains(q)
        }
        base.sortedWith(compareByDescending<com.example.data.repository.Teacher> { favorites.contains(it.name) }.thenBy { it.name })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBgMain,
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier = Modifier
                    .size(36.dp)
                    .bouncyClickable(onClick = onBack)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = ColorBrandBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Преподаватели",
                style = TextStylePageTitle.copy(fontSize = 24.sp),
                maxLines = 1
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ColorDividerLight)
        )

        if (teachers.isEmpty()) {
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
                        text = "Список всех преподавателей колледжа с поиском появится после загрузки CSV.",
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp,
                            color = ColorTextMuted
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск преподавателя...") },
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered) { teacher ->
                    TeacherCard(
                        teacher = teacher,
                        isFavorite = favorites.contains(teacher.name),
                        onToggleFavorite = {
                            repository.toggleFavorite(teacher.name)
                            favoritesVersion++
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TeacherCard(
    teacher: com.example.data.repository.Teacher,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (teacher.photo.isNotBlank()) {
                coil.compose.AsyncImage(
                    model = "file:///android_asset/teachers_photos/" + teacher.photo,
                    contentDescription = teacher.name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .background(ColorSurfaceHighlight, RoundedCornerShape(2.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(ColorBrandFill, RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = teacher.name.split(" ").firstOrNull()?.take(1) ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = teacher.name,
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ColorTextTitle
                    )
                )
                if (teacher.subject.isNotBlank()) {
                    Text(
                        text = teacher.subject,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp,
                            color = ColorTextMuted
                        ),
                        maxLines = 2
                    )
                }
            }

            androidx.compose.material3.IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) androidx.compose.material.icons.Icons.Filled.Star
                    else androidx.compose.material.icons.Icons.Outlined.StarBorder,
                    contentDescription = if (isFavorite) "Убрать из избранного" else "В избранное",
                    tint = if (isFavorite) androidx.compose.ui.graphics.Color(0xFFF59E0B) else ColorTextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Общие элементы
// ---------------------------------------------------------------------------

@Composable
private fun Header(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = title,
            style = TextStylePageTitle,
            maxLines = 1
        )
        Text(
            text = subtitle,
            style = androidx.compose.ui.text.TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = ColorBrandBlue
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = androidx.compose.ui.text.TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = ColorBrandBlue
        )
    )
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
