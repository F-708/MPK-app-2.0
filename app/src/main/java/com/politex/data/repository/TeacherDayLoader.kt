package com.politex.data.repository

import android.content.Context
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.TeacherLessonEntity
import com.politex.data.local.entity.TeacherOverrideEntity
import com.politex.data.model.CollegeBellSchedule
import com.politex.data.network.MpkScheduleParser
import com.politex.data.network.TeacherSlot
import com.politex.ui.teacher.TeacherProfile
import com.politex.util.DateFormats
import com.politex.util.Dow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Calendar

// ——— Перенос из CollegeScreen (AA5/R2#18), семантика не изменилась ———

internal fun teacherTargetCalendar(
    dayOffset: Int,
    customDate: Calendar?,
    base: Calendar = Calendar.getInstance(),
): Calendar =
    customDate ?: (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, dayOffset) }

// AA5: в воскресенье документа преподавателей не существует (проверено curl: вс — 404,
// сб/пн — 200), поэтому по умолчанию сразу открываем понедельник, а не «сегодня».
internal fun defaultTeacherDayOffset(todayDow: Int): Int = if (todayDow == 7) 1 else 0

internal fun teacherSyncErrorMessage(
    e: Throwable,
    dateKey: String,
): String {
    val msg = e.message.orEmpty()
    return if (msg.contains("не найден") || msg.contains("вместо") || msg.contains("другой день")) {
        "Расписание преподавателя на $dateKey ещё не опубликовано"
    } else {
        "Не удалось загрузить расписание (проверьте интернет)"
    }
}

internal data class TeacherDayLoad(
    val slots: List<TeacherSlot>,
    val error: String,
)

// AA5: кэш-первым, сеть один раз; ЛЮБАЯ ошибка (Result.failure или исключение)
// превращается в текст — вызывающий код всегда может сбросить loading.
internal suspend fun loadTeacherDay(
    dateKey: String,
    fromDatabase: suspend () -> List<TeacherSlot>,
    sync: suspend () -> Result<Int>,
): TeacherDayLoad {
    val cached = fromDatabase()
    if (cached.isNotEmpty()) return TeacherDayLoad(cached, "")

    var slots = cached
    var error = ""
    runCatching { sync() }
        .onFailure { if (it is CancellationException) throw it }
        .getOrElse { Result.failure(it) }
        .fold(
            onSuccess = { slots = fromDatabase() },
            onFailure = { error = teacherSyncErrorMessage(it, dateKey) },
        )
    return TeacherDayLoad(slots, error)
}

// ——— Экран «Мой день» преподавателя (SPEC §4.1 фича 3, §5.2, §6.3) ———

data class TeacherDayLesson(
    val lessonNumber: Int,
    val subject: String,
    val groups: List<String>,
    val room: String,
    val start: String,
    val end: String,
    val startMinutes: Int,
    val endMinutes: Int,
    /** Личная правка преподавателя (SPEC §5.4) — на карточке маркер ✎ «ваша правка». */
    val overridden: Boolean = false,
    /** SOMEONE_ELSE: пару веду не я. Статусы ИДЁТ/СЛЕДУЮЩАЯ и напоминания её игнорируют. */
    val isReplacement: Boolean = false,
)

data class TeacherDay(
    val lessons: List<TeacherDayLesson>,
    val ambiguous: Boolean,
    val error: String,
    val isSynced: Boolean,
    val lastSyncEpoch: Long,
    /** SPEC §5.4: правки, сброшенные полевым diff'ом при этом синке (для плашки/уведомления). */
    val resets: List<OverrideReset> = emptyList(),
)

/** Причина сброса правки — конкретное поле конкретной пары (SPEC §5.4). */
enum class OverrideResetReason { ROOM_CHANGED, TIME_CHANGED, SUBJECT_CHANGED, GROUPS_CHANGED, LESSON_GONE }

data class OverrideReset(
    val dateString: String,
    val lessonNumber: Int,
    val reason: OverrideResetReason,
    val newRoom: String = "",
    val newStart: String = "",
    val newEnd: String = "",
)

data class NextLesson(
    val dateString: String,
    val dow: Int,
    val lesson: TeacherDayLesson,
)

enum class LessonStatus { PAST, NOW, NEXT, UPCOMING }

/** Чистые функции экрана «Мой день» — без Android, покрыты юнит-тестами. */
object TeacherDayLogic {
    private val SHORT_DOW = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

