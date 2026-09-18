package com.example

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutsTest {

    private val allowedTags = setOf(
        "FrameLayout", "LinearLayout", "RelativeLayout", "GridLayout",
        "AnalogClock", "Button", "Chronometer", "ImageButton", "ImageView",
        "ProgressBar", "TextView", "ViewFlipper", "ListView", "GridView",
        "StackView", "AdapterViewFlipper", "ViewStub"
    )

    private val layoutsDir = File("src/main/res/layout")

    @Test
    fun `разметки виджетов используют только поддерживаемые RemoteViews элементы`() {
        val widgetLayouts = layoutsDir.listFiles { f -> f.name.startsWith("widget_") && f.extension == "xml" }
        assertTrue("Не найдено ни одной разметки виджета в ${layoutsDir.absolutePath}", !widgetLayouts.isNullOrEmpty())

        val problems = mutableListOf<String>()
        widgetLayouts.forEach { file ->
            val text = file.readText()

            val tags = Regex("""<\s*/?\s*([A-Za-z][A-Za-z0-9_.]*)""")
                .findAll(text)
                .map { it.groupValues[1] }
                .toSet()

            tags.filter { it !in allowedTags && !it.contains('.') }.forEach { tag ->
                problems += "${file.name}: <$tag> не поддерживается RemoteViews"
            }
        }

        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
