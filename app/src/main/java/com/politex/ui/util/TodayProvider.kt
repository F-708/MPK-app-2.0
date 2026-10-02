package com.politex.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Calendar

/**
 * SUI-01: «сегодня» с ключом календарного дня.
 *
 * `remember { Calendar.getInstance() }` вычислялся один раз на время жизни композиции:
 * приложение, пережившее полночь в фоне, утром показывало вчерашний день под заголовком
 * «СЕГОДНЯ». Ключ — номер дня [dayKeyOf], он пересчитывается на каждом ON_RESUME, поэтому
 * после полуночи и после возврата из фона «сегодня» обновляется, а внутри сессии — стабилен.
 */
@Composable
fun rememberToday(): Calendar {
    val lifecycleOwner = LocalLifecycleOwner.current
    var dayKey by remember { mutableLongStateOf(dayKeyOf(Calendar.getInstance())) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) dayKey = dayKeyOf(Calendar.getInstance())
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return remember(dayKey) { Calendar.getInstance() }
}

/**
 * SUI-01: однозначный номер календарного дня (год + день года). Одинаков для любого времени
 * суток одного дня и различается у соседних дней — ровно то, что нужно ключом remember.
 * Чистая функция ради юнит-теста.
 */
internal fun dayKeyOf(cal: Calendar): Long =
    cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR)

/**
 * SUI-03: минуты от начала суток. Ключ поминутного тика — с ним «текущая пара» и
 * «уроки кончились» пересчитываются на границе урока без переоткрытия экрана.
 */
internal fun minutesOfDay(cal: Calendar = Calendar.getInstance()): Int =
    cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

/** SUI-03: тикающая «текущая минута» (раз в минуту), ключ для remember «идущего урока». */
@Composable
fun rememberNowMinutes(): Int {
    var nowMinutes by remember { mutableIntStateOf(minutesOfDay()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            nowMinutes = minutesOfDay()
        }
    }
    return nowMinutes
}
