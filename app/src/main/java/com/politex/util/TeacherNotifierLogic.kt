package com.politex.util

import com.politex.data.repository.TeacherDayLesson
import com.politex.data.repository.TeacherDayLogic
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Чистая логика уведомлений преподавателя (SPEC §5.3, §4.1 фичи 5–7) — без Android,
 * покрыта TeacherNotifierLogicTest. Контракт честности §4.2 «д»: лучше промолчать,
 * чем соврать — summaryText возвращает null вместо врущего пуша.
 */
object TeacherNotifierLogic {
    /** SPEC §5.3: сводка не раньше 07:15 (после попытки синка). */
    const val SUMMARY_AT = 7 * 60 + 15

    /** SPEC §5.3: quiet hours 21:00–07:00 — не уровень канала, а логика откладывания. */
    fun quietHours(hourMinute: Int): Boolean = hourMinute >= 21 * 60 || hourMinute < 7 * 60

    /**
     * Сводка отправляется, если есть что сказать: кэш на сегодня НЕ пуст ИЛИ синк
     * сегодня удался (док parsed, но у меня 0 пар — «Сегодня пар нет», это не враньё).
     * Пустой кэш + проваленный синк → молчание (решает summaryText == null).
     */
    fun shouldSendSummary(
        hourMinute: Int,
        sentFlag: Boolean,
        hasCache: Boolean,
        syncOk: Boolean,
        enabled: Boolean,
    ): Boolean = enabled && !sentFlag && hourMinute >= SUMMARY_AT && (hasCache || syncOk)

    fun pairsWord(n: Int): String =
        when {
            n % 10 == 1 && n % 100 != 11 -> "пара"
            n % 10 in 2..4 && n % 100 !in 12..14 -> "пары"
            else -> "пар"
        }

    /** «N пар» — число слотов: на номер пары может быть 2 строки (две группы в одном слоте). */
    fun lessonCount(lessons: List<TeacherDayLesson>): Int = lessons.map { it.lessonNumber }.distinct().size

    /**
     * Текст утренней сводки (SPEC §5.3, три ветки); null = МОЛЧАНИЕ (кэш пуст и синк
     * не дал данных). syncFresh = синк сегодня удался; cacheTime = метка кэша
     * («вчера 21:40», null = ни разу не синхронизировано); syncHm = «HH:MM» свежих данных.
     * Замены (isReplacement) не считаем: эти пары веду не я.
     */
    fun summaryText(
        lessons: List<TeacherDayLesson>,
        syncFresh: Boolean,
        cacheTime: String?,
        syncHm: String,
    ): String? {
        if (!syncFresh) {
            if (lessons.isEmpty()) return null
            val from = if (cacheTime != null) " от $cacheTime" else ""
            return "Сегодня по расписанию с сайта$from — возможны изменения, сверьтесь с чатом учебной части"
        }
        val mine = lessons.filter { !it.isReplacement }.sortedBy { it.lessonNumber }
        if (mine.isEmpty()) return "Сегодня пар нет."
        // «N пар» — число слотов (на номер пары может быть 2 строки: две группы в одном слоте)
        val n = lessonCount(mine)
        val first = mine.first()
        val at = if (first.start.isNotBlank()) "${first.start} — " else ""
        val room = if (first.room.isNotBlank()) ", каб. ${first.room}" else ""
        return "Сегодня $n ${pairsWord(n)}, первая $at${first.subject}$room (данные сайта на $syncHm)"
    }

    /**
     * Метка возраста кэша: «вчера 21:40» / «05.10 21:40» / просто «06:40» (сегодня).
     * null = lastSyncEpoch не записан. Конвенция проекта — Calendar, не лексикографика.
     */
    fun cacheTimeLabel(
        lastSyncEpoch: Long,
        nowEpoch: Long,
    ): String? {
        if (lastSyncEpoch <= 0L) return null
        val then = Calendar.getInstance().apply { timeInMillis = lastSyncEpoch }
        val hm = String.format(Locale.ROOT, "%02d:%02d", then.get(Calendar.HOUR_OF_DAY), then.get(Calendar.MINUTE))
        // ponytail: разница дней через millis/24h — DST не учитываем (в РБ/РФ не переводят)
        val days = (dayStart(nowEpoch) - dayStart(lastSyncEpoch)) / 86_400_000L
        return when (days) {
            0L -> hm
            1L -> "вчера $hm"
            else -> SimpleDateFormat("dd.MM", Locale.ROOT).format(Date(lastSyncEpoch)) + " $hm"
        }
    }

    private fun dayStart(epoch: Long): Long =
        (Calendar.getInstance().apply { timeInMillis = epoch }.clone() as Calendar).run {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            timeInMillis
        }

