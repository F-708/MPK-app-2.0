package com.example.data.model

/**
 * Карта колледжа: планы этажей и поиск кабинета.
 *
 * Основа — три скана пожарных планов эвакуации, по которым в колледже
 * и ориентируются. Номер кабинета однозначно говорит об этаже: 1xx — первый,
 * 2xx — второй, 3xx — третий, 4xx — четвёртый. Это правило работает всегда,
 * поэтому «куда идти» приложение знает точно даже там, где метка кабинета
 * на плане ещё не проставлена.
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

    /**
     * Известные положения кабинетов на плане.
     *
     * Координаты — доли от ширины и высоты картинки (0..1), а не пиксели:
     * так они не поедут, если план пересканируют в другом разрешении.
     *
     * ВНИМАНИЕ: это ПРИБЛИЗИТЕЛЬНАЯ разметка по сканам, а не результат замера.
     * Кабинеты, которых здесь нет, всё равно открываются — приложение покажет
     * нужный этаж и сам номер, найти его на плане не составит труда.
     * Уточнять координаты удобно здесь же: одна строка на кабинет.
     */
    val roomPins: Map<String, RoomPin> = buildMap {
        // --- 1 этаж ---
        put("101", RoomPin(Floor.FIRST, 0.53f, 0.21f))
        put("102", RoomPin(Floor.FIRST, 0.53f, 0.26f))
        put("103", RoomPin(Floor.FIRST, 0.45f, 0.37f))
        put("104", RoomPin(Floor.FIRST, 0.57f, 0.37f))
        put("105", RoomPin(Floor.FIRST, 0.63f, 0.37f))
        put("106", RoomPin(Floor.FIRST, 0.69f, 0.37f))
        put("107", RoomPin(Floor.FIRST, 0.75f, 0.37f))
        put("128", RoomPin(Floor.FIRST, 0.42f, 0.58f))
        put("130", RoomPin(Floor.FIRST, 0.42f, 0.72f))
        put("131", RoomPin(Floor.FIRST, 0.39f, 0.45f))
        put("132", RoomPin(Floor.FIRST, 0.37f, 0.39f))
        put("133", RoomPin(Floor.FIRST, 0.37f, 0.35f))
        put("134", RoomPin(Floor.FIRST, 0.39f, 0.29f))
        put("135", RoomPin(Floor.FIRST, 0.23f, 0.32f))
        put("136", RoomPin(Floor.FIRST, 0.21f, 0.32f))
        put("138", RoomPin(Floor.FIRST, 0.19f, 0.32f))
        put("140", RoomPin(Floor.FIRST, 0.19f, 0.37f))
        put("141", RoomPin(Floor.FIRST, 0.18f, 0.41f))
        put("142", RoomPin(Floor.FIRST, 0.11f, 0.47f))
        put("144", RoomPin(Floor.FIRST, 0.19f, 0.35f))
        put("145", RoomPin(Floor.FIRST, 0.18f, 0.32f))
        put("146", RoomPin(Floor.FIRST, 0.06f, 0.31f))
        put("147", RoomPin(Floor.FIRST, 0.06f, 0.27f))
        put("148", RoomPin(Floor.FIRST, 0.06f, 0.23f))
        put("149", RoomPin(Floor.FIRST, 0.08f, 0.12f))
        put("150", RoomPin(Floor.FIRST, 0.13f, 0.12f))
        put("151", RoomPin(Floor.FIRST, 0.15f, 0.21f))
        put("152", RoomPin(Floor.FIRST, 0.19f, 0.21f))
        put("153", RoomPin(Floor.FIRST, 0.22f, 0.21f))
        put("154", RoomPin(Floor.FIRST, 0.26f, 0.21f))
        put("155", RoomPin(Floor.FIRST, 0.33f, 0.25f))
        put("111", RoomPin(Floor.FIRST, 0.90f, 0.27f))
        put("112", RoomPin(Floor.FIRST, 0.95f, 0.27f))
        put("113", RoomPin(Floor.FIRST, 0.95f, 0.31f))
        put("114", RoomPin(Floor.FIRST, 0.95f, 0.35f))
        put("115", RoomPin(Floor.FIRST, 0.95f, 0.40f))
        put("116", RoomPin(Floor.FIRST, 0.94f, 0.45f))
        put("117", RoomPin(Floor.FIRST, 0.94f, 0.50f))
        put("118", RoomPin(Floor.FIRST, 0.94f, 0.61f))
        put("119", RoomPin(Floor.FIRST, 0.91f, 0.65f))
        put("120", RoomPin(Floor.FIRST, 0.93f, 0.58f))
        put("121", RoomPin(Floor.FIRST, 0.93f, 0.52f))
        put("122", RoomPin(Floor.FIRST, 0.71f, 0.42f))
        put("123", RoomPin(Floor.FIRST, 0.66f, 0.42f))
        put("124", RoomPin(Floor.FIRST, 0.62f, 0.42f))
        put("125", RoomPin(Floor.FIRST, 0.58f, 0.42f))
        put("126", RoomPin(Floor.FIRST, 0.51f, 0.42f))
        put("127", RoomPin(Floor.FIRST, 0.48f, 0.42f))

        // --- 2 этаж ---
        put("201", RoomPin(Floor.SECOND, 0.52f, 0.22f))
        put("202", RoomPin(Floor.SECOND, 0.52f, 0.31f))
        put("203", RoomPin(Floor.SECOND, 0.53f, 0.36f))
        put("204", RoomPin(Floor.SECOND, 0.59f, 0.36f))
        put("205", RoomPin(Floor.SECOND, 0.65f, 0.36f))
        put("206", RoomPin(Floor.SECOND, 0.71f, 0.36f))
        put("208", RoomPin(Floor.SECOND, 0.77f, 0.36f))
        put("209", RoomPin(Floor.SECOND, 0.80f, 0.36f))
        put("210", RoomPin(Floor.SECOND, 0.86f, 0.36f))
        put("211", RoomPin(Floor.SECOND, 0.90f, 0.36f))
        put("213", RoomPin(Floor.SECOND, 0.90f, 0.28f))
        put("214", RoomPin(Floor.SECOND, 0.90f, 0.25f))
        put("215", RoomPin(Floor.SECOND, 0.90f, 0.21f))
        put("216", RoomPin(Floor.SECOND, 0.95f, 0.21f))
        put("217", RoomPin(Floor.SECOND, 0.94f, 0.30f))
        put("218", RoomPin(Floor.SECOND, 0.94f, 0.36f))
        put("219", RoomPin(Floor.SECOND, 0.94f, 0.42f))
        put("220", RoomPin(Floor.SECOND, 0.94f, 0.47f))
        put("221", RoomPin(Floor.SECOND, 0.94f, 0.51f))
        put("222", RoomPin(Floor.SECOND, 0.93f, 0.61f))
        put("223", RoomPin(Floor.SECOND, 0.95f, 0.63f))
        put("224", RoomPin(Floor.SECOND, 0.91f, 0.62f))
        put("225", RoomPin(Floor.SECOND, 0.91f, 0.55f))
        put("226", RoomPin(Floor.SECOND, 0.77f, 0.42f))
        put("227", RoomPin(Floor.SECOND, 0.73f, 0.42f))
        put("228", RoomPin(Floor.SECOND, 0.68f, 0.42f))
        put("229", RoomPin(Floor.SECOND, 0.64f, 0.42f))
        put("230", RoomPin(Floor.SECOND, 0.61f, 0.42f))
        put("231", RoomPin(Floor.SECOND, 0.54f, 0.43f))
        put("232", RoomPin(Floor.SECOND, 0.50f, 0.70f))
        put("233", RoomPin(Floor.SECOND, 0.51f, 0.52f))
        put("234", RoomPin(Floor.SECOND, 0.51f, 0.38f))
        put("235", RoomPin(Floor.SECOND, 0.48f, 0.35f))
        put("236", RoomPin(Floor.SECOND, 0.48f, 0.32f))
        put("237", RoomPin(Floor.SECOND, 0.25f, 0.29f))
        put("238", RoomPin(Floor.SECOND, 0.20f, 0.29f))
        put("239", RoomPin(Floor.SECOND, 0.20f, 0.29f))
        put("240", RoomPin(Floor.SECOND, 0.16f, 0.29f))
        put("241", RoomPin(Floor.SECOND, 0.16f, 0.25f))
        put("242", RoomPin(Floor.SECOND, 0.12f, 0.22f))
        put("243", RoomPin(Floor.SECOND, 0.08f, 0.21f))
        put("244", RoomPin(Floor.SECOND, 0.04f, 0.21f))
        put("245", RoomPin(Floor.SECOND, 0.03f, 0.12f))
        put("246", RoomPin(Floor.SECOND, 0.06f, 0.12f))
        put("247", RoomPin(Floor.SECOND, 0.09f, 0.12f))
        put("248", RoomPin(Floor.SECOND, 0.13f, 0.12f))
        put("249", RoomPin(Floor.SECOND, 0.18f, 0.12f))
        put("250", RoomPin(Floor.SECOND, 0.19f, 0.16f))
        put("251", RoomPin(Floor.SECOND, 0.23f, 0.21f))
        put("252", RoomPin(Floor.SECOND, 0.28f, 0.21f))
        put("253", RoomPin(Floor.SECOND, 0.32f, 0.21f))
        put("254", RoomPin(Floor.SECOND, 0.36f, 0.21f))
        put("255", RoomPin(Floor.SECOND, 0.37f, 0.23f))
        put("256", RoomPin(Floor.SECOND, 0.36f, 0.26f))
        put("257", RoomPin(Floor.SECOND, 0.40f, 0.26f))
        put("258", RoomPin(Floor.SECOND, 0.43f, 0.26f))
        put("259", RoomPin(Floor.SECOND, 0.45f, 0.26f))

        // --- 3 и 4 этажи ---
        put("301", RoomPin(Floor.THIRD_FOURTH, 0.48f, 0.44f))
        put("302", RoomPin(Floor.THIRD_FOURTH, 0.49f, 0.37f))
        put("303", RoomPin(Floor.THIRD_FOURTH, 0.49f, 0.28f))
        put("304", RoomPin(Floor.THIRD_FOURTH, 0.49f, 0.25f))
        put("305", RoomPin(Floor.THIRD_FOURTH, 0.55f, 0.25f))
        put("306", RoomPin(Floor.THIRD_FOURTH, 0.55f, 0.31f))
        put("307", RoomPin(Floor.THIRD_FOURTH, 0.49f, 0.39f))
        put("308", RoomPin(Floor.THIRD_FOURTH, 0.51f, 0.40f))
        put("309", RoomPin(Floor.THIRD_FOURTH, 0.57f, 0.40f))
        put("310", RoomPin(Floor.THIRD_FOURTH, 0.62f, 0.40f))
        put("311", RoomPin(Floor.THIRD_FOURTH, 0.66f, 0.40f))
        put("312", RoomPin(Floor.THIRD_FOURTH, 0.71f, 0.40f))
        put("313", RoomPin(Floor.THIRD_FOURTH, 0.75f, 0.40f))
        put("314", RoomPin(Floor.THIRD_FOURTH, 0.79f, 0.40f))
        put("315", RoomPin(Floor.THIRD_FOURTH, 0.83f, 0.40f))
        put("317", RoomPin(Floor.THIRD_FOURTH, 0.90f, 0.42f))
        put("318", RoomPin(Floor.THIRD_FOURTH, 0.92f, 0.46f))
        put("319", RoomPin(Floor.THIRD_FOURTH, 0.92f, 0.62f))
        put("320", RoomPin(Floor.THIRD_FOURTH, 0.90f, 0.69f))
        put("321", RoomPin(Floor.THIRD_FOURTH, 0.89f, 0.66f))
        put("322", RoomPin(Floor.THIRD_FOURTH, 0.89f, 0.60f))
        put("323", RoomPin(Floor.THIRD_FOURTH, 0.89f, 0.56f))
        put("324", RoomPin(Floor.THIRD_FOURTH, 0.83f, 0.45f))
        put("325", RoomPin(Floor.THIRD_FOURTH, 0.79f, 0.45f))
        put("326", RoomPin(Floor.THIRD_FOURTH, 0.75f, 0.45f))
        put("327", RoomPin(Floor.THIRD_FOURTH, 0.71f, 0.45f))
        put("328", RoomPin(Floor.THIRD_FOURTH, 0.67f, 0.45f))
        put("329", RoomPin(Floor.THIRD_FOURTH, 0.63f, 0.45f))
        put("330", RoomPin(Floor.THIRD_FOURTH, 0.54f, 0.47f))
        put("331", RoomPin(Floor.THIRD_FOURTH, 0.30f, 0.28f))
        put("332", RoomPin(Floor.THIRD_FOURTH, 0.25f, 0.28f))
        put("333", RoomPin(Floor.THIRD_FOURTH, 0.20f, 0.28f))
        put("334", RoomPin(Floor.THIRD_FOURTH, 0.17f, 0.28f))
        put("335", RoomPin(Floor.THIRD_FOURTH, 0.18f, 0.25f))
        put("336", RoomPin(Floor.THIRD_FOURTH, 0.15f, 0.21f))
        put("337", RoomPin(Floor.THIRD_FOURTH, 0.08f, 0.21f))
        put("338", RoomPin(Floor.THIRD_FOURTH, 0.04f, 0.21f))
        put("339", RoomPin(Floor.THIRD_FOURTH, 0.03f, 0.12f))
        put("340", RoomPin(Floor.THIRD_FOURTH, 0.06f, 0.12f))
        put("341", RoomPin(Floor.THIRD_FOURTH, 0.09f, 0.12f))
        put("342", RoomPin(Floor.THIRD_FOURTH, 0.13f, 0.12f))
        put("343", RoomPin(Floor.THIRD_FOURTH, 0.17f, 0.12f))
        put("344", RoomPin(Floor.THIRD_FOURTH, 0.19f, 0.17f))
        put("345", RoomPin(Floor.THIRD_FOURTH, 0.21f, 0.21f))
        put("346", RoomPin(Floor.THIRD_FOURTH, 0.26f, 0.21f))
        put("347", RoomPin(Floor.THIRD_FOURTH, 0.30f, 0.21f))
        put("348", RoomPin(Floor.THIRD_FOURTH, 0.34f, 0.21f))
        put("401", RoomPin(Floor.THIRD_FOURTH, 0.68f, 0.17f))
        put("402", RoomPin(Floor.THIRD_FOURTH, 0.71f, 0.12f))
        put("403", RoomPin(Floor.THIRD_FOURTH, 0.78f, 0.12f))
        put("404", RoomPin(Floor.THIRD_FOURTH, 0.78f, 0.19f))
        put("405", RoomPin(Floor.THIRD_FOURTH, 0.78f, 0.24f))
    }
}

/**
 * Положение кабинета на плане.
 *
 * [x] и [y] — центр кабинета, [w] и [h] — размеры подсветки.
 * Всё в долях от ширины и высоты плана (не в пикселях):
 * так разметка не поедет, если план пересканируют в другом разрешении.
 *
 * Разметку удобно делать через `tools/map-editor.html`:
 * рисуешь прямоугольники по контуру кабинетов и получаешь готовые строки кода.
 */
data class RoomPin(
    val floor: CollegeMap.Floor,
    val x: Float,
    val y: Float,
    /** Ширина подсветки. По умолчанию — небольшая рамка. */
    val w: Float = 0.045f,
    val h: Float = 0.04f
)
