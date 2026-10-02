package com.politex.util

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Абзац DOCX: текст + минимальное оформление (жирный / заголовок). */
data class DocxParagraph(
    val text: String,
    val bold: Boolean = false,
    val heading: Boolean = false
)

/**
 * M2: извлечение текста из .docx БЕЗ сторонних библиотек.
 *
 * DOCX — это обычный ZIP (OOXML): `word/document.xml` содержит абзацы `<w:p>`, внутри них
 * ранги `<w:r>` с текстом `<w:t>`, свойства `<w:b>` (жирный) и `<w:pStyle w:val="Heading1">`
 * (стиль абзаца). Читаем ровно это: ZipInputStream → XmlPullParser (stdlib) → список абзацев.
 *
 * Что НАМЕРЕННО не поддерживается: таблицы (`w:tbl` — текст ячеек всё равно попадёт в выборку,
 * так как `w:t` читается везде), сноски, нумерация/маркеры списков (автонумерация хранится не в
 * тексте), картинки, колонтитулы. Для поиска и чтения конспекта этого достаточно.
 *
 * .doc (бинарный OLE2, до 2007) не парсится: формат требует отдельной библиотеки (POI ≈ +10 МБ
 * к APK). Честная заглушка в просмотрщике предлагает конвертировать в .docx.
 *
 * Парсер создаётся с isNamespaceAware=false, поэтому имена тегов приходят квалифицированными
 * («w:t»); локальное имя берётся через [localName] — работает и с префиксом, и без него.
 */
object DocxTextExtractor {

    /** Путь внутри OOXML-пакета, где лежит тело документа. */
    private const val DOCUMENT_ENTRY = "word/document.xml"

    /**
     * Стили абзаца, которые считаем заголовками. Идентификаторы стилей в DOCX локализуются
     * («Heading1» в англ. шаблоне, «Заголовок1» в русскоязычном Word), плюс встроенные
     * числовые id — поэтому набор широкий.
     */
    private val HEADING_STYLE = Regex("^(heading|заголовок|title|название)\\s*\\d*$", RegexOption.IGNORE_CASE)

    /** Стили, которые точно не заголовок, но начинаются похоже (защита от ложных срабатываний). */
    private val NOT_HEADING_STYLE = Regex("^(heading\\s*[1-9]\\s*char|заголовок\\s*\\d+\\s*знак)", RegexOption.IGNORE_CASE)

    /**
     * Разобрать поток .docx (весь архив читается последовательно, в память документ не грузится).
     * Пустой список — если `word/document.xml` не найден (повреждённый/не DOCX файл).
     */
    fun extract(source: InputStream): List<DocxParagraph> {
        ZipInputStream(source.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return emptyList()
                if (entry.name == DOCUMENT_ENTRY) return parseDocumentXml(zip)
                zip.closeEntry()
            }
        }
    }

    /** Абзацы одной строкой через перевод — то, что кладётся в `textContent` для поиска. */
    fun toPlainText(paragraphs: List<DocxParagraph>): String =
        paragraphs.joinToString("\n") { it.text }

    private fun parseDocumentXml(stream: InputStream): List<DocxParagraph> {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
        parser.setInput(stream, "UTF-8")

        val out = ArrayList<DocxParagraph>()
        val buffer = StringBuilder()
        var inParagraph = false
        var bold = false
        var heading = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (localName(parser)) {
                        "p" -> {
                            inParagraph = true
                            buffer.setLength(0)
                            bold = false
                            heading = false
                        }
                        "t" -> if (inParagraph) buffer.append(parser.nextText())
                        "tab" -> if (inParagraph) buffer.append('\t')
                        "br" -> if (inParagraph) buffer.append('\n')
                        "b" -> if (inParagraph && !isSwitchedOff(parser.attr("val"))) bold = true
                        "pStyle" -> {
                            val style = parser.attr("val") ?: ""
                            if (HEADING_STYLE.matches(style.trim()) && !NOT_HEADING_STYLE.matches(style.trim())) {
                                heading = true
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (inParagraph && localName(parser) == "p") {
                        inParagraph = false
                        val text = buffer.toString().trim()
                        if (text.isNotEmpty()) out.add(DocxParagraph(text, bold, heading))
                    }
                }
            }
            event = parser.next()
        }
        return out
    }

    /** `<w:b w:val="false"/>` / `w:val="0"` / `w:val="none"` — свойство ВЫКЛЮЧЕНО, а не включено. */
    private fun isSwitchedOff(value: String?): Boolean =
        value.equals("false", ignoreCase = true) || value == "0" || value.equals("none", ignoreCase = true)

    private fun localName(parser: XmlPullParser): String = parser.name.substringAfterLast(':')

    /** Атрибут по локальному имени: с isNamespaceAware=false он приходит как «w:val». */
    private fun XmlPullParser.attr(local: String): String? {
        for (i in 0 until attributeCount) {
            if (getAttributeName(i).substringAfterLast(':') == local) return getAttributeValue(i)
        }
        return null
    }
}