    // Конвенция проекта: сравнение только по минутам дня из Calendar, dd.MM.yyyy.
    fun statuses(
        lessons: List<TeacherDayLesson>,
        nowMinutes: Int,
        isToday: Boolean,
    ): List<LessonStatus> {
        if (!isToday) return List(lessons.size) { LessonStatus.UPCOMING }
        var nextAssigned = false
        return lessons.map { l ->
            when {
                // «Замена — веду не я» и пара без времени не занимают ИДЁТ/СЛЕДУЮЩАЯ.
                l.startMinutes < 0 || l.isReplacement -> LessonStatus.UPCOMING
                nowMinutes in l.startMinutes..l.endMinutes -> LessonStatus.NOW
                nowMinutes > l.endMinutes -> LessonStatus.PAST
                !nextAssigned -> {
                    nextAssigned = true
                    LessonStatus.NEXT
                }
                else -> LessonStatus.UPCOMING
            }
        }
    }

    /** Оверлей личных правок поверх строк teacher_lessons (SPEC §5.4), ключ (dateString, lessonNumber). */
    fun applyOverrides(
        dateString: String,
        lessons: List<TeacherDayLesson>,
        overrides: List<TeacherOverrideEntity>,
    ): List<TeacherDayLesson> {
        val byLesson = overrides.filter { it.dateString == dateString }.associateBy { it.lessonNumber }
        if (byLesson.isEmpty() || lessons.isEmpty()) return lessons
        return lessons.mapNotNull { l ->
            val o = byLesson[l.lessonNumber] ?: return@mapNotNull l
            when (o.kind) {
                TeacherOverrideEntity.KIND_NO_LESSON -> null
                TeacherOverrideEntity.KIND_OTHER_ROOM -> l.copy(room = o.room.trim().ifBlank { l.room }, overridden = true)
                TeacherOverrideEntity.KIND_SOMEONE_ELSE -> l.copy(overridden = true, isReplacement = true)
                else -> l
            }
        }
    }

