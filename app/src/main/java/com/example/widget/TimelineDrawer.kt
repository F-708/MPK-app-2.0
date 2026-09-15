package com.example.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.data.local.MpkDatabase
import com.example.data.model.BellItem
import com.example.data.model.CollegeBellSchedule
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Рисует шкалу учебного дня (таймлайн) в Bitmap для виджета «Бегущая строка».
 *
 * Логика совпадает с экраном «Звонки»:
 * - серая линия на весь учебный день (от начала 1-го до конца последнего урока);
 * - синяя зона до конца последнего урока ПО ФАКТУ расписания группы;
 * - риски на границах уроков и перемен;
 * - точка текущего времени: синяя на занятиях, серая после;
 * - в широком режиме подписи номеров уроков и текущее время.
 */
object TimelineDrawer {

    private val BLUE = Color.parseColor("#0072CE")
    private val BLUE_SOFT = Color.parseColor("#CCE3F5")
    private val GRAY = Color.parseColor("#94A3B8")
    private val GRAY_SOFT = Color.parseColor("#CBD5E1")
    private val TEXT_GRAY = Color.parseColor("#64748B")
    private val CARD_BG = Color.WHITE

    /** Ширина в dp, начиная с которой рисуем подписи уроков. */
    private const val LABELS_MIN_DP = 110

    fun draw(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        horizontal: Boolean,
        density: Float
    ): Bitmap {
        val cal = Calendar.getInstance()
        val dow = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 1
        }
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val bells = CollegeBellSchedule.getBellsForDay(dow)

        // Последний урок по факту расписания выбранной группы
        val groupName = WidgetUpdateHelper.getSelectedGroup(context)
        val lastLesson = try {
            kotlinx.coroutines.runBlocking {
                val lessons = MpkDatabase.getInstance(context)
                    .lessonDao()
                    .getLessonsForDaySync(groupName, dow)
                // берём последний снапшот даты, чтобы не смешивать архивные дни
                val latestDate = lessons.filter { it.dateString.isNotBlank() }
                    .maxOfOrNull { it.dateString.replace("-", ".") }
                val todayLessons = if (latestDate != null) {
                    lessons.filter { it.dateString.isBlank() || it.dateString.replace("-", ".") == latestDate }
                } else lessons
                todayLessons.maxOfOrNull { it.lessonNumber }
            }
        } catch (_: Exception) {
            null
        }

        val first = bells.minOf { it.startMinutes }
        val last = bells.maxOf { it.endMinutes }
        val span = (last - first).coerceAtLeast(1)
        val blueEnd = lastLesson?.let { num ->
            bells.filter { !it.isInfoHour }.lastOrNull { it.lessonNumber <= num }?.endMinutes
        } ?: last
        val isOnLessons = nowMinutes <= blueEnd

        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(CARD_BG)

        val dp = density
        val pad = 8 * dp
        val axisWidth = 3 * dp
        val tickLen = 5 * dp

        // Геометрия оси
        val axisX: Float
        val axisY: Float
        val fromPos: Float
        val toPos: Float
        if (horizontal) {
            axisY = heightPx / 2f
            axisX = 0f
            fromPos = pad
            toPos = widthPx - pad
        } else {
            axisX = widthPx / 2f
            axisY = 0f
            fromPos = pad
            toPos = heightPx - pad
        }
        val length = toPos - fromPos

        fun pos(minutes: Int): Float = fromPos + (minutes - first).toFloat() / span * length

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = axisWidth
            strokeCap = Paint.Cap.ROUND
        }
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = (1.5 * dp).roundToInt().toFloat()
        }

        // Серая базовая линия
        if (horizontal) {
            linePaint.color = GRAY_SOFT
            canvas.drawLine(fromPos, axisY, toPos, axisY, linePaint)
        } else {
            linePaint.color = GRAY_SOFT
            canvas.drawLine(axisX, fromPos, axisX, toPos, linePaint)
        }

        // Синяя зона до конца последнего фактического урока
        val bluePos = pos(blueEnd).coerceIn(fromPos, toPos)
        linePaint.color = BLUE
        if (horizontal) {
            canvas.drawLine(pos(first), axisY, bluePos, axisY, linePaint)
        } else {
            canvas.drawLine(axisX, pos(first), axisX, bluePos, linePaint)
        }

        // Отметка конца занятий
        tickPaint.color = BLUE
        tickPaint.strokeWidth = 2 * dp
        if (horizontal) {
            canvas.drawLine(bluePos, axisY - tickLen, bluePos, axisY + tickLen, tickPaint)
        } else {
            canvas.drawLine(axisX - tickLen, bluePos, axisX + tickLen, bluePos, tickPaint)
        }

        // Риски границ уроков + подписи (в широком режиме)
        val showLabels = (if (horizontal) heightPx else widthPx) / dp >= LABELS_MIN_DP
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9 * dp
            color = TEXT_GRAY
            textAlign = Paint.Align.CENTER
        }
        bells.forEach { bell: BellItem ->
            val inBlue = bell.startMinutes < blueEnd
            tickPaint.color = if (inBlue) BLUE else GRAY_SOFT
            tickPaint.strokeWidth = (1.5 * dp).roundToInt().toFloat()
            listOf(bell.startMinutes, bell.endMinutes).forEach { minutes ->
                val p = pos(minutes)
                if (horizontal) {
                    canvas.drawLine(p, axisY - tickLen, p, axisY + tickLen, tickPaint)
                } else {
                    canvas.drawLine(axisX - tickLen, p, axisX + tickLen, p, tickPaint)
                }
            }
            // Номер урока рядом с началом
            if (showLabels && !bell.isInfoHour) {
                labelPaint.color = if (inBlue) BLUE else TEXT_GRAY
                val p = pos(bell.startMinutes)
                if (horizontal) {
                    canvas.drawText("${bell.lessonNumber}", p + 6 * dp, axisY - 8 * dp, labelPaint)
                } else {
                    // слева от линии
                    labelPaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText("${bell.lessonNumber}", axisX - 8 * dp, p + 3 * dp, labelPaint)
                    labelPaint.textAlign = Paint.Align.CENTER
                }
            }
        }

        // Точка текущего времени
        val dotPos = pos(nowMinutes).coerceIn(fromPos, toPos)
        val dotColor = if (isOnLessons) BLUE else GRAY
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUE_SOFT }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor }
        val ringRadius = 7 * dp
        val dotRadius = 4.5f * dp
        val cx = if (horizontal) dotPos else axisX
        val cy = if (horizontal) axisY else dotPos
        if (isOnLessons) {
            canvas.drawCircle(cx, cy, ringRadius, ringPaint)
        }
        canvas.drawCircle(cx, cy, dotRadius, dotPaint)

        // Подпись текущего времени у точки (в широком режиме)
        if (showLabels) {
            val timeText = String.format("%02d:%02d", nowMinutes / 60, nowMinutes % 60)
            labelPaint.color = dotColor
            labelPaint.textSize = 9 * dp
            if (horizontal) {
                val tx = (dotPos + 6 * dp).coerceAtMost(widthPx - 20 * dp)
                canvas.drawText(timeText, tx, axisY + 16 * dp, labelPaint)
            } else {
                labelPaint.textAlign = Paint.Align.CENTER
                val ty = (dotPos + 16 * dp).coerceAtMost(heightPx - 4 * dp)
                canvas.drawText(timeText, axisX, ty, labelPaint)
            }
        }

        return bitmap
    }
}
