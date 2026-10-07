package com.politex.ui.teacher

import com.politex.data.repository.TeacherDayLesson
import com.politex.util.DateFormats
import com.politex.util.Dow
import java.util.Calendar

/**
 * Чистые функции экрана «Неделя» преподавателя (SPEC §4.1 фича 9, §6.4) —
 * без Android, покрыты юнит-тестами (TeacherWeekLogicTest).
 */
object TeacherWeekLogic {
    /** Пн..Сб недели, содержащей `any` (6 независимых копий Calendar; воскресенье — конец ПРЕДЫДУЩЕЙ недели). */
    fun mondayToSaturday(any: Calendar): List<Calendar> {
        val mon = any.clone() as Calendar
        mon.add(Calendar.DAY_OF_YEAR, -(Dow.of(any) - 1))
        return (0..5).map { i -> (mon.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) } }
    }

    fun weekDates(any: Calendar): List<String> = mondayToSaturday(any).map { DateFormats.dotDate(it) }

    /** Сдвиг всей недели на ±days; Calendar сам разруливает переходы месяца/года. */
    fun shiftWeek(
        week: List<Calendar>,
        days: Int,
    ): List<Calendar> = week.map { (it.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, days) } }

    /**
     * Дата вне окна кэша расписания — данные честно «нет», это не ошибка.
     */
    // ponytail: 60 дублирует приватную ARCHIVE_KEEP_DAYS из ScheduleRepository; вынести константу, если окно станет настраиваемым
    fun outsideCacheWindow(
        date: Calendar,
        now: Calendar = Calendar.getInstance(),
        keepDays: Int = 60,
    ): Boolean {
        val from = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -keepDays) }
        val to = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, keepDays) }
        return date.before(from) || date.after(to)
    }

    data class WeekLoad(
        val totalLessons: Int,
        val totalMinutes: Int,
        val bySubject: List<Pair<String, Int>>,
        val byGroup: List<Pair<String, Int>>,
    )

    /**
     * Нагрузка за видимый период: число пар и сумма (endMinutes-startMinutes) по предметам
     * и группам (сортировка — по убыванию минут, затем по имени). Пары без времени не учитываются в часах.
     */
    // ponytail: многогруппная (потоковая) пара засчитывается ЦЕЛИКОМ каждой группе; если нужна точность — делить пропорционально
    fun aggregateLoad(days: List<List<TeacherDayLesson>>): WeekLoad {
        val timed = days.flatten().filter { it.startMinutes >= 0 && it.endMinutes > it.startMinutes }
        val bySubject =
            timed
                .groupBy { it.subject.ifBlank { "Пара" } }
                .mapValues { (_, ls) -> ls.sumOf { it.endMinutes - it.startMinutes } }
        val byGroup =
            timed
                .flatMap { l -> l.groups.map { g -> g to (l.endMinutes - l.startMinutes) } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, ms) -> ms.sum() }
        return WeekLoad(
            totalLessons = days.sumOf { it.size },
            totalMinutes = timed.sumOf { it.endMinutes - it.startMinutes },
            bySubject = sortDesc(bySubject),
            byGroup = sortDesc(byGroup),
        )
    }

    private fun sortDesc(m: Map<String, Int>): List<Pair<String, Int>> =
        m.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }

    /** 750 → «12 ч 30 мин», 120 → «2 ч», 45 → «45 мин», 0 → «0 мин». */
    fun formatMinutes(min: Int): String {
        val h = min / 60
        val m = min % 60
        return when {
            h == 0 -> "$m мин"
            m == 0 -> "$h ч"
            else -> "$h ч $m мин"
        }
    }

    /** Честный счётчик дней с данными: «3 из 6»; null, когда есть все. */
    fun daysWithDataCounter(
        withData: Int,
        total: Int = 6,
    ): String? = if (withData in 0 until total) "$withData из $total" else null
}
