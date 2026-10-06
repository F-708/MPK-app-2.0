package com.politex.ui.screens.materials

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipInputStream

// HH4: структурированная модель документа DOCX (параграфы с run'ами, таблицы, разрывы страниц).
data class DocxRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val sizeHalfPt: Int? = null,
    val colorArgb: Long? = null,
    val linkUrl: String? = null,
    val imagePlaceholder: Boolean = false,
)

enum class DocxAlign { LEFT, CENTER, RIGHT }

sealed interface DocxBlock {
    data class Para(
        val runs: List<DocxRun>,
        val headingLevel: Int = 0,
        val align: DocxAlign = DocxAlign.LEFT,
        val bullet: String? = null,
        val indentLevel: Int = 0,
        val indentTwips: Int = 0,
    ) : DocxBlock

    // rows -> cells -> блоки ячейки (вложенные таблицы допустимы)
    data class Table(val rows: List<List<List<DocxBlock>>>) : DocxBlock

    object PageBreak : DocxBlock
}

data class DocxDocument(val blocks: List<DocxBlock>) {

    fun plainText(): String {
        val sb = StringBuilder()
        fun walk(list: List<DocxBlock>) {
            for (b in list) when (b) {
                is DocxBlock.Para -> sb.append(b.runs.joinToString("") { it.text }).append('\n')
                is DocxBlock.Table -> b.rows.forEach { row -> row.forEach { walk(it) } }
                DocxBlock.PageBreak -> {}
            }
        }
        walk(blocks)
        return sb.toString().trimEnd('\n')
    }
}

/**
 * HH4: lite-парсер OOXML — word/document.xml + styles.xml + numbering.xml + rels.
 * Потолок: без docDefaults, без rPr секций, без точной ширины колонок таблиц,
 * нумерация roman/letter отображается арабскими числами.
 */
object DocxDocumentParser {

    private val HEADING_ID = Regex("^(heading|заголовок)\\s*([1-9])$", RegexOption.IGNORE_CASE)
    private val TITLE_ID = Regex("^(title|название)$", RegexOption.IGNORE_CASE)

