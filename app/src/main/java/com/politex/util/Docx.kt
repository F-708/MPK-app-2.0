package com.politex.util

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** R1-СЖАТИЕ: единственная копия чтения word/document.xml из .docx (была в 3 местах). */
object Docx {
    private const val DOCUMENT_ENTRY = "word/document.xml"

    fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

    /** R1-МЕЛОЧ18: поток закрывается через use (раньше ZipInputStream утекал). */
    fun readDocumentXml(source: InputStream): String? =
        ZipInputStream(source.buffered()).use { zip ->
            var result: String? = null
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == DOCUMENT_ENTRY) {
                    result = zip.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    break
                }
                zip.closeEntry()
            }
            result
        }

    fun readDocumentXml(bytes: ByteArray): String? = readDocumentXml(ByteArrayInputStream(bytes))
}
