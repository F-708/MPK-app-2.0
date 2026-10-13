package com.politex.ui.teacher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.ui.theme.MyApplicationTheme

// TR (п.5): «доступность через токены» (масштаб 48dp/13sp) ОТМЕНЕНА — вместо неё будет
// глобальный масштаб в W4. Teacher-экраны рендерятся в той же MyApplicationTheme, что и
// студенческие (MainActivity применяет палитру до ветвления flavor). TeacherTheme сведён к
// чистой оболочке MyApplicationTheme.
//
// TeacherMetrics/LocalTeacherMetrics оставлены ТОЛЬКО чтобы не ломать компиляцию экранов,
// которые ещё не переписаны (TeacherMoreScreen/Onboarding/GroupLessons/Week). Новые экраны
// (TeacherHomeScreen/TeacherNav) их НЕ используют — там inline-литералы как в оригинале.
data class TeacherMetrics(
    val hitTarget: Dp = 48.dp,
    val chipText: TextUnit = 13.sp,
    val body: TextUnit = 16.sp,
    val buttonText: TextUnit = 14.sp,
)

val LocalTeacherMetrics = staticCompositionLocalOf { TeacherMetrics() }

@Composable
fun TeacherTheme(content: @Composable () -> Unit) {
    MyApplicationTheme(content = content)
}
