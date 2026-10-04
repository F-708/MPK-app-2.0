package com.politex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

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

    @Test
    fun `N7 чип кабинета вынесен вправо и не перекрывает текст сейчас-дальше`() {
        val text = File(layoutsDir, "widget_now_next.xml").readText()

        assertTrue("нет чипа кабинета «сейчас»", text.contains("@+id/tv_now_room"))
        assertTrue("нет чипа кабинета «дальше»", text.contains("@+id/tv_next_room"))

        assertTrue(
            "строка время+чип должна быть горизонтальной",
            text.contains("android:orientation=\"horizontal\""),
        )

        assertTrue(
            "время должно тянуться по весу, выталкивая чип вправо",
            text.contains("android:layout_width=\"0dp\""),
        )

        assertTrue(
            "чип должен иметь отступ 8dp от времени",
            text.contains("android:layout_marginStart=\"8dp\""),
        )
    }

    @Test
    fun `N10 pastColor LIGHT соответствует контрасту на белом`() {
        assertEquals(0xFF6B7280.toInt(), com.politex.widget.WidgetStyle.LIGHT.pastColor)
    }

    @Test
    fun `N10 время в now-next тяжелее предмета по весу`() {
        val text = File(layoutsDir, "widget_now_next.xml").readText()
        assertTrue(
            "время должно быть medium (sans-serif-medium)",
            text.contains("android:fontFamily=\"sans-serif-medium\""),
        )
    }

    @Test
    fun `CC9 iv_idle есть в layout виджетов и по умолчанию скрыт`() {
        listOf("widget_now_next.xml", "widget_today_schedule.xml", "widget_bell_countdown.xml").forEach { name ->
            val text = File(layoutsDir, name).readText()
            assertTrue("$name: нет @+id/iv_idle", text.contains("@+id/iv_idle"))
            val attrs = text.substringAfter("android:id=\"@+id/iv_idle\"").substringBefore("/>")
            assertTrue("$name: iv_idle не скрыт по умолчанию", attrs.contains("android:visibility=\"gone\""))
        }
    }

    @Test
    fun `CC9 anim_idle — бесконечный animated-vector`() {
        val text = File("src/main/res/drawable/anim_idle.xml").readText()
        assertTrue("anim_idle не animated-vector", text.contains("<animated-vector"))
        assertTrue("anim_idle не зациклен", text.contains("android:repeatCount=\"infinite\""))
    }

    @Test
    fun `DD idle-блок — колокольчик и надпись в одном вертикальном LinearLayout, колокольчик первый`() {
        val layouts =
            listOf(
                "widget_now_next.xml" to "tv_empty",
                "widget_today_schedule.xml" to "tv_today_empty",
                "widget_bell_countdown.xml" to "tv_countdown_label",
            )

        layouts.forEach { (name, textId) ->
            val doc =
                DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(File(layoutsDir, name))

            fun findById(id: String): Element =
                doc.getElementsByTagName("*")
                    .let { nodes ->
                        (0 until nodes.length)
                            .map { nodes.item(it) as Element }
                            .first { it.getAttribute("android:id") == "@+id/$id" }
                    }

            val bell = findById("iv_idle")
            val text = findById(textId)
            val parent = bell.parentNode as Element

            assertEquals("$name: родитель iv_idle не LinearLayout", "LinearLayout", parent.tagName)
            assertEquals(
                "$name: idle-блок не вертикальный",
                "vertical",
                parent.getAttribute("android:orientation"),
            )
            assertSame("$name: надпись не в idle-блоке с колокольчиком", parent, text.parentNode)

            val children =
                (0 until parent.childNodes.length)
                    .map { parent.childNodes.item(it) }
                    .filterIsInstance<Element>()
            assertEquals("$name: колокольчик не первый в idle-блоке", bell, children.first())
            assertTrue(
                "$name: надпись должна идти ниже колокольчика",
                children.indexOf(bell) < children.indexOf(text),
            )
        }
    }

    @Test
    fun `DD у колокольчика нет marginBottom-оверлея`() {
        listOf("widget_now_next.xml", "widget_today_schedule.xml").forEach { name ->
            val text = File(layoutsDir, name).readText()
            val attrs = text.substringAfter("android:id=\"@+id/iv_idle\"").substringBefore("/>")
            assertTrue("$name: у iv_idle остался позиционирующий marginBottom", !attrs.contains("layout_marginBottom"))
        }
    }

    @Test
    fun `EE3 row_room wrap_content и вертикальный паддинг чипа в провайдере не больше 4dp`() {
        val layout = File(layoutsDir, "widget_row_list.xml").readText()
        val roomAttrs = layout.substringAfter("android:id=\"@+id/row_room\"").substringBefore("/>")
        assertTrue(
            "row_room должен быть wrap_content по высоте, иначе клиппится в строке с weight",
            roomAttrs.contains("android:layout_height=\"wrap_content\""),
        )

        val provider = File("src/main/java/com/politex/widget/TodayScheduleWidgetProvider.kt").readText()
        val pv = Regex("""val pv = \((\d+)""").find(provider)?.groupValues?.get(1)?.toInt()
        assertTrue("в TodayScheduleWidgetProvider не найден val pv = (N * d", pv != null)
        assertTrue("pv=$pv dp раздувает чип выше высоты строки (row_root 0dp+weight) — цифры клиппятся", pv!! <= 4)
    }

    @Test
    fun `EE2 чип кабинета в now-next отодвинут от предмета marginTop 4-6dp`() {
        val text = File(layoutsDir, "widget_now_next.xml").readText()
        listOf("tv_now_room", "tv_next_room").forEach { id ->
            val attrs = text.substringAfter("android:id=\"@+id/$id\"").substringBefore("/>")
            val mt = Regex("""android:layout_marginTop="(\d+)dp"""").find(attrs)?.groupValues?.get(1)?.toInt()
            assertTrue("$id: нет layout_marginTop — чип вплотную к названию предмета", mt != null)
            assertTrue("$id: marginTop=$mt dp вне диапазона 4-6dp", mt!! in 4..6)
        }
    }
}