    /**
     * Правки старше maxAgeDays дней (Calendar-разбор dd.MM.yyyy — конвенция проекта,
     * НЕ лексикографика). Неразобранную дату не трогаем: чужие данные не удаляем.
     */
    fun overridesToPrune(
        overrides: List<TeacherOverrideEntity>,
        today: Calendar,
        maxAgeDays: Int = 60,
    ): List<TeacherOverrideEntity> {
        if (overrides.isEmpty()) return emptyList()
        val cutoff =
            (today.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, -maxAgeDays)
            }
        return overrides.filter { o ->
            val cal = MpkScheduleParser.parseDotDate(o.dateString) ?: return@filter false
            cal.timeInMillis < cutoff.timeInMillis
        }
    }

    /**
     * Полевой diff-сброс (SPEC §5.4): правка пары сбрасывается ТОЛЬКО если в новом доке
     * изменилось конкретное поле этой пары (room/time/subject/groups) или пара исчезла.
     * beforeDoc/afterDoc — СЫРЫЕ строки документа (без оверлея), ключ (dateString, lessonNumber).
     * Пары, которой не было в старом доке, правка не трогается (fail-safe: не удаляем без доказательства).
     * На номер пары может приходиться несколько строк документа (две группы в одном слоте) —
     * сравниваем весь набор строк, иначе изменение в первой из них прошло бы молча.
     */
    fun diffOverrideResets(
        dateString: String,
        beforeDoc: List<TeacherDayLesson>,
        afterDoc: List<TeacherDayLesson>,
        overrides: List<TeacherOverrideEntity>,
    ): List<OverrideReset> {
        val mine = overrides.filter { it.dateString == dateString }
        if (mine.isEmpty()) return emptyList()
        val before = beforeDoc.groupBy { it.lessonNumber }
        val after = afterDoc.groupBy { it.lessonNumber }
        return mine
            .mapNotNull { o ->
                val b = before[o.lessonNumber] ?: return@mapNotNull null
                val a = after[o.lessonNumber]
                val reason: OverrideResetReason? =
                    when {
                        a == null -> OverrideResetReason.LESSON_GONE
                        sig(b) { it.room } != sig(a) { it.room } -> OverrideResetReason.ROOM_CHANGED
                        sig(b) { "${it.start}-${it.end}" } != sig(a) { "${it.start}-${it.end}" } -> OverrideResetReason.TIME_CHANGED
                        sig(b) { it.subject } != sig(a) { it.subject } -> OverrideResetReason.SUBJECT_CHANGED
                        sig(b) { groupsKey(it.groups) } != sig(a) { groupsKey(it.groups) } -> OverrideResetReason.GROUPS_CHANGED
                        else -> null
                    }
                reason ?: return@mapNotNull null
                OverrideReset(
                    dateString = dateString,
                    lessonNumber = o.lessonNumber,
                    reason = reason,
                    newRoom = a?.mapNotNull { it.room.trim().takeIf(String::isNotBlank) }?.distinct()?.sorted()?.joinToString(", ").orEmpty(),
                    newStart = a?.firstOrNull()?.start.orEmpty(),
                    newEnd = a?.firstOrNull()?.end.orEmpty(),
                )
            }.sortedBy { it.lessonNumber }
    }

    private fun sig(
        rows: List<TeacherDayLesson>,
        pick: (TeacherDayLesson) -> String,
    ): String = rows.map { pick(it).trim() }.sorted().joinToString("|")

    private fun groupsKey(groups: List<String>): String =
        groups.map { it.trim() }.filter { it.isNotEmpty() }.sorted().joinToString(",")

    /** «07.10, вт» — день в тексте уведомления о сбросе. */
    fun dayLabel(dateString: String): String {
        val cal = MpkScheduleParser.parseDotDate(dateString) ?: return dateString
        return dateString.dropLast(5) + ", " + shortDow(Dow.of(cal))
    }

    /** Текст плашки/уведомления об одном сбросе (SPEC §5.4: молчаливый сброс запрещён). */
    fun resetMessage(reset: OverrideReset): String {
        val tail =
            when (reset.reason) {
                OverrideResetReason.ROOM_CHANGED -> "в доке теперь " + reset.newRoom.ifBlank { "без кабинета" }
                OverrideResetReason.TIME_CHANGED -> "в доке теперь ${reset.newStart}–${reset.newEnd}"
                OverrideResetReason.SUBJECT_CHANGED, OverrideResetReason.GROUPS_CHANGED -> "в доке теперь другой предмет/группы"
                OverrideResetReason.LESSON_GONE -> "пары больше нет в документе"
            }
        return "Расписание обновлено: ваша правка на ${dayLabel(reset.dateString)}, " +
            "${reset.lessonNumber}-я пара сброшена — $tail"
    }

    fun shortDow(dow: Int): String = SHORT_DOW[(dow - 1).coerceIn(0, 6)]

    fun emptyDayText(
        dow: Int,
        next: NextLesson?,
        isToday: Boolean = true,
    ): String = when {
        dow == 7 -> "Выходной"
        next != null ->
            (if (isToday) "Сегодня пар нет." else "На этот день пар нет.") +
                " Следующая — ${shortDow(next.dow)} ${next.lesson.start}" +
                if (next.lesson.room.isNotBlank()) ", каб. ${next.lesson.room}" else ""
        else -> if (isToday) "Сегодня пар нет." else "На этот день пар нет."
    }
}

// Фильтр строк teacher_lessons через matchRowToTeacher (однофамильцы) + день недели.
// Точная копия прежней логики CollegeScreen.TeacherScheduleTab.
internal fun matchRowsForTeacher(
    rows: List<TeacherLessonEntity>,
    selected: Teacher,
    allTeachers: List<Teacher>,
    dow: Int,
): Pair<List<TeacherLessonEntity>, Boolean> {
    val shown =
        rows
            .mapNotNull { row ->
                val m = TeacherInsights.matchRowToTeacher(row.teacherName, selected, allTeachers)
                if (m == TeacherRowMatch.NO) null else row to m
            }.filter { (row, _) -> row.dayOfWeek == dow || row.dayOfWeek == 0 }
    return shown.map { it.first } to shown.any { it.second == TeacherRowMatch.AMBIGUOUS }
}

private fun splitGroups(raw: String): List<String> = raw.split(", ").filter { it.isNotBlank() }

private fun toLesson(
    row: TeacherLessonEntity,
    dow: Int,
): TeacherDayLesson {
    val bell = CollegeBellSchedule.getBellsForDay(dow).firstOrNull { it.lessonNumber == row.lessonNumber }
    return TeacherDayLesson(
        lessonNumber = row.lessonNumber,
        subject = row.subject,
        groups = splitGroups(row.groups),
        room = row.room,
        start = bell?.start ?: "",
        end = bell?.end ?: "",
        startMinutes = bell?.startMinutes ?: -1,
        endMinutes = bell?.endMinutes ?: -1,
    )
}

