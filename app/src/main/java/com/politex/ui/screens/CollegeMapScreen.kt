package com.politex.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.model.CollegeMap
import com.politex.data.model.CollegeBellSchedule
import com.politex.data.model.CollegeMapData
import com.politex.data.model.FloorMap
import com.politex.data.model.MapRoom
import com.politex.data.repository.ScheduleRepository
import com.politex.data.local.entity.LessonEntity
import com.politex.ui.components.RoomScheduleDialog
import com.politex.ui.components.currentRoomLessonNumber
import com.politex.ui.components.filterRoomLessonsForDate
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.util.bouncyClickable
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val HIGHLIGHT_COLOR = Color(0xFF0072CE)
private val ROOM_FILL = Color(0x331976D2)
private val ROOM_BORDER = Color(0xFF0057A8)
private val DEAD_COLOR = Color(0xFFC0392B)

@Composable
fun CollegeMapScreen(
    initialRoom: String?,
    onBack: () -> Unit,

    // Т6: переход к карточке преподавателя из расписания кабинета
    onOpenTeacher: (String) -> Unit = { },

    // Точка входа из расписания (кабинет-«откуда»); маршруты удалены, параметр оставлен для совместимости вызовов
    fromRoom: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapData = remember { CollegeMapData.load(context) }

    // Т5: поиск по группе — репозиторий, как на остальных экранах
    val scheduleRepository = remember {
        ScheduleRepository(
            com.politex.data.local.MpkDatabase.getInstance(context).lessonDao()
        )
    }
    val today = remember { Calendar.getInstance() }
    val todayDateString = remember(today) {
        "%02d.%02d.%04d".format(
            today.get(Calendar.DAY_OF_MONTH),
            today.get(Calendar.MONTH) + 1,
            today.get(Calendar.YEAR)
        )
    }
    val todayDow = remember(today) {
        when (today.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }
    }

    // Группы с уроками сегодня — для быстрого выбора
    val todayGroups by produceState<List<String>>(initialValue = emptyList(), key1 = todayDateString) {
        value = runCatching {
            scheduleRepository.getDistinctGroupsForDateOrDay(todayDateString, todayDow)
        }.getOrDefault(emptyList())
    }

    val cameFromSchedule = !initialRoom.isNullOrBlank()
    var query by remember { mutableStateOf("") }
    var groupQuery by remember { mutableStateOf("") }
    // Сообщение от поиска по группе («Сейчас уроков нет — …» / ошибка)
    var groupMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    var searchFailed by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf(initialRoom?.trim().orEmpty()) }
    var floorId by remember {
        mutableStateOf(CollegeMap.floorForRoom(initialRoom.orEmpty())?.id ?: "1")
    }

    // Т6: расписание выбранного кабинета на сегодня
    var roomScheduleFor by remember { mutableStateOf<String?>(null) }
    val roomScheduleLessons by produceState<List<LessonEntity>>(
        initialValue = emptyList(),
        key1 = roomScheduleFor
    ) {
        val room = roomScheduleFor ?: return@produceState
        runCatching {
            scheduleRepository.getLessonsForRoomOnDate(room, todayDateString, todayDow)
        }.getOrNull()?.collect { value = it }
    }
    val nowMinutes = remember {
        val c = Calendar.getInstance()
        c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }
    val roomScheduleCurrentLesson = remember(roomScheduleFor) {
        currentRoomLessonNumber(nowMinutes, todayDow)
    }

    val floor: FloorMap = remember(floorId, mapData) { mapData.floor(floorId) }
    val rooms = remember(floor) { floor.rooms.filter { it.status != "nonexistent" } }
    val targetRoom = remember(floor, target) { floor.room(target) }

    LaunchedEffect(target) {
        CollegeMap.floorForRoom(target)?.let { floorId = it.id }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain)
    ) {
        MapHeader(
            subtitle = when {
                targetRoom != null -> "Кабинет ${targetRoom.number}"
                else -> "Планы этажей"
            },
            onBack = onBack
        )

        if (!cameFromSchedule) {
            Spacer(modifier = Modifier.height(4.dp))
            SearchField(
                value = query,
                onValueChange = { query = it; searchFailed = false },
                onFound = { found ->
                    groupMessage = null
                    target = found
                    searchFailed = found.isNotBlank() && findRoomEverywhere(mapData, found) == null
                },
                notFound = searchFailed
            )
            Spacer(modifier = Modifier.height(6.dp))
            GroupSearchField(
                value = groupQuery,
                todayGroups = todayGroups,
                onValueChange = { groupQuery = it; groupMessage = null },
                onSearch = { group ->
                    groupMessage = null
                    scope.launch {
                        val (room, msg) = searchGroupNow(
                            group = group.trim(),
                            scheduleRepository = scheduleRepository,
                            todayDateString = todayDateString,
                            todayDow = todayDow,
                            mapData = mapData
                        )
                        groupMessage = msg
                        room?.let {
                            target = it.number
                            searchFailed = false
                        }
                    }
                },
                message = groupMessage
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        FloorRow(
            current = floorId,
            onSelect = { floorId = it },
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        MapCanvas(
            floorId = floorId,
            floor = floor,
            targetRoom = targetRoom,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        if (targetRoom != null) {
            RoomPanel(
                room = targetRoom,
                onShowSchedule = { roomScheduleFor = targetRoom.number }
            )
        }
    }

    // Т6: расписание кабинета на сегодня
    roomScheduleFor?.let { roomNumber ->
        RoomScheduleDialog(
            roomNumber = roomNumber,
            dateTitle = "$todayDateString",
            lessons = filterRoomLessonsForDate(roomScheduleLessons, roomNumber, todayDateString),
            currentLessonNumber = roomScheduleCurrentLesson,
            onTeacherClick = onOpenTeacher,
            onDismissRequest = { roomScheduleFor = null }
        )
    }
}

@Composable
private fun RoomPanel(
    room: MapRoom?,
    onShowSchedule: (String) -> Unit = { },
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (room == null) {
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = ColorSurfaceVariantLight,
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Выберите этаж или введите номер кабинета — покажем, где он находится.",
                    style = TextStyle(fontSize = 13.sp, color = ColorTextMuted),
                    modifier = Modifier.padding(14.dp)
                )
            }
            return@Column
        }

        Surface(
            shape = RoundedCornerShape(3.dp),
            color = ColorBgMain,
            border = BorderStroke(1.dp, ColorBorderLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = ColorBrandFill,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = room.number,
                            style = TextStyle(
                                fontWeight = FontWeight.Normal,
                                fontSize = 17.sp,
                                color = Color.White
                            ),
                            maxLines = 1
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = room.title.ifBlank { "Кабинет ${room.number}" },
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ColorTextTitle),
                        maxLines = 2
                    )
                    val sub = room.title.ifBlank { "" }
                    if (sub.isNotBlank()) {
                        Text(
                            text = sub,
                            style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                            maxLines = 2
                        )
                    }
                }
            }
        }

        // Т6: подробнее — расписание кабинета на сегодня
        Surface(
            shape = RoundedCornerShape(2.dp),
            border = BorderStroke(1.dp, ColorBrandBlue),
            color = ColorBrandFill,
            modifier = Modifier
                .fillMaxWidth()
                .bouncyClickable { onShowSchedule(room.number) }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EventNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ПОДРОБНЕЕ — РАСПИСАНИЕ КАБИНЕТА",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                )
            }
        }

        if (room.note.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = ColorSurfaceVariantLight,
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = room.note,
                    style = TextStyle(fontSize = 13.sp, color = ColorTextBody),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun MapHeader(subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .bouncyClickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                tint = ColorBrandBlue,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Карта колледжа",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ColorTextTitle),
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun findRoomEverywhere(mapData: CollegeMapData, number: String): MapRoom? =
    listOf("1", "2", "3-4").firstNotNullOfOrNull { mapData.floor(it).room(number) }

// Т5: где группа сейчас. isNow=false → следующая пара («к N уроку»)
data class GroupRoomResult(val room: String, val lessonNumber: Int, val isNow: Boolean)

fun findGroupRoomNow(
    lessons: List<com.politex.data.local.entity.LessonEntity>,
    currentMinutes: Int,
    dayOfWeek: Int
): GroupRoomResult? {
    if (lessons.isEmpty()) return null
    val withRoom = lessons
        .filter { (it.roomFirst.isNotBlank() || it.roomSecond.isNotBlank()) && it.lessonNumber > 0 }
        .sortedBy { it.lessonNumber }
    if (withRoom.isEmpty()) return null

    // Текущий слот по звонкам (dayOfWeek — 1..7, как в ScheduleScreen)
    val slot = CollegeBellSchedule.getBellsForDay(dayOfWeek)
        .firstOrNull { currentMinutes in it.startMinutes..it.endMinutes }
        ?.takeIf { it.lessonNumber > 0 }

    val current = slot?.let { s -> withRoom.firstOrNull { it.lessonNumber == s.lessonNumber } }
    if (current != null) {
        return GroupRoomResult(
            // ponytail: подгруппу split-пары не определяем — берём roomFirst (потолок: не в 2 кабинетах сразу)
            room = current.roomFirst.ifBlank { current.roomSecond },
            lessonNumber = current.lessonNumber,
            isNow = true
        )
    }

    // Следующая пара: по звонкам, фолбэк — timeStart строки
    val endOfCurrent = slot?.endMinutes ?: currentMinutes
    val next = withRoom.firstOrNull { l ->
        val lessonStart = CollegeBellSchedule.getBellsForDay(dayOfWeek)
            .firstOrNull { it.lessonNumber == l.lessonNumber }?.startMinutes
            ?: l.timeStart.split(":").let { p ->
                if (p.size == 2) (p[0].toIntOrNull() ?: 0) * 60 + (p[1].toIntOrNull() ?: 0) else -1
            }
        lessonStart > endOfCurrent
    }
    return next?.let { GroupRoomResult(it.roomFirst.ifBlank { it.roomSecond }, it.lessonNumber, isNow = false) }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onFound: (String) -> Unit,
    notFound: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(3.dp),
            color = if (notFound) Color(0xFFFDECEC) else ColorSurfaceVariantLight,
            border = BorderStroke(
                if (notFound) 2.dp else 1.dp,
                if (notFound) DEAD_COLOR else ColorBorderLight
            ),
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (notFound) DEAD_COLOR else ColorTextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) {
                        Text(
                            text = "Номер кабинета, например 214",
                            style = TextStyle(fontSize = 14.sp, color = ColorTextMuted),
                            maxLines = 1
                        )
                    } else if (notFound) {
                        Text(
                            text = "$value — такого кабинета нет",
                            style = TextStyle(fontSize = 14.sp, color = DEAD_COLOR),
                            maxLines = 1
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { input ->
                            onValueChange(input.filter { it.isDigit() || it.isLetter() }.take(6))
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            color = if (notFound) Color.Transparent else ColorTextTitle
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (value.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Очистить",
                        tint = ColorTextMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .bouncyClickable { onValueChange("") }
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(3.dp),
            color = ColorBrandFill,
            modifier = Modifier
                .height(46.dp)
                .bouncyClickable { if (value.isNotBlank()) onFound(value.trim()) }
        ) {
            Text(
                text = "НАЙТИ",
                softWrap = false,
                maxLines = 1,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
            )
        }
    }
}

// Т5: поиск по группе. Запрашивает сегодняшие пары, считает «где группа сейчас»
// и открывает карту на этом кабинете; результат — либо кабинет, либо сообщение.
private suspend fun searchGroupNow(
    group: String,
    scheduleRepository: ScheduleRepository,
    todayDateString: String,
    todayDow: Int,
    mapData: CollegeMapData
): Pair<MapRoom?, String?> {
    if (group.isBlank()) return null to null
    // Ввод «41о»/«41O» приводим к каноническому виду БД («41О»)
    val canonical = com.politex.util.GroupParser.cleanRawGroupName(group)
    val lessons = runCatching {
        scheduleRepository.getLessonsForDateOrDay(canonical, todayDateString, todayDow).first()
    }.getOrDefault(emptyList())
    // Точная дата приоритетна, недельный шаблон — фолбэк (как в ScheduleScreen)
    val exact = lessons.filter { it.dateString == todayDateString }
    val effective = exact.ifEmpty { lessons.filter { it.dateString.isBlank() } }

    val cal = Calendar.getInstance()
    val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val result = findGroupRoomNow(effective, minutes, todayDow)

    val room = result?.room?.trim().orEmpty()
    val mapped = if (room.isBlank()) null else findRoomEverywhere(mapData, room)

    return mapped to when {
        effective.isEmpty() -> "У группы «$canonical» нет уроков сегодня"
        result == null || room.isBlank() -> "Сейчас уроков нет"
        mapped == null -> "Кабинет «$room» не найден на карте"
        result.isNow -> null
        else -> "Сейчас уроков нет — к ${result.lessonNumber} уроку"
    }
}

@Composable
private fun GroupSearchField(
    value: String,
    todayGroups: List<String>,
    onValueChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    message: String?
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            var dropdownExpanded by remember { mutableStateOf(false) }

            Surface(
                shape = RoundedCornerShape(3.dp),
                color = ColorSurfaceVariantLight,
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = ColorTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                text = "Группа, например 41О",
                                style = TextStyle(fontSize = 14.sp, color = ColorTextMuted),
                                maxLines = 1
                            )
                        }
                        BasicTextField(
                            value = value,
                            onValueChange = { input ->
                                onValueChange(input.filter { it.isLetter() || it.isDigit() }.take(5))
                            },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 15.sp, color = ColorTextTitle),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (value.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Очистить",
                            tint = ColorTextMuted,
                            modifier = Modifier
                                .size(18.dp)
                                .bouncyClickable { onValueChange("") }
                        )
                    }
                    // Быстрый выбор группы из сегодняшнего расписания
                    Box {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .bouncyClickable(
                                    enabled = todayGroups.isNotEmpty()
                                ) { dropdownExpanded = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Выбрать группу из сегодняшнего расписания",
                                tint = if (todayGroups.isNotEmpty()) ColorBrandBlue else ColorTextMuted,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            todayGroups.forEach { g ->
                                DropdownMenuItem(
                                    text = { Text(g, style = TextStyle(fontSize = 14.sp)) },
                                    onClick = {
                                        dropdownExpanded = false
                                        onValueChange(g)
                                        onSearch(g)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(3.dp),
                color = ColorBrandFill,
                modifier = Modifier
                    .height(46.dp)
                    .bouncyClickable { onSearch(value.trim()) }
            ) {
                Text(
                    text = "ГДЕ ГРУППА",
                    softWrap = false,
                    maxLines = 1,
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                )
            }
        }

        message?.let { msg ->
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = ColorSurfaceVariantLight,
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = msg,
                    style = TextStyle(fontSize = 13.sp, color = ColorTextBody),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun FloorRow(current: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CollegeMap.Floor.entries.forEach { f ->
            val selected = current == f.id
            Surface(
                shape = RoundedCornerShape(3.dp),
                color = if (selected) ColorBrandFill else ColorBgMain,
                border = BorderStroke(1.dp, if (selected) ColorBrandFill else ColorBorderLight),
                modifier = Modifier
                    .weight(1f)
                    .bouncyClickable { onSelect(f.id) }
            ) {
                Text(
                    text = f.title,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (selected) Color.White else ColorTextBody
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun MapCanvas(
    floorId: String,
    floor: FloorMap,
    targetRoom: MapRoom?,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    val painter = painterResource(id = mapRes(floorId))
    val aspect = painter.intrinsicSize.let { s ->
        if (s.height > 0f && s.width > 0f) s.width / s.height else 1.7917f
    }

    val pulse by rememberInfiniteTransition(label = "подсветка").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "пульс"
    )

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .background(Color(0xFFEDF2F7))
    ) {
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        val imgW = viewW
        val imgH = imgW / aspect

    val measurer = rememberTextMeasurer()

    val baseFontPx = 22f
    val numberLayouts: Map<String, TextLayoutResult> = remember(floorId, floor, density) {
        floor.rooms.filter { it.number.isNotBlank() }.associate { r ->
            r.id to measurer.measure(
                text = r.number,
                style = TextStyle(
                    fontSize = with(density) { baseFontPx.toSp() },
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF0B3564)
                ),
                maxLines = 1
            )
        }
    }

        fun clampX(v: Float, s: Float): Float {
            val c = imgW * s
            return if (c <= viewW) (viewW - c) / 2f else v.coerceIn(viewW - c, 0f)
        }
        fun clampY(v: Float, s: Float): Float {
            val c = imgH * s
            return if (c <= viewH) (viewH - c) / 2f else v.coerceIn(viewH - c, 0f)
        }

        fun restZoom(): Float =
            (viewH / imgH * 0.72f).coerceIn(1f, 2.2f)

        LaunchedEffect(floorId, targetRoom?.id, viewW, viewH) {
            if (viewW <= 0f || viewH <= 0f) return@LaunchedEffect
            val rest = restZoom()
            val room = targetRoom
            if (room == null) {
                scope.launch { scale.animateTo(rest, tween(250)) }
                scope.launch { offsetX.animateTo(clampX(viewW / 2f - imgW * rest / 2f, rest), tween(250)) }
                scope.launch { offsetY.animateTo(clampY(viewH / 2f - imgH * rest / 2f, rest), tween(250)) }
                return@LaunchedEffect
            }
            val px = room.x * imgW
            val py = room.y * imgH
            val zoom = 2.6f
            scope.launch { scale.animateTo(zoom, tween(420, easing = LinearOutSlowInEasing)) }
            scope.launch { offsetX.animateTo(clampX(viewW / 2f - px * zoom, zoom), tween(420, easing = LinearOutSlowInEasing)) }
            scope.launch { offsetY.animateTo(clampY(viewH / 2f - py * zoom, zoom), tween(420, easing = LinearOutSlowInEasing)) }
            kotlinx.coroutines.delay(600)
            scope.launch { scale.animateTo(rest, tween(540)) }
            scope.launch { offsetX.animateTo(clampX(viewW / 2f - imgW * rest / 2f, rest), tween(540)) }
            scope.launch { offsetY.animateTo(clampY(viewH / 2f - imgH * rest / 2f, rest), tween(540)) }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(viewW, viewH) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        scope.launch {
                            val next = (scale.value * zoom).coerceIn(0.8f, 8f)

                            val bx = (centroid.x - offsetX.value) / scale.value
                            val by = (centroid.y - offsetY.value) / scale.value
                            scale.snapTo(next)
                            offsetX.snapTo(clampX(centroid.x - bx * next + pan.x, next))
                            offsetY.snapTo(clampY(centroid.y - by * next + pan.y, next))
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        translationX = offsetX.value
                        translationY = offsetY.value
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                Image(
                    painter = painter,
                    contentDescription = "План этажа",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(with(density) { imgW.toDp() })
                        .height(with(density) { imgH.toDp() })
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val k = scale.value
                    drawMap(
                        floor = floor,
                        imgW = imgW,
                        imgH = imgH,
                        targetRoom = targetRoom,
                        pulse = pulse,
                        zoom = k,
                        numberLayouts = numberLayouts,
                        minScreenPxPerDp = with(density) { 1.dp.toPx() },
                        baseFontPx = baseFontPx
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            fun zoomTo(z: Float) {
                val target = z.coerceIn(restZoom(), 8f)
                val cx = viewW / 2f
                val cy = viewH / 2f
                val bx = (cx - offsetX.value) / scale.value
                val by = (cy - offsetY.value) / scale.value
                scope.launch {
                    scale.animateTo(target, tween(220))
                }
                scope.launch {
                    offsetX.animateTo(clampX(cx - bx * target, target), tween(220))
                }
                scope.launch {
                    offsetY.animateTo(clampY(cy - by * target, target), tween(220))
                }
            }

            ZoomButton(Icons.Default.Add) { zoomTo(scale.value * 1.5f) }
            ZoomButton(Icons.Default.Remove) { zoomTo(scale.value / 1.5f) }
            ZoomButton(Icons.Default.ZoomOutMap) {
                scope.launch {
                    val z = restZoom()
                    scale.animateTo(z, tween(250))
                    offsetX.animateTo(clampX((viewW - imgW * z) / 2f, z), tween(250))
                    offsetY.animateTo(clampY((viewH - imgH * z) / 2f, z), tween(250))
                }
            }
        }
    }
}

@Composable
private fun ZoomButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(3.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier = Modifier
            .size(42.dp)
            .bouncyClickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ColorBrandBlue,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun DrawScope.drawMap(
    floor: FloorMap,
    imgW: Float,
    imgH: Float,
    targetRoom: MapRoom?,
    pulse: Float,
    zoom: Float,
    numberLayouts: Map<String, TextLayoutResult>,

    minScreenPxPerDp: Float,

    baseFontPx: Float
) {
    val unit = 1f / zoom

    if (targetRoom != null) {
        val x = (targetRoom.x - targetRoom.w / 2f) * imgW
        val y = (targetRoom.y - targetRoom.h / 2f) * imgH
        val w = targetRoom.w * imgW
        val h = targetRoom.h * imgH
        val corner = 5f * unit

        drawRoundRect(
            color = HIGHLIGHT_COLOR.copy(alpha = 0.16f * pulse),
            topLeft = Offset(x - 5f * unit, y - 5f * unit),
            size = Size(w + 10f * unit, h + 10f * unit),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner + 3f * unit)
        )
        drawRoundRect(
            color = ROOM_FILL,
            topLeft = Offset(x, y),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner)
        )
        drawRoundRect(
            color = if (targetRoom.status == "nonexistent") DEAD_COLOR else ROOM_BORDER,
            topLeft = Offset(x, y),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner),
            style = Stroke(2.5f * unit)
        )
    }

    // Кабинеты: единый масштаб цифр в пределах этажа + белый ореол (читаемость поверх стен)
    val rawFits = HashMap<String, Float>()
    floor.rooms.forEach { r ->
        val layout = numberLayouts[r.id] ?: return@forEach
        if (r.number.isBlank()) return@forEach
        val wPx = r.w * imgW
        val hPx = r.h * imgH
        val vertical = r.h > r.w * 1.6f
        val fit = if (vertical) {
            (wPx * 0.62f) / layout.size.height.toFloat()
        } else {
            minOf(
                (hPx * 0.52f) / layout.size.height.toFloat(),
                (wPx * 0.88f) / layout.size.width.toFloat()
            )
        }
        if (fit > 0f) rawFits[r.id] = fit
    }
    val sortedFits = rawFits.values.toFloatArray().also { it.sort() }
    val medianFit = if (sortedFits.isEmpty()) 1f else sortedFits[sortedFits.size / 2]

    floor.rooms.forEach { r ->
        val layout = numberLayouts[r.id] ?: return@forEach
        if (r.number.isBlank()) return@forEach
        val raw = rawFits[r.id] ?: return@forEach

        // сглаживание разброса: похожие кабинеты получают близкий размер шрифта
        val damped = medianFit + (raw - medianFit) * 0.45f
        val fit = damped.coerceIn(medianFit * 0.70f, medianFit * 1.35f)
        if (fit <= 0f) return@forEach

        val cx = r.x * imgW
        val cy = r.y * imgH
        val vertical = r.h > r.w * 1.6f
        val color = if (r.status == "nonexistent") DEAD_COLOR else Color(0xFF0B3564)

        withTransform({
            if (vertical) rotate(90f, Offset(cx, cy))
            scale(fit, fit, Offset(cx, cy))
        }) {
            val topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f)
            drawText(
                textLayoutResult = layout,
                topLeft = topLeft,
                color = Color.White,
                // Ореол чуть шире, чтобы тонкие цифры оставались читаемыми поверх стен
                drawStyle = Stroke(width = layout.size.height * 0.22f, join = StrokeJoin.Round)
            )
            drawText(
                textLayoutResult = layout,
                topLeft = topLeft,
                color = color
            )
        }
    }
}

private fun mapRes(floorId: String): Int = when (floorId) {
    "2" -> com.politex.R.drawable.map_floor2
    "3-4" -> com.politex.R.drawable.map_floor34
    else -> com.politex.R.drawable.map_floor1
}
