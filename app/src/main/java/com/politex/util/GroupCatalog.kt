package com.politex.util

import android.content.Context

/**
 * Каталог существующих групп колледжа — только КОДЫ групп, без персональных данных.
 *
 * Ассет `group_codes.csv` лежит в `app/src/main` и попадает в ОБА флейвора:
 * `standard` должен знать полный список групп (поиск группы на карте, O3-c),
 * но НЕ должен видеть ФИО — список выгружен из базы учащихся без персональных колонок.
 * Источник данных — та же выгрузка, что и `app/src/admin/assets/students.csv` (O8).
 */
class GroupCatalog(private val codes: List<String>) {

    /** Полный список кодов в порядке курс → номер → буква. */
    fun all(): List<String> = codes

    /**
     * Приводит ввод пользователя к каноническому коду группы.
     * Понимает регистр и латиницу (`41o`, `41O` → `41О`), как `GroupParser`.
     * Возвращает `null`, если такой группы в колледже нет.
     */
    fun find(input: String): String? {
        val normalized = normalize(input) ?: return null
        return codes.firstOrNull { it == normalized }
    }

    companion object {

        private const val ASSET = "group_codes.csv"

        fun load(context: Context): GroupCatalog = try {
            context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).readLines()
                .let(::parse)
        } catch (_: Exception) {
            GroupCatalog(emptyList())
        }

        fun parse(lines: List<String>): GroupCatalog =
            GroupCatalog(
                lines
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            )

        /**
         * Нормализация кода группы: убирает пробелы и дефисы, переводит латинские
         * двойники в кириллицу, приводит к верхнему регистру.
         * Курс/номер остаются как есть — «4 курс» это не про код группы.
         */
        fun normalize(input: String): String? {
            val s = input.trim()
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "")
            if (s.length < 3) return null
            val course = s[0] - '0'
            val number = s[1] - '0'
            if (course !in 1..4 || number !in 1..9) return null
            val letter = GroupParser.normalizeSpecialtyChar(s[2])
            if (!letter.isLetter()) return null
            return "$course$number$letter"
        }
    }
}