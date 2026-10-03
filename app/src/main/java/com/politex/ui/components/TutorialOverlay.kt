package com.politex.ui.components

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.util.bouncyClickable
import com.politex.util.TutorialFlags
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * V14: интерактивные туториалы. Одна фича активна в момент времени; оверлей затемняет
 * всё окно, кроме «дырки» вокруг цели, показывает демо-жест пальцем и ждёт, пока
 * пользователь повторит его сам (успех приходит с экрана через [TutorialController.success]).
 *
 * Координаты: приложение работает в enableEdgeToEdge, поэтому корневой Box MainScreen
 * начинается в (0,0) окна, и [boundsInWindow] якоря совпадают с локальными координатами
 * оверлея — дополнительных пересчётов не нужно.
 */

/** Куда экран пишет свои границы; оверлей читает их по [TutorialFeature.anchorId]. */
object TutorialAnchors {
    val bounds = mutableStateMapOf<String, Rect>()
}

/** Помечает цель туториала: пишет boundsInWindow в [TutorialAnchors] по id. */
fun Modifier.tutorialAnchor(id: String): Modifier =
    this.onGloballyPositioned { coords ->
        TutorialAnchors.bounds[id] = coords.boundsInWindow()
    }

/** Тип демо-жеста. */
enum class TutorialGesture { SWIPE, TAP }

/**
 * Активные фичи и их тексты. [key] — стабильный идентификатор флага пройденности
 * (имя enum), переживает обновление приложения. [altAnchorId] — запасная цель, если
 * основной якорь на экране отсутствует (X39: кабинет ИЛИ преподаватель).
 */
enum class TutorialFeature(
    val anchorId: String,
    val title: String,
    val demoCaption: String,
    val repeatCaption: String,
    val gesture: TutorialGesture,
    val altAnchorId: String? = null,
) {
    TASK_SWIPE(
        anchorId = "task_card_first",
        title = "Удаление свайпом",
        demoCaption = "Свайпните карточку влево — задание удалится. «ОТМЕНИТЬ» вернёт его.",
        repeatCaption = "Повторите свайп",
        gesture = TutorialGesture.SWIPE,
    ),
    SCHEDULE_TAP(
        anchorId = "schedule_room_target",
        altAnchorId = "schedule_teacher_target",
        title = "Кабинет и преподаватель",
        demoCaption = "Нажмите кабинет или преподавателя — откроется карта или карточка.",
        repeatCaption = "Нажмите кабинет или преподавателя",
        gesture = TutorialGesture.TAP,
    ),
    ;

    val key: String get() = name
}

/**
 * Оркестрация по образцу ViewerMode/GradeDetailMode: один объект-синглтон держит активную
 * фичу. Экраны зовут [request] при первой композиции и [success] при реальном результате.
 */
object TutorialController {
    /** Активная фича (null = оверлей скрыт). Публичный var — оверлей гасит его в [clear]. */
    var active by mutableStateOf<TutorialFeature?>(null)

    /** Инкремент = «пользователь повторил жест»; оверлей по нему завершает туториал. */
    var successSignal by mutableIntStateOf(0)
        private set

    /** Показать туториал, если он ещё не пройден и сейчас ничего не активно. */
    fun request(
        ctx: Context,
        feature: TutorialFeature,
    ) {
        if (active == null && !TutorialFlags.isDone(ctx, feature.key)) {
            successSignal = 0
            active = feature
        }
    }

    /** Экран сообщает об успешном результате; засчитывается только для активной фичи. */
    fun success(feature: TutorialFeature) {
        if (active == feature) successSignal++
    }

    fun clear() {
        active = null
        successSignal = 0
    }
}

/**
 * Траектория демо-свайпа: справа налево на 60% ширины [rect]. Две точки (старт, конец);
 * «и обратно» дорисовывает анимация оверлея. Чистая функция — см. TutorialTrajectoryTest.
 */
fun trajectorySwipe(rect: Rect): List<Offset> {
    val y = rect.center.y
    val start = Offset(rect.right - rect.width * 0.15f, y)
    val end = Offset(start.x - rect.width * 0.6f, y)
    return listOf(start, end)
}

/** Траектория демо-тапа: одна точка в центре [rect] (палец пульсирует на месте). */
fun trajectoryTap(rect: Rect): List<Offset> = listOf(rect.center)

// X38: демо проигрывается ПОЛНЫМ циклом (2 жеста «вперёд-назад» с паузами), и только
// потом подпись меняется на «повторите» — отдельным таймером на TOTAL_DEMO_MS.
private const val DEMO_TWEEN_MS = 1100
private const val DEMO_PAUSE_MS = 600L
private const val DEMO_CYCLES = 2
private const val TOTAL_DEMO_MS = DEMO_CYCLES * 2 * (DEMO_TWEEN_MS + DEMO_PAUSE_MS)

private enum class TutorialPhase { DEMO, REPEAT }

/**
 * Хостится в корневом Box MainScreen поверх контента. Ничего не рисует, пока
 * [TutorialController.active] == null или якорь ещё не измерен.
 */
