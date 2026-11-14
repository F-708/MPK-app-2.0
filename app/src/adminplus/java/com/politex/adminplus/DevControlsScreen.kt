package com.politex.adminplus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.ui.components.AppCard
import com.politex.ui.components.SubPageHeader
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorOnAccent
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.util.bouncyClickable
import com.politex.weather.DevControlsStore
import com.politex.weather.EffectsMode
import com.politex.weather.Season
import com.politex.weather.SkyMode
import com.politex.weather.currentSeason
import com.politex.weather.label

/**
 * Панель «Оформление и погода» в AdminPlus.
 *
 * Позволяет на месте переключить осадки, небо и сезон, чтобы проверить
 * эмбиент-слой, сезонный декор и виджет, не дожидаясь реальной погоды/даты.
 * Всё хранится локально и по умолчанию в AUTO (обычное поведение).
 */
@Composable
fun DevControlsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var effects by remember { mutableStateOf(DevControlsStore.readEffects(context)) }
    var sky by remember { mutableStateOf(DevControlsStore.readSky(context)) }
    var season by remember { mutableStateOf(DevControlsStore.readSeason(context)) }
    val detectedSeason = remember { currentSeason() }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(ColorBgMain)
            .testTag("dev_controls_screen"),
    ) {
        SubPageHeader(
            title = "Оформление и погода",
            subtitle = "Проверка эффектов и сезонного декора",
            onBack = onBack,
        )
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DevSection(title = "Погодные эффекты", subtitle = "Авто — по прогнозу Open-Meteo") {
                ChipRow(
                    options = EffectsMode.entries.map { it to it.title },
                    selected = effects,
                    testTagPrefix = "dev_effects_",
                ) { picked ->
                    effects = picked
                    DevControlsStore.writeEffects(context, picked)
                }
            }

            DevSection(title = "Небо", subtitle = "Ночной градиент и звёзды") {
                ChipRow(
                    options = SkyMode.entries.map { it to it.title },
                    selected = sky,
                    testTagPrefix = "dev_sky_",
                ) { picked ->
                    sky = picked
                    DevControlsStore.writeSky(context, picked)
                }
            }

            DevSection(
                title = "Сезонный декор",
                subtitle = "Сейчас по календарю: ${detectedSeason.title()}",
            ) {
                ChipRow(
                    options = Season.entries.map { it to it.title() },
                    selected = season,
                    testTagPrefix = "dev_season_",
                ) { picked ->
                    season = picked
                    DevControlsStore.writeSeason(context, picked)
                }
            }

            Text(
                text = "Переключатели действуют сразу на главном экране и карте. " +
                    "«Авто»/«Нет» возвращают обычное поведение.",
                style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
            )
        }
    }
}

private fun Season.title(): String = when (this) {
    Season.NONE -> "Нет"
    Season.NEW_YEAR -> "Новый год"
}

@Composable
private fun DevSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ColorTextBody),
            )
            Text(
                text = subtitle,
                style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
            )
            content()
        }
    }
}

@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    testTagPrefix: String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = if (isSelected) ColorBrandFill else ColorSurfaceVariantLight,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) ColorBrandFill else ColorBorderLight),
                modifier =
                Modifier
                    .weight(1f)
                    .testTag(testTagPrefix + label.lowercase().replace(' ', '_'))
                    .bouncyClickable { onSelect(value) },
            ) {
                Text(
                    text = label,
                    maxLines = 1,
                    style =
                    TextStyle(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (isSelected) ColorOnAccent else ColorTextBody,
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
