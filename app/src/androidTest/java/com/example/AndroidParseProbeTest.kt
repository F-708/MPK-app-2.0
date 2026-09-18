package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.network.MpkScheduleParser
import com.example.util.GroupParser
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidParseProbeTest {

    private fun log(msg: String) {
        println("MPK_PROBE | $msg")
    }

    private fun realDocBytes(): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets.open("raspisanie-15.09.2026.doc").use { it.readBytes() }

    @Test
    fun probeEverything() {
        val bytes = realDocBytes()
        log("docBytes = ${bytes.size}, head = ${bytes.take(8).joinToString(",") { (it.toInt() and 0xFF).toString(16) }}")

        log("groupChars(41О) = " + "41О".map { it.code.toString(16) }.joinToString(","))
        log("groupChars(41Г) = " + "41Г".map { it.code.toString(16) }.joinToString(","))

        for (g in listOf("41О", "41Г", "11А", "42О", "21М", " 41О ", "99Z")) {
            log("isValid('$g') = ${GroupParser.isValid(g)}, parse=${GroupParser.parse(g)?.canonicalName}")
        }
        log("matchesGroup('41О','41О') = ${GroupParser.matchesGroup("41О", "41О")}")
        log("matchesGroup('│          41О            │','41О') = ${GroupParser.matchesGroup("│          41О            │", "41О")}")
        log("cleanRawGroupName('41О') = '${GroupParser.cleanRawGroupName("41О")}'")

        val groupRegex = Regex("^[\\s]*([1-4])\\s*([1-9])\\s*[-_\\s]*([а-яА-Яa-zA-ZёЁ])[\\s]*$")
        log("GROUP_REGEX.find('41О') = ${groupRegex.find("41О")?.value}")
        log("GROUP_REGEX.find('41Г') = ${groupRegex.find("41Г")?.value}")
        val searchRegex = Regex("(?i)(?:\\b|[^0-9а-яa-z])4\\s*1\\s*[-_\\s]*[ОоOo](?:\\b|[^0-9а-яa-z])")
        log("SEARCH_REGEX.find('          41О            ') = ${searchRegex.find("          41О            ")?.value}")
        log("SEARCH_REGEX.find('41О') = ${searchRegex.find("41О")?.value}")

        val runs = MpkScheduleParser.extractUtf16LeRuns(bytes)
        log("utf16Runs = ${runs.size}")
        val text = runs.joinToString("\n")
        log("textLen = ${text.length}, containsBoxBar=${text.contains('│')}, contains41О=${text.contains("41О")}")
        log("textHead = " + text.take(90).replace("\r", "⏎").replace("\n", "¶"))
        val groupHeaderLine = "│          41Г            │          41Н            │          42Н            │          41О            │"
        log("docContainsGroupHeaderRow = ${text.contains(groupHeaderLine)}")

        val lessons = MpkScheduleParser.parseFile(bytes, "41О", "15.09.2026")
        log("parseFile lessons = ${lessons.size}")
        log("stats after parseFile = ${MpkScheduleParser.lastParseStats}")

        val grid = MpkScheduleParser.parseTableGridSchedule(text.lines(), "41О", 2, "15.09.2026")
        log("grid lessons = ${grid.size}")
        val cells = MpkScheduleParser.parseCellRunsSchedule(text.lines(), "41О", 2, "15.09.2026")
        log("cells lessons = ${cells.size}")

        val cellsOfHeader = groupHeaderLine.split("│").map { it.trim() }.filter { it.isNotBlank() }
        log("headerCells = $cellsOfHeader")
        log("headerCells validCount = ${cellsOfHeader.count { GroupParser.isValid(it) }}")

        log("PROBE DONE")
    }
}
