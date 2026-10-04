package com.politex.util

/**
 * R1-СЖАТИЕ: разбор строки CSV с `;`-разделителем и кавычками — был приватной копией
 * в StudentsRepository,TeachersRepository резал наивным split(';').
 */
object Csv {
    fun splitRow(line: String): List<String> {
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ';' && !inQuotes -> {
                    cells.add(sb.toString())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
            i++
        }
        cells.add(sb.toString())
        return cells
    }

    fun stripBom(s: String): String = if (s.firstOrNull() == '﻿') s.substring(1) else s
}
