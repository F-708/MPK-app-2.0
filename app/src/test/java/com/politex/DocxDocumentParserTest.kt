package com.politex

import com.politex.ui.screens.materials.DocxAlign
import com.politex.ui.screens.materials.DocxBlock
import com.politex.ui.screens.materials.DocxDocument
import com.politex.ui.screens.materials.DocxDocumentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class DocxDocumentParserTest {

    private fun docxBytes(
        document: String,
        styles: String? = null,
        numbering: String? = null,
        rels: String? = null,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", """<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>""")
            put("word/document.xml", wrap(document))
            styles?.let { put("word/styles.xml", """<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">$it</w:styles>""") }
            numbering?.let { put("word/numbering.xml", """<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">$it</w:numbering>""") }
            rels?.let { put("word/_rels/document.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$it</Relationships>""") }
        }
        return out.toByteArray()
    }

    private fun wrap(body: String) = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
        "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
        "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
        "<w:body>$body</w:body></w:document>"

    private fun parse(
        body: String,
        styles: String? = null,
        numbering: String? = null,
        rels: String? = null,
    ): DocxDocument = DocxDocumentParser.parse(ByteArrayInputStream(docxBytes(body, styles, numbering, rels)))!!

    private fun paras(doc: DocxDocument): List<DocxBlock.Para> = doc.blocks.filterIsInstance<DocxBlock.Para>()

    private fun paraText(p: DocxBlock.Para): String = p.runs.joinToString("") { it.text }

    @Test
    fun `HH4 уровни заголовков из pStyle id и из name в styles xml`() {
        val doc = parse(
            """
            <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Глава</w:t></w:r></w:p>
            <w:p><w:pPr><w:pStyle w:val="Заголовок2"/></w:pPr><w:r><w:t>Раздел</w:t></w:r></w:p>
            <w:p><w:pPr><w:pStyle w:val="MyHead"/></w:pPr><w:r><w:t>Свой</w:t></w:r></w:p>
            <w:p><w:pPr><w:pStyle w:val="Heading1Char"/></w:pPr><w:r><w:t>Не заголовок</w:t></w:r></w:p>
            <w:p><w:r><w:t>Обычный</w:t></w:r></w:p>
            """.trimIndent(),
            styles = """<w:style w:styleId="MyHead"><w:name w:val="heading 3"/></w:style>""",
        )
        assertEquals(listOf(1, 2, 3, 0, 0), paras(doc).map { it.headingLevel })
    }

    @Test
    fun `HH4 свойства run - b i u sz color и val=0 выключает`() {
        val doc = parse(
            """
            <w:p>
              <w:r><w:rPr><w:b/><w:i/><w:u w:val="single"/><w:sz w:val="28"/><w:color w:val="FF0000"/></w:rPr><w:t>акцент</w:t></w:r>
              <w:r><w:rPr><w:b w:val="0"/></w:rPr><w:t>простой</w:t></w:r>
            </w:p>
            """.trimIndent(),
        )
        val runs = paras(doc).single().runs
        assertEquals(2, runs.size)
        assertTrue(runs[0].bold && runs[0].italic && runs[0].underline)
        assertEquals(28, runs[0].sizeHalfPt)
        assertTrue(runs[0].colorArgb == 0xFFFF0000L)
        assertFalse(runs[1].bold)
    }

    @Test
    fun `HH4 стиль абзаца наследуется run'ами`() {
        val doc = parse(
            """<w:p><w:pPr><w:pStyle w:val="StrongPara"/></w:pPr><w:r><w:t>текст</w:t></w:r></w:p>""",
            styles = """<w:style w:styleId="StrongPara"><w:name w:val="Strong Para"/><w:rPr><w:b/></w:rPr></w:style>""",
        )
        assertTrue(paras(doc).single().runs.single().bold)
    }

    @Test
    fun `HH4 списки - маркер и нумерация из numbering xml`() {
        val numbering = """
            <w:abstractNum w:abstractNumId="0">
              <w:lvl w:ilvl="0"><w:numFmt w:val="bullet"/><w:lvlText w:val="&#xF0B7;"/></w:lvl>
            </w:abstractNum>
            <w:abstractNum w:abstractNumId="1">
              <w:lvl w:ilvl="0"><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/></w:lvl>
              <w:lvl w:ilvl="1"><w:numFmt w:val="decimal"/><w:lvlText w:val="%1.%2."/></w:lvl>
            </w:abstractNum>
            <w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
            <w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num>
        """.trimIndent()
        val doc = parse(
            """
            <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>маркер</w:t></w:r></w:p>
            <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="2"/></w:numPr></w:pPr><w:r><w:t>первый</w:t></w:r></w:p>
            <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="2"/></w:numPr></w:pPr><w:r><w:t>второй</w:t></w:r></w:p>
            <w:p><w:pPr><w:numPr><w:ilvl w:val="1"/><w:numId w:val="2"/></w:numPr></w:pPr><w:r><w:t>подпункт</w:t></w:r></w:p>
            """.trimIndent(),
            numbering = numbering,
        )
        val ps = paras(doc)
        assertEquals("•", ps[0].bullet)
        assertEquals("1.", ps[1].bullet)
        assertEquals("2.", ps[2].bullet)
        assertEquals("1.", ps[3].bullet)
        assertEquals(1, ps[3].indentLevel)
    }

    @Test
    fun `HH4 jc - center right left`() {
        val doc = parse(
            """
            <w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:t>Ц</w:t></w:r></w:p>
            <w:p><w:pPr><w:jc w:val="right"/></w:pPr><w:r><w:t>П</w:t></w:r></w:p>
            <w:p><w:r><w:t>Л</w:t></w:r></w:p>
            """.trimIndent(),
        )
        assertEquals(listOf(DocxAlign.CENTER, DocxAlign.RIGHT, DocxAlign.LEFT), paras(doc).map { it.align })
    }

    @Test
    fun `HH4 ind left сохраняется в twips`() {
        val doc = parse("""<w:p><w:pPr><w:ind w:left="720"/></w:pPr><w:r><w:t>отступ</w:t></w:r></w:p>""")
        assertEquals(720, paras(doc).single().indentTwips)
    }

    @Test
    fun `HH4 таблица - строки и ячейки`() {
        val doc = parse(
            """
            <w:tbl>
              <w:tr><w:tc><w:p><w:r><w:t>Напряжение</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>220 В</w:t></w:r></w:p></w:tc></w:tr>
              <w:tr><w:tc><w:p><w:r><w:t>Ток</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>10 А</w:t></w:r></w:p></w:tc></w:tr>
            </w:tbl>
            """.trimIndent(),
        )
        val t = doc.blocks.filterIsInstance<DocxBlock.Table>().single()
        assertEquals(2, t.rows.size)
        assertEquals(2, t.rows[0].size)
        assertEquals("Напряжение", paraText(t.rows[0][0].single() as DocxBlock.Para))
        assertEquals("10 А", paraText(t.rows[1][1].single() as DocxBlock.Para))
    }

    @Test
    fun `HH4 гиперссылка резолвится через rels`() {
        val doc = parse(
            """<w:p><w:r><w:t>см. </w:t></w:r><w:hyperlink r:id="rId5"><w:r><w:t>сайт</w:t></w:r></w:hyperlink></w:p>""",
            rels = """<Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="https://example.com" TargetMode="External"/>""",
        )
        val runs = paras(doc).single().runs
        assertEquals("сайт", runs[1].text)
        assertEquals("https://example.com", runs[1].linkUrl)
        assertEquals(null, runs[0].linkUrl)
    }

    @Test
    fun `HH4 разрыв страницы - br type=page и sectPr`() {
        val doc = parse(
            """
            <w:p><w:r><w:t>До</w:t></w:r><w:r><w:br w:type="page"/></w:r><w:r><w:t>После</w:t></w:r></w:p>
            <w:p><w:pPr><w:sectPr><w:pgSz w:w="11906" w:h="16838"/></w:sectPr></w:pPr><w:r><w:t>Секция</w:t></w:r></w:p>
            """.trimIndent(),
        )
        assertEquals(
            listOf("До", "<break>", "После", "Секция", "<break>"),
            doc.blocks.map { if (it is DocxBlock.Para) paraText(it) else "<break>" },
        )
    }

    @Test
    fun `HH4 картинка - заглушка и изоляция текстовой рамки`() {
        val doc = parse(
            """<w:p><w:r><w:t>до</w:t></w:r><w:r><w:drawing><wp:inline xmlns:wp="urn:x"><w:p><w:r><w:t>внутри рамки</w:t></w:r></w:p></wp:inline></w:drawing></w:r><w:r><w:t>после</w:t></w:r></w:p>""",
        )
        val p = paras(doc).single()
        assertEquals("до[изображение]после", paraText(p))
        assertTrue(p.runs.any { it.imagePlaceholder })
        assertFalse(p.runs.any { it.text.contains("внутри рамки") })
    }

    @Test
    fun `HH4 скрытый vanish и удалённый delText не попадают в вывод`() {
        val doc = parse(
            """
            <w:p><w:r><w:rPr><w:vanish/></w:rPr><w:t>секрет</w:t></w:r><w:r><w:t>видимый</w:t></w:r></w:p>
            <w:p><w:r><w:delText>удалено</w:delText></w:r><w:r><w:t>живой</w:t></w:r></w:p>
            """.trimIndent(),
        )
        val ps = paras(doc)
        assertEquals("видимый", paraText(ps[0]))
        assertEquals("живой", paraText(ps[1]))
    }

    @Test
    fun `HH4 табы и мягкие переносы остаются в run`() {
        val doc = parse("""<w:p><w:r><w:t>Пункт</w:t><w:tab/><w:t>1</w:t><w:br/><w:t>ещё</w:t></w:r></w:p>""")
        assertEquals("Пункт\t1\nещё", paras(doc).single().runs.single().text)
    }

    @Test
    fun `HH4 plainText уплощает документ для редактора`() {
        val doc = parse(
            """
            <w:p><w:r><w:t>строка1</w:t></w:r></w:p>
            <w:tbl><w:tr><w:tc><w:p><w:r><w:t>ячейка</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
            <w:p><w:r><w:t>строка2</w:t></w:r></w:p>
            """.trimIndent(),
        )
        assertEquals("строка1\nячейка\nстрока2", doc.plainText())
    }

    @Test
    fun `HH4 битый пакет не роняет парсер`() {
        val outcome = runCatching { DocxDocumentParser.parse(ByteArrayInputStream(byteArrayOf(1, 2, 3))) }
        assertTrue(outcome.getOrNull() == null)
    }
}
