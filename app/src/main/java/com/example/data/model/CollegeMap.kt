package com.example.data.model

/**
 * Карта колледжа: планы этажей и поиск кабинета.
 *
 * Разметка (кабинеты, коридоры, узлы) хранится в `assets/college_map.json`
 * и готовится заказчиком в редакторе `tools/editor.html`.
 *
 * Здесь остаётся только то, что можно вывести из самого номера кабинета:
 * 1xx — первый этаж, 2xx — второй, 3xx и 4xx — общий план третьего и четвёртого.
 * Это правило работает всегда, поэтому «на каком этаже искать» приложение
 * знает даже без разметки.
 */
object CollegeMap {

    /** План этажа: файл в assets и человеческое название. */
    enum class Floor(val id: String, val title: String, val assetFile: String, val hint: String) {
        FIRST("1", "1 этаж", "map_floor1.jpg", "Актовый зал, библиотека, столовая"),
        SECOND("2", "2 этаж", "map_floor2.jpg", "Учебная часть, приёмная, преподавательская"),
        THIRD_FOURTH("3-4", "3 и 4 этажи", "map_floor34.jpg", "Один план на два этажа");

        companion object {
            fun from(id: String?): Floor = entries.firstOrNull { it.id == id } ?: FIRST
        }
    }

    /**
     * Этаж по номеру кабинета.
     *
     * Первая цифра номера — этаж. Если номер нестандартный (например «спортзал»
     * или «актовый зал»), возвращаем null: лучше показать все планы, чем соврать.
     */
    fun floorForRoom(room: String): Floor? {
        val digits = room.trim().takeWhile { it.isDigit() }
        if (digits.isEmpty()) return null
        return when (digits.first()) {
            '1' -> Floor.FIRST
            '2' -> Floor.SECOND
            '3', '4' -> Floor.THIRD_FOURTH
            else -> null
        }
    }

    /** Есть ли осмысленный номер кабинета (а не «спортзал», «актовый зал»). */
    fun looksLikeRoomNumber(room: String): Boolean =
        room.trim().firstOrNull()?.isDigit() == true

}