private fun toLesson(
    slot: TeacherSlot,
    dow: Int,
): TeacherDayLesson {
    val bell = CollegeBellSchedule.getBellsForDay(dow).firstOrNull { it.lessonNumber == slot.lessonNumber }
    return TeacherDayLesson(
        lessonNumber = slot.lessonNumber,
        subject = slot.subject,
        groups = slot.groups,
        room = slot.room,
        start = bell?.start ?: "",
        end = bell?.end ?: "",
        startMinutes = bell?.startMinutes ?: -1,
        endMinutes = bell?.endMinutes ?: -1,
    )
}

private const val PROFILE_PREFS = "mpk_teacher_profile"
private const val KEY_LAST_SYNC = "lastSyncEpoch"

object TeacherDayLoader {
    fun readLastSyncEpoch(context: Context): Long =
        context
            .getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNC, 0L)

    fun markSyncedNow(context: Context) {
        context
            .getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
    }

    suspend fun loadDay(
        context: Context,
        repository: ScheduleRepository,
        profile: TeacherProfile,
        dateString: String,
        allTeachers: List<Teacher>? = null,
    ): TeacherDay =
        withContext(Dispatchers.IO) {
            val cal = MpkScheduleParser.parseDotDate(dateString)
            val dow = cal?.let { Dow.of(it) } ?: 0
            val teachers = allTeachers ?: TeachersRepository(context).loadTeachers()
            val selected = Teacher(name = profile.name)
            var ambiguous = false
            var resets: List<OverrideReset> = emptyList()
            val result =
                loadTeacherDay(
                    dateKey = dateString,
                    fromDatabase = {
                        val (rows, amb) = readMatchedRows(repository, profile, selected, teachers, dateString, dow)
                        ambiguous = amb
                        rows.map {
                            TeacherSlot(it.lessonNumber, splitGroups(it.groups), it.subject, it.room, it.dayOfWeek)
                        }
                    },
                    sync = {
                        val (res, diffResets) =
                            syncDayWithOverrideDiff(context, repository, profile, dateString, cal, teachers)
                        resets = diffResets
                        res
                    },
                )
            val overrideDao = MpkDatabase.getInstance(context).teacherOverrideDao()
            val overrides = overrideDao.forDate(dateString)
            // ponytail: all() читает всю таблицу правок (личная, единицы строк);
            // при росте — @Query DELETE ... WHERE dateString < :cutoff
            TeacherDayLogic.overridesToPrune(overrideDao.all(), Calendar.getInstance())
                .forEach { overrideDao.deleteForLesson(it.dateString, it.lessonNumber) }
            TeacherDay(
                lessons = TeacherDayLogic.applyOverrides(dateString, result.slots.map { toLesson(it, dow) }, overrides),
                ambiguous = ambiguous,
                error = result.error,
                isSynced = repository.hasTeacherScheduleForDate(dateString),
                lastSyncEpoch = readLastSyncEpoch(context),
                resets = resets,
            )
        }

    /**
     * SPEC §5.4: синк дня + ПОЛЕВОЙ diff-сброс личных правок вокруг него.
     * Единственная точка, где replaceForDate может вернуть неверный кабинет в правку пользователя:
     * снимок пар ДО и ПОСЛЕ, сброс только изменившегося поля. Никакого «док новее → сбросить день».
     * Возвращает (результат синка, список сбросов).
     */
    suspend fun syncDayWithOverrideDiff(
        context: Context,
        repository: ScheduleRepository,
        profile: TeacherProfile,
        dateString: String,
        cal: Calendar?,
        allTeachers: List<Teacher>? = null,
        force: Boolean = false,
    ): Pair<Result<Int>, List<OverrideReset>> =
        withContext(Dispatchers.IO) {
            val sync: suspend () -> Result<Int> = {
                cal?.let { repository.syncTeacherScheduleForDate(it, force) }
                    ?: Result.failure(IOException("Неверная дата $dateString"))
            }
            if (cal == null) return@withContext runSync(sync) to emptyList()

            val overrideDao = MpkDatabase.getInstance(context).teacherOverrideDao()
            val overrides = overrideDao.forDate(dateString)
            if (overrides.isEmpty()) return@withContext runSync(sync) to emptyList()

            val dow = Dow.of(cal)
            val teachers = allTeachers ?: TeachersRepository(context).loadTeachers()
            val selected = Teacher(name = profile.name)
            val before = rawLessons(repository, profile, selected, teachers, dateString, dow)
            val result = runSync(sync)
            if (result.isFailure) return@withContext result to emptyList()
            val after = rawLessons(repository, profile, selected, teachers, dateString, dow)
            val resets = TeacherDayLogic.diffOverrideResets(dateString, before, after, overrides)
            if (resets.isNotEmpty()) {
                resets.forEach { overrideDao.deleteForLesson(it.dateString, it.lessonNumber) }
                // SPEC §5.4: системное уведомление — единственная точка на ВСЕХ путях синка
                // (воркер, home/week refresh, loadDay). Падение нотификатора не рвёт синк (GG8).
                runCatching { com.politex.util.TeacherNotifier.sendOverrideResets(context, resets) }
            }
            result to resets
        }

    /** Тот же контракт, что у loadTeacherDay: исключение синка не уходит наверх, а становится failure. */
    private suspend fun runSync(block: suspend () -> Result<Int>): Result<Int> =
        runCatching { block() }
            .onFailure { if (it is CancellationException) throw it }
            .getOrElse { Result.failure(it) }

    private suspend fun rawLessons(
        repository: ScheduleRepository,
        profile: TeacherProfile,
        selected: Teacher,
        allTeachers: List<Teacher>,
        dateString: String,
        dow: Int,
    ): List<TeacherDayLesson> = readMatchedRows(repository, profile, selected, allTeachers, dateString, dow).first.map { toLesson(it, dow) }

    /** Следующая пара в пределах maxDays дней (кэш; одна попытка синка, если кэш совсем пуст). */
    suspend fun findNextLesson(
        context: Context,
        repository: ScheduleRepository,
        profile: TeacherProfile,
        baseDate: String,
        allTeachers: List<Teacher>? = null,
        maxDays: Int = 7,
    ): NextLesson? =
        withContext(Dispatchers.IO) {
            val base = MpkScheduleParser.parseDotDate(baseDate) ?: return@withContext null
            val teachers = allTeachers ?: TeachersRepository(context).loadTeachers()
            val selected = Teacher(name = profile.name)
            var anyCached = false
            var firstSchoolCal: Calendar? = null
            for (offset in 1..maxDays) {
                val cal = teacherTargetCalendar(offset, null, base)
                val dow = Dow.of(cal)
                if (dow == 7) continue
                if (firstSchoolCal == null) firstSchoolCal = cal
                val dateKey = DateFormats.dotDate(cal)
                if (repository.hasTeacherScheduleForDate(dateKey)) anyCached = true
                firstRow(repository, profile, selected, teachers, dateKey, dow)
                    ?.let { return@withContext NextLesson(dateKey, dow, toLesson(it, dow)) }
            }
            // ponytail: одна попытка синка ближайшего учебного дня; остальное доделает воркер
            val cal = firstSchoolCal
            if (!anyCached && cal != null) {
                val dateKey = DateFormats.dotDate(cal)
                val dow = Dow.of(cal)
                runCatching { repository.syncTeacherScheduleForDate(cal) }
                    .onFailure { if (it is CancellationException) throw it }
                firstRow(repository, profile, selected, teachers, dateKey, dow)
                    ?.let { return@withContext NextLesson(dateKey, dow, toLesson(it, dow)) }
            }
            null
        }

    private suspend fun firstRow(
        repository: ScheduleRepository,
        profile: TeacherProfile,
        selected: Teacher,
        allTeachers: List<Teacher>,
        dateString: String,
        dow: Int,
    ): TeacherLessonEntity? = readMatchedRows(repository, profile, selected, allTeachers, dateString, dow).first.firstOrNull()

    private suspend fun readMatchedRows(
        repository: ScheduleRepository,
        profile: TeacherProfile,
        selected: Teacher,
        allTeachers: List<Teacher>,
        dateString: String,
        dow: Int,
    ): Pair<List<TeacherLessonEntity>, Boolean> {
        val key = profile.surnameKey.ifBlank { TeacherInsights.surnameOf(profile.name) }
        val rows = repository.getTeacherLessonsForDate(key, dateString)
        return matchRowsForTeacher(rows, selected, allTeachers, dow)
    }
}
