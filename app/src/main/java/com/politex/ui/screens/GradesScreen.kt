package com.politex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.politex.data.model.GroupInfo
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorTextMuted

/**
 * Ф4: «Оценки» — заглушка-каркас. Наполнение (предметы из MpkCurriculum + средний балл,
 * категории обычный/практические/контрольные/лабораторные(зачёт)/экзамен, кнопки 1-10,
 * даты и подписи работ, настраиваемое дерево агрегации drag-and-drop, формула по умолчанию
 * ((обычные+практ)/2 + контр)/2 + экзамен)/2) — отдельная волна агента G (MASTER-PLAN фаза 4.2).
 */
@Composable
fun GradesScreen(
    groupInfo: GroupInfo,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Оценки — скоро",
            color = ColorTextMuted
        )
    }
}