@Composable
fun TutorialOverlay(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val feature = TutorialController.active ?: return

    val anchor =
        TutorialAnchors.bounds[feature.anchorId]
            ?: feature.altAnchorId?.let { TutorialAnchors.bounds[it] }
    // Ждём первый замер цели: без rect рисовать нечего, а «пустой» оверлей перехватил бы весь экран.
    if (anchor == null || anchor.width <= 0f || anchor.height <= 0f) return

    val scope = rememberCoroutineScope()
    val fade = remember(feature) { Animatable(1f) }
    var phase by remember(feature) { mutableStateOf(TutorialPhase.DEMO) }
    // SWIPE: прогресс 0→1 вдоль траектории (Animatable<Offset> не нужен — точек всего две).
    val demoT = remember(feature) { Animatable(0f) }
    // TAP: пульс масштаба пальца на месте.
    val fingerScale = remember(feature) { Animatable(1f) }
    var containerH by remember { mutableFloatStateOf(0f) }

    val hole =
        with(density) {
            anchor.inflate(8.dp.toPx())
        }
    val brand = ColorBrandFill
    val swipePts = remember(hole, feature) { if (feature.gesture == TutorialGesture.SWIPE) trajectorySwipe(hole) else null }
    val tapPt = remember(hole, feature) { if (feature.gesture == TutorialGesture.TAP) trajectoryTap(hole)[0] else Offset.Zero }

    fun dismiss(markDone: Boolean) {
        scope.launch {
            if (markDone) TutorialFlags.markDone(ctx, feature.key)
            fade.animateTo(0f, tween(300))
            TutorialController.clear()
        }
    }

    // Системное «назад» = «Пропустить».
    BackHandler { dismiss(markDone = true) }

    // Демо-фаза: палец по траектории — полный цикл (2 жеста «вперёд-назад» с паузами).
    // Ключ ТОЛЬКО feature: прежний ключ hole перезапускал эффект при каждом перезамере якоря.
    LaunchedEffect(feature) {
        when (feature.gesture) {
            TutorialGesture.SWIPE ->
                repeat(DEMO_CYCLES) {
                    demoT.animateTo(1f, tween(DEMO_TWEEN_MS))
                    delay(DEMO_PAUSE_MS)
                    demoT.animateTo(0f, tween(DEMO_TWEEN_MS))
                    delay(DEMO_PAUSE_MS)
                }
            TutorialGesture.TAP ->
                repeat(DEMO_CYCLES) {
                    fingerScale.animateTo(0.85f, tween(DEMO_TWEEN_MS))
                    delay(DEMO_PAUSE_MS)
                    fingerScale.animateTo(1f, tween(DEMO_TWEEN_MS))
                    delay(DEMO_PAUSE_MS)
                }
        }
    }

    // X38: подпись «повторите» — ТОЛЬКО после полного демо (delay(TOTAL_DEMO_MS));
    // до этого показан demo-текст.
    LaunchedEffect(feature) {
        delay(TOTAL_DEMO_MS)
        phase = TutorialPhase.REPEAT
    }

    // Фаза «Повторите»: ждём РЕАЛЬНОГО результата с экрана (successSignal).
    val signal = TutorialController.successSignal
    LaunchedEffect(signal, phase, feature) {
        if (phase == TutorialPhase.REPEAT && signal > 0) dismiss(markDone = true)
    }

    // Плашка-подпись: под hole, если цель в верхней части экрана, иначе над hole.
    val gap = with(density) { 12.dp.roundToPx() }
    val below = anchor.center.y < containerH * 0.6f
    val captionY =
        if (below) {
            anchor.bottom.toInt() + gap
        } else {
            -(containerH.toInt() - anchor.top.toInt() + gap)
        }

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .onSizeChanged { containerH = it.height.toFloat() }
            .graphicsLayer { alpha = fade.value },
    ) {
        // X12: Canvas ТОЛЬКО рисует dim+hole+палец — без pointerInput, ничего не перехватывает.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val corner = 4.dp.toPx()
            val full = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
            val holePath =
                Path().apply {
                    addRoundRect(RoundRect(hole, CornerRadius(corner, corner)))
                }
            drawPath(
                path = Path.combine(PathOperation.Difference, full, holePath),
                color = Color.Black.copy(alpha = 0.70f),
            )

            if (phase == TutorialPhase.DEMO) {
                val r = 9.dp.toPx() * fingerScale.value
                val pos =
                    swipePts?.let {
                        val t = demoT.value
                        Offset(it[0].x + (it[1].x - it[0].x) * t, it[0].y)
                    } ?: tapPt
                // Хвост-«палец» вниз-вправо от курсора.
                drawLine(
                    color = Color.White.copy(alpha = 0.9f),
                    start = pos,
                    end = pos + Offset(r * 1.6f, r * 2.2f),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(color = Color.White.copy(alpha = 0.9f), radius = r, center = pos)
                drawCircle(
                    color = brand,
                    radius = r,
                    center = pos,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }

        // Y6: оверлей НЕ перехватывает ввод вообще: dim+hole+палец — чисто визуальные
        // (Canvas без pointerInput), пользователь может работать с приложением сразу,
        // включая целевую зону и всё остальное. Успех ловят колбэки экранов
        // (TutorialController.success), «Пропустить» — явная кнопка ниже и системный back.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd,
        ) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorTopBar,
                modifier =
                Modifier
                    .padding(12.dp)
                    .bouncyClickable { dismiss(markDone = true) },
            ) {
                Text(
                    text = "Пропустить",
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White,
                    ),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (below) Alignment.TopCenter else Alignment.BottomCenter,
        ) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorTopBar,
                modifier =
                Modifier
                    .offset { IntOffset(0, captionY) }
                    .padding(horizontal = 24.dp),
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text =
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(feature.title) }
                            append("\n")
                            append(if (phase == TutorialPhase.DEMO) feature.demoCaption else feature.repeatCaption)
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