    fun parse(source: InputStream): DocxDocument? {
        var documentXml: String? = null
        var stylesXml: String? = null
        var numberingXml: String? = null
        var relsXml: String? = null
        ZipInputStream(source.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val text = when (entry.name) {
                    "word/document.xml", "word/styles.xml", "word/numbering.xml", "word/_rels/document.xml.rels" ->
                        zip.readBytes().decodeToString()
                    else -> null
                }
                when (entry.name) {
                    "word/document.xml" -> documentXml = text
                    "word/styles.xml" -> stylesXml = text
                    "word/numbering.xml" -> numberingXml = text
                    "word/_rels/document.xml.rels" -> relsXml = text
                }
                zip.closeEntry()
            }
        }
        val xml = documentXml ?: return null
        return parseDocument(xml, parseStyles(stylesXml), parseRels(relsXml), parseNumbering(numberingXml))
    }

    // ---------------------------------------------------------------- styles

    private class StyleInfo(
        val basedOn: String? = null,
        val headingLevel: Int = 0,
        val bold: Boolean? = null,
        val italic: Boolean? = null,
        val underline: Boolean? = null,
        val sizeHalfPt: Int? = null,
        val colorArgb: Long? = null,
    )

    private fun parseStyles(xml: String?): Map<String, StyleInfo> {
        if (xml == null) return emptyMap()
        val raw = HashMap<String, StyleInfo>()
        val parser = newParser(xml)
        var id: String? = null
        var name = ""
        var basedOn: String? = null
        var b: Boolean? = null
        var i: Boolean? = null
        var u: Boolean? = null
        var sz: Int? = null
        var color: Long? = null
        var inRPr = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (localName(parser)) {
                    "style" -> {
                        id = parser.attr("styleId"); name = ""; basedOn = null
                        b = null; i = null; u = null; sz = null; color = null
                    }
                    "name" -> name = parser.attr("val") ?: name
                    "basedOn" -> basedOn = parser.attr("val")
                    "rPr" -> inRPr = true
                    "b" -> if (inRPr) b = !off(parser.attr("val"))
                    "i" -> if (inRPr) i = !off(parser.attr("val"))
                    "u" -> if (inRPr) u = !off(parser.attr("val"))
                    "sz" -> if (inRPr) sz = parser.attr("val")?.toIntOrNull()
                    "color" -> if (inRPr) color = parseColor(parser.attr("val"))
                }
                XmlPullParser.END_TAG -> when (localName(parser)) {
                    "rPr" -> inRPr = false
                    "style" -> {
                        val sid = id
                        if (sid != null) raw[sid] = StyleInfo(basedOn, headingLevelOf(sid, name), b, i, u, sz, color)
                        id = null; inRPr = false
                    }
                }
            }
            event = parser.next()
        }
        return resolveStyles(raw)
    }

    private fun resolveStyles(raw: Map<String, StyleInfo>): Map<String, StyleInfo> {
        val out = HashMap<String, StyleInfo>()
        fun rec(id: String, depth: Int): StyleInfo? {
            out[id]?.let { return it }
            val s = raw[id] ?: return null
            if (depth <= 0) return s
            val base = s.basedOn?.let { rec(it, depth - 1) }
            val merged = if (base == null) s else StyleInfo(
                s.basedOn,
                maxOf(s.headingLevel, base.headingLevel),
                s.bold ?: base.bold,
                s.italic ?: base.italic,
                s.underline ?: base.underline,
                s.sizeHalfPt ?: base.sizeHalfPt,
                s.colorArgb ?: base.colorArgb,
            )
            out[id] = merged
            return merged
        }
        raw.keys.toList().forEach { rec(it, 8) }
        return out
    }

    private fun headingLevelOf(id: String, name: String): Int {
        HEADING_ID.matchEntire(id)?.let { return it.groupValues[2].toInt() }
        HEADING_ID.matchEntire(name.trim())?.let { return it.groupValues[2].toInt() }
        if (TITLE_ID.matches(id) || TITLE_ID.matches(name.trim())) return 1
        return 0
    }

    // ---------------------------------------------------------------- rels / numbering

    private fun parseRels(xml: String?): Map<String, String> {
        if (xml == null) return emptyMap()
        val map = HashMap<String, String>()
        val parser = newParser(xml)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && localName(parser) == "Relationship") {
                val id = parser.attr("Id")
                val target = parser.attr("Target")
                val type = parser.attr("Type")
                if (id != null && target != null && (type == null || type.endsWith("/hyperlink"))) map[id] = target
            }
            event = parser.next()
        }
        return map
    }

    private class Numbering(
        val numToAbs: Map<String, String>,
        val levels: Map<String, Map<Int, Pair<String, String>>>,
    ) {
        private val counters = HashMap<Pair<String, Int>, Int>()

        fun label(numId: String, ilvl: Int): String? {
            val abs = numToAbs[numId] ?: return "•"
            val (fmt, text) = levels[abs]?.get(ilvl) ?: return "•"
            if (fmt == "none") return null
            if (fmt == "bullet") {
                val ch = text.firstOrNull()
                return if (ch == null || ch in ''..'' || ch < ' ') "•" else ch.toString()
            }
            // ponytail: decimal → «N.»; roman/letter форматы тоже арабскими — достаточно для конспектов
            val key = numId to ilvl
            val n = (counters[key] ?: 0) + 1
            counters[key] = n
            counters.keys.removeAll { it.first == numId && it.second > ilvl }
            return "$n."
        }
    }

    private fun parseNumbering(xml: String?): Numbering? {
        if (xml == null) return null
        val numToAbs = HashMap<String, String>()
        val levels = HashMap<String, MutableMap<Int, Pair<String, String>>>()
        val parser = newParser(xml)
        var absId: String? = null
        var numId: String? = null
        var ilvl = -1
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (localName(parser)) {
                    "abstractNum" -> { absId = parser.attr("abstractNumId"); numId = null }
                    "num" -> { numId = parser.attr("numId"); absId = null }
                    "lvl" -> ilvl = parser.attr("ilvl")?.toIntOrNull() ?: -1
                    "abstractNumId" -> {
                        val n = numId; val v = parser.attr("val")
                        if (n != null && v != null) numToAbs[n] = v
                    }
                    "numFmt", "lvlText" -> {
                        val a = absId
                        if (a != null && ilvl >= 0) {
                            val map = levels.getOrPut(a) { HashMap() }
                            val prev = map[ilvl] ?: ("" to "")
                            val v = parser.attr("val") ?: ""
                            map[ilvl] = if (localName(parser) == "numFmt") v to prev.second else prev.first to v
                        }
                    }
                }
            }
            event = parser.next()
        }
        return Numbering(numToAbs, levels)
    }

    // ---------------------------------------------------------------- document

    private fun parseDocument(
        xml: String,
        styles: Map<String, StyleInfo>,
        rels: Map<String, String>,
        numbering: Numbering?,
    ): DocxDocument {
        val parser = newParser(xml)
        val body = ArrayList<DocxBlock>()
        val tableStack = ArrayDeque<MutableList<List<List<DocxBlock>>>>()
        val rowStack = ArrayDeque<MutableList<List<DocxBlock>>>()
        val cellStack = ArrayDeque<MutableList<DocxBlock>>()
        fun container(): MutableList<DocxBlock> = cellStack.lastOrNull() ?: body

        val runs = ArrayList<DocxRun>()
        var inP = false
        var inPPr = false
        var inRPr = false
        var inR = false
        var drawingDepth = 0
        var pStyleId: String? = null
        var pAlign = DocxAlign.LEFT
        var pNumId: String? = null
        var pIlvl = 0
        var pIndTwips = 0
        var pSectionBreak = false
        val runText = StringBuilder()
        var rBold: Boolean? = null
        var rItalic: Boolean? = null
        var rUnder: Boolean? = null
        var rSz: Int? = null
        var rColor: Long? = null
        var rStyleId: String? = null
        var rHidden = false
        var linkUrl: String? = null

        fun resetPara() {
            runs.clear(); pStyleId = null; pAlign = DocxAlign.LEFT
            pNumId = null; pIlvl = 0; pIndTwips = 0; pSectionBreak = false
        }

        fun resetRun() {
            runText.setLength(0); rBold = null; rItalic = null; rUnder = null
            rSz = null; rColor = null; rStyleId = null; rHidden = false
        }

        fun flushRun() {
            val text = runText.toString()
            runText.setLength(0)
            if (!inR || text.isEmpty() || rHidden) return
            val cs = rStyleId?.let { styles[it] }
            val ps = pStyleId?.let { styles[it] }
            runs.add(
                DocxRun(
                    text,
                    bold = rBold ?: cs?.bold ?: ps?.bold ?: false,
                    italic = rItalic ?: cs?.italic ?: ps?.italic ?: false,
                    underline = rUnder ?: cs?.underline ?: ps?.underline ?: false,
                    sizeHalfPt = rSz ?: cs?.sizeHalfPt ?: ps?.sizeHalfPt,
                    colorArgb = rColor ?: cs?.colorArgb ?: ps?.colorArgb,
                    linkUrl = linkUrl,
                ),
            )
        }

        fun headingLevelOf(id: String?, style: StyleInfo?): Int {
            if (id != null) {
                HEADING_ID.matchEntire(id)?.let { return it.groupValues[2].toInt() }
                if (TITLE_ID.matches(id)) return 1
            }
            return style?.headingLevel ?: 0
        }

        fun flushPara() {
            if (runs.any { it.text.isNotBlank() || it.imagePlaceholder }) {
                val style = pStyleId?.let { styles[it] }
                val bullet = pNumId?.let { numbering?.label(it, pIlvl) ?: "•" }
                container().add(
                    DocxBlock.Para(
                        runs = runs.toList(),
                        headingLevel = headingLevelOf(pStyleId, style),
                        align = pAlign,
                        bullet = bullet,
                        indentLevel = if (bullet != null) pIlvl else 0,
                        indentTwips = if (bullet != null) 0 else pIndTwips,
                    ),
                )
            }
            runs.clear()
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = if (event == XmlPullParser.START_TAG || event == XmlPullParser.END_TAG) localName(parser) else ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (drawingDepth > 0) {
                        // содержимое drawing/pict (включая текстовые рамки) не парсим — только заглушка
                        if (tag == "drawing" || tag == "pict" || tag == "object") drawingDepth++
                    } else when (tag) {
                        "drawing", "pict", "object" -> {
                            drawingDepth = 1
                            if (inP && !inPPr) {
                                flushRun()
                                runs.add(DocxRun("[изображение]", imagePlaceholder = true))
                            }
                        }
                        "tbl" -> tableStack.addLast(ArrayList())
                        "tr" -> rowStack.addLast(ArrayList())
                        "tc" -> cellStack.addLast(ArrayList())
                        "p" -> if (!inP) { inP = true; resetPara() }
                        "pPr" -> inPPr = true
                        "rPr" -> inRPr = true
                        "r" -> { inR = true; resetRun() }
                        "hyperlink" -> linkUrl = parser.attr("id")?.let { rels[it] }
                        "t" -> if (inP && inR && !inPPr) runText.append(parser.nextText())
                        "tab" -> if (inP && inR) runText.append('\t')
                        "br" -> {
                            if (parser.attr("type") == "page") {
                                flushRun(); flushPara(); container().add(DocxBlock.PageBreak)
                            } else if (inR) {
                                runText.append('\n')
                            }
                        }
                        "pStyle" -> if (inPPr) pStyleId = parser.attr("val")
                        "jc" -> if (inPPr) pAlign = when (parser.attr("val")) {
                            "center" -> DocxAlign.CENTER
                            "right", "end" -> DocxAlign.RIGHT
                            else -> DocxAlign.LEFT
                        }
                        "numId" -> if (inPPr) pNumId = parser.attr("val")?.takeIf { it != "0" }
                        "ilvl" -> if (inPPr) pIlvl = parser.attr("val")?.toIntOrNull() ?: 0
                        "ind" -> if (inPPr) pIndTwips = (parser.attr("left") ?: parser.attr("start"))?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                        "sectPr" -> if (inPPr) pSectionBreak = true
                        "b" -> if (inRPr && !inPPr) rBold = !off(parser.attr("val"))
                        "i" -> if (inRPr && !inPPr) rItalic = !off(parser.attr("val"))
                        "u" -> if (inRPr && !inPPr) rUnder = !off(parser.attr("val"))
                        "sz" -> if (inRPr && !inPPr) rSz = parser.attr("val")?.toIntOrNull()
                        "color" -> if (inRPr && !inPPr) rColor = parseColor(parser.attr("val"))
                        "rStyle" -> if (inRPr && !inPPr) rStyleId = parser.attr("val")
                        "vanish" -> if (inRPr && !inPPr) rHidden = !off(parser.attr("val"))
                    }
                }

                XmlPullParser.END_TAG -> when {
                    drawingDepth > 0 -> if (tag == "drawing" || tag == "pict" || tag == "object") drawingDepth--
                    tag == "r" -> { flushRun(); inR = false }
                    tag == "p" -> if (inP) {
                        flushRun(); flushPara()
                        if (pSectionBreak) container().add(DocxBlock.PageBreak)
                        inP = false
                    }
                    tag == "pPr" -> inPPr = false
                    tag == "rPr" -> inRPr = false
                    tag == "hyperlink" -> linkUrl = null
                    tag == "tc" -> cellStack.removeLastOrNull()?.let { rowStack.lastOrNull()?.add(it) }
                    tag == "tr" -> rowStack.removeLastOrNull()?.let { tableStack.lastOrNull()?.add(it) }
                    tag == "tbl" -> {
                        val rows = tableStack.removeLastOrNull()
                        if (!rows.isNullOrEmpty()) container().add(DocxBlock.Table(rows.toList()))
                    }
                }
            }
            event = parser.next()
        }
        return DocxDocument(body)
    }

    // ---------------------------------------------------------------- utils

    private fun off(v: String?): Boolean = v == "0" || v.equals("false", ignoreCase = true) || v.equals("none", ignoreCase = true)

    private fun parseColor(v: String?): Long? {
        if (v == null || v.equals("auto", ignoreCase = true)) return null
        val rgb = v.removePrefix("#").takeLast(6).toLongOrNull(16) ?: return null
        return 0xFF000000L or rgb
    }

    private fun newParser(xml: String): XmlPullParser =
        XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
            .also { it.setInput(StringReader(xml)) }

    private fun localName(p: XmlPullParser): String = p.name.substringAfterLast(':')

    private fun XmlPullParser.attr(local: String): String? {
        for (i in 0 until attributeCount) {
            if (getAttributeName(i).substringAfterLast(':') == local) return getAttributeValue(i)
        }
        return null
    }
}