    /** «сегодня» / «завтра» / «07.10, вт» — для склеенного diff-уведомления. */
    fun dateLabel(
        dateString: String,
        todayString: String,
        tomorrowString: String,
    ): String =
        when (dateString) {
            todayString -> "сегодня"
            tomorrowString -> "завтра"
            else -> TeacherDayLogic.dayLabel(dateString)
        }

    /**
     * Diff кэша ДО/ПОСЛЕ синка по ключу (dateString, lessonNumber) — SPEC §5.3.
     * Возвращает по одной склеенной строке на день («Изменения на завтра: 3-я пара
     * перенесена в 241; добавлена 5-я пара»); пустой список = изменений нет.
     * Порядок дней — порядок ключей map (вызывающий передаёт сегодня→завтра).
     * На номер пары может быть несколько строк (две группы в слоте) — сравниваем агрегаты.
     */
    fun diffMessages(
        before: Map<String, List<TeacherDayLesson>>,
        after: Map<String, List<TeacherDayLesson>>,
        labels: Map<String, String>,
    ): List<String> =
        (before.keys + after.keys).distinct().mapNotNull { date ->
            val items = dayChanges(before[date].orEmpty(), after[date].orEmpty())
            if (items.isEmpty()) null else "Изменения на ${labels[date] ?: date}: ${items.joinToString("; ")}"
        }

    private fun dayChanges(
        before: List<TeacherDayLesson>,
        after: List<TeacherDayLesson>,
    ): List<String> {
        val b = before.groupBy { it.lessonNumber }
        val a = after.groupBy { it.lessonNumber }
        return (b.keys + a.keys).distinct().sorted().mapNotNull { n ->
            val bb = b[n]
            val aa = a[n]
            when {
                bb == null && aa != null -> {
                    val subj = sig(aa) { it.subject }
                    "добавлена $n-я пара" + if (subj.isNotBlank()) " — $subj" else ""
                }
                bb != null && aa == null -> "$n-я пара отменена"
                bb != null && aa != null -> {
                    val parts = mutableListOf<String>()
                    val rb = sig(bb) { it.room }
                    val ra = sig(aa) { it.room }
                    if (rb != ra) parts += if (ra.isNotBlank()) "перенесена в $ra" else "кабинет изменился"
                    val tb = sig(bb) { "${it.start}-${it.end}" }
                    val ta = sig(aa) { "${it.start}-${it.end}" }
                    if (tb != ta) parts += "теперь ${ta.replace("-", "–")}"
                    if (sig(bb) { it.subject } != sig(aa) { it.subject } ||
                        sig(bb) { it.groups.joinToString(",") } != sig(aa) { it.groups.joinToString(",") }
                    ) {
                        parts += "другой предмет/группы"
                    }
                    if (parts.isEmpty()) null else "$n-я пара " + parts.joinToString(", ")
                }
                else -> null
            }
        }
    }

    private fun sig(
        rows: List<TeacherDayLesson>,
        pick: (TeacherDayLesson) -> String,
    ): String = rows.map { pick(it).trim() }.filter { it.isNotBlank() }.sorted().joinToString(", ")

    /** Хэш списка сообщений для дедуп-ключа diff_notified_<date>_<hash> (String.hashCode стабилен). */
    fun diffHash(messages: List<String>): String = messages.joinToString("|").hashCode().toString()

    /**
     * Пары, о которых напоминаем (SPEC §5.3): первая пара дня + пары со сменой кабинета
     * относительно предыдущей. ЗАМЕНА (веду не я) и пары без времени игнорируются.
     */
    fun reminderCandidates(lessons: List<TeacherDayLesson>): List<TeacherDayLesson> {
        val mine = lessons.filter { !it.isReplacement && it.startMinutes >= 0 }.sortedBy { it.startMinutes }
        return mine.filterIndexed { i, l -> i == 0 || l.room.trim() != mine[i - 1].room.trim() }
    }

    /** Пара, о которой пора напомнить: now в окне [start-nMin, start). */
    fun reminderDue(
        lessons: List<TeacherDayLesson>,
        nowMinutes: Int,
        nMin: Int,
    ): TeacherDayLesson? = reminderCandidates(lessons).firstOrNull { nowMinutes in (it.startMinutes - nMin) until it.startMinutes }

    /** Минуты суток следующего будильника напоминания (строго позже now); null = на сегодня всё. */
    fun nextReminderAt(
        lessons: List<TeacherDayLesson>,
        nowMinutes: Int,
        nMin: Int,
    ): Int? = reminderCandidates(lessons).map { it.startMinutes - nMin }.firstOrNull { it > nowMinutes }
}
