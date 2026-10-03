package com.politex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WidgetLayoutsTest {
    private val allowedTags =
        setOf(
            "FrameLayout",
            "LinearLayout",
            "RelativeLayout",
            "GridLayout",
            "AnalogClock",
            "Button",
            "Chronometer",
            "ImageButton",
            "ImageView",
            "ProgressBar",
            "TextView",
            "ViewFlipper",
            "ListView",
            "GridView",
            "StackView",
            "AdapterViewFlipper",
            "ViewStub",
        )

    private val layoutsDir = File("src/main/res/layout")

    @Test
    fun `разметки виджетов используют только поддерживаемые RemoteViews элементы`() {
        val widgetLayouts = layoutsDir.listFiles { f -> f.name.startsWith("widget_") && f.extension == "xml" }
        assertTrue("Не найдено ни одной разметки виджета в ${layoutsDir.absolutePath}", !widgetLayouts.isNullOrEmpty())

        val problems = mutableListOf<String>()
        widgetLayouts.forEach { file ->
            val text = file.readText()

            val tags =
                Regex("""<\s*/?\s*([A-Za-z][A-Za-z0-9_.]*)""")
                    .findAll(text)
                    .map { it.groupValues[1] }
                    .toSet()

            tags.filter { it !in allowedTags && !it.contains('.') }.forEach { tag ->
                problems += "${file.name}: <$tag> не поддерживается RemoteViews"
            }
        }

        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    // N7: чип кабинета в «Сейчас и дальше» — отдельная view, вынесенная в правый
    // конец строки с временем (не поверх текста «СЕЙЧАС/ДАЛЬШЕ», не на разделителе).
    // Проверяем структурно: чип — свой TextView, строка «время+чип» горизонтальна,
    // время тянется по весу (width=0dp) и выталкивает чип вправо, отступ чипа 8dp.
    @Test
    fun `N7 чип кабинета вынесен вправо и не перекрывает текст сейчас-дальше`() {
        val text = File(layoutsDir, "widget_now_next.xml").readText()

        // Чип — отдельная view в обоих блоках (не слит с текстом «сейчас/дальше»)
        assertTrue("нет чипа кабинета «сейчас»", text.contains("@+id/tv_now_room"))
        assertTrue("нет чипа кабинета «дальше»", text.contains("@+id/tv_next_room"))

        // Строка «время + чип» горизонтальна => чип справа от времени, не под/поверх текста
        assertTrue(
            "строка время+чип должна быть горизонтальной",
            text.contains("android:orientation=\"horizontal\""),
        )
        // Время занимает остаток ширины по весу (0dp) и выталкивает чип в правый конец
        assertTrue(
            "время должно тянуться по весу, выталкивая чип вправо",
            text.contains("android:layout_width=\"0dp\""),
        )
        // Аккуратный зазор 8dp — чип не липнет ни к времени, ни к разделителю
        assertTrue(
            "чип должен иметь отступ 8dp от времени",
            text.contains("android:layout_marginStart=\"8dp\""),
        )
    }

    // N10: контраст прошедших уроков на белом. 0xFF6B7280 ≈ 4.8:1 (≥4.5);
    // прежний 0xFF9AA3AF ≈ 2.5:1 — не читался. pastColor определён ОДИН раз в
    // WidgetStyle.LIGHT — все провайдеры (Today/Bells/NowNext) берут style.pastColor.
    @Test
    fun `N10 pastColor LIGHT соответствует контрасту на белом`() {
        assertEquals(0xFF6B7280.toInt(), com.politex.widget.WidgetStyle.LIGHT.pastColor)
    }

    // N10: иерархия «когда» первой — время (tv_*_details) тяжелее предмета по весу
    // (sans-serif-medium = medium), предмет при этом обычный (не bold).
    @Test
    fun `N10 время в now-next тяжелее предмета по весу`() {
        val text = File(layoutsDir, "widget_now_next.xml").readText()
        assertTrue(
            "время должно быть medium (sans-serif-medium)",
            text.contains("android:fontFamily=\"sans-serif-medium\""),
        )
    }
}
