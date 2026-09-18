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
     * Разметка сделана инструментом `tools/editor.html` по контурам кабинетов,
     * затем границы прижаты к линиям стен скриптом `tools/snap_pins.py`.
     * Кабинеты, которых здесь нет, всё равно открываются — приложение покажет
     * нужный этаж и сам номер, найти его на плане не составит труда.
     */
    val roomPins: Map<String, RoomPin> = buildMap {
        // --- 1 этаж ---
        put("101", RoomPin(Floor.FIRST, 0.4927f, 0.2411f, 0.0138f, 0.0452f))
        put("102", RoomPin(Floor.FIRST, 0.5103f, 0.2705f, 0.0249f, 0.0427f))
        put("103", RoomPin(Floor.FIRST, 0.5331f, 0.4096f, 0.0224f, 0.0794f))
        put("104", RoomPin(Floor.FIRST, 0.6475f, 0.4096f, 0.0894f, 0.0794f))
        put("104-1", RoomPin(Floor.FIRST, 0.5748f, 0.4011f, 0.0610f, 0.0574f))
        put("105", RoomPin(Floor.FIRST, 0.7193f, 0.4109f, 0.0525f, 0.0769f))
        put("106", RoomPin(Floor.FIRST, 0.7683f, 0.4109f, 0.0456f, 0.0769f))
        put("107", RoomPin(Floor.FIRST, 0.8392f, 0.4109f, 0.0602f, 0.0769f))
        put("107-1", RoomPin(Floor.FIRST, 0.7992f, 0.4109f, 0.0198f, 0.0769f))
        put("108", RoomPin(Floor.FIRST, 0.8839f, 0.4274f, 0.0258f, 0.0220f))
        put("109", RoomPin(Floor.FIRST, 0.8912f, 0.3846f, 0.0370f, 0.0635f))
        put("110", RoomPin(Floor.FIRST, 0.8912f, 0.3376f, 0.0370f, 0.0305f))
        put("111", RoomPin(Floor.FIRST, 0.8968f, 0.2918f, 0.0550f, 0.0635f))
        put("112", RoomPin(Floor.FIRST, 0.9506f, 0.3071f, 0.0404f, 0.0916f))
        put("113", RoomPin(Floor.FIRST, 0.9480f, 0.3828f, 0.0456f, 0.0598f))
        put("114", RoomPin(Floor.FIRST, 0.9480f, 0.4274f, 0.0456f, 0.0293f))
        put("115", RoomPin(Floor.FIRST, 0.9514f, 0.4756f, 0.0387f, 0.0672f))
        put("116", RoomPin(Floor.FIRST, 0.9428f, 0.5580f, 0.0490f, 0.0977f))
        put("117", RoomPin(Floor.FIRST, 0.9428f, 0.6874f, 0.0490f, 0.0904f))
        put("117-1", RoomPin(Floor.FIRST, 0.9428f, 0.6245f, 0.0490f, 0.0354f))
        put("118", RoomPin(Floor.FIRST, 0.9424f, 0.7503f, 0.0499f, 0.0354f))
        put("119", RoomPin(Floor.FIRST, 0.8947f, 0.7576f, 0.0456f, 0.0232f))
        put("120", RoomPin(Floor.FIRST, 0.8942f, 0.7149f, 0.0464f, 0.0452f))
        put("121", RoomPin(Floor.FIRST, 0.8934f, 0.6166f, 0.0482f, 0.1538f))
        put("122", RoomPin(Floor.FIRST, 0.7670f, 0.5098f, 0.0447f, 0.0623f))
        put("123", RoomPin(Floor.FIRST, 0.7279f, 0.5079f, 0.0353f, 0.0659f))
        put("124", RoomPin(Floor.FIRST, 0.6995f, 0.5085f, 0.0249f, 0.0647f))
        put("125", RoomPin(Floor.FIRST, 0.6668f, 0.5067f, 0.0439f, 0.0635f))
        put("126", RoomPin(Floor.FIRST, 0.6354f, 0.5073f, 0.0206f, 0.0672f))
        put("127", RoomPin(Floor.FIRST, 0.6148f, 0.5067f, 0.0206f, 0.0684f))
        put("128", RoomPin(Floor.FIRST, 0.4746f, 0.6661f, 0.0997f, 0.1893f))
        put("128-1", RoomPin(Floor.FIRST, 0.4364f, 0.5525f, 0.0232f, 0.0379f))
        put("129", RoomPin(Floor.FIRST, 0.5047f, 0.7863f, 0.0172f, 0.0513f))
        put("130", RoomPin(Floor.FIRST, 0.4708f, 0.7863f, 0.0507f, 0.0513f))
        put("131", RoomPin(Floor.FIRST, 0.4910f, 0.5220f, 0.0447f, 0.0232f))
        put("132", RoomPin(Floor.FIRST, 0.4553f, 0.5018f, 0.0146f, 0.0586f))
        put("133", RoomPin(Floor.FIRST, 0.4377f, 0.4933f, 0.0206f, 0.0757f))
        put("134", RoomPin(Floor.FIRST, 0.4441f, 0.3669f, 0.0335f, 0.1770f))
        put("135", RoomPin(Floor.FIRST, 0.2794f, 0.3181f, 0.0344f, 0.0696f))
        put("136", RoomPin(Floor.FIRST, 0.2502f, 0.3346f, 0.0275f, 0.0415f))
        put("137", RoomPin(Floor.FIRST, 0.2171f, 0.3712f, 0.0215f, 0.0269f))
        put("138", RoomPin(Floor.FIRST, 0.2068f, 0.3175f, 0.0198f, 0.0708f))
        put("139", RoomPin(Floor.FIRST, 0.1767f, 0.3199f, 0.0387f, 0.0757f))
        put("139-1", RoomPin(Floor.FIRST, 0.1445f, 0.3175f, 0.0241f, 0.0488f))
        put("141", RoomPin(Floor.FIRST, 0.1389f, 0.3694f, 0.0387f, 0.0330f))
        put("142", RoomPin(Floor.FIRST, 0.1148f, 0.4524f, 0.0868f, 0.1258f))
        put("142-1", RoomPin(Floor.FIRST, 0.1500f, 0.5391f, 0.0163f, 0.0476f))
        put("142-2", RoomPin(Floor.FIRST, 0.0808f, 0.5397f, 0.0189f, 0.0464f))
        put("143", RoomPin(Floor.FIRST, 0.0916f, 0.3742f, 0.0353f, 0.0305f))
        put("144", RoomPin(Floor.FIRST, 0.0873f, 0.3260f, 0.0267f, 0.0635f))
        put("145", RoomPin(Floor.FIRST, 0.0873f, 0.2766f, 0.0267f, 0.0379f))
        put("146", RoomPin(Floor.FIRST, 0.0636f, 0.2601f, 0.0189f, 0.0195f))
        put("147", RoomPin(Floor.FIRST, 0.0649f, 0.2295f, 0.0163f, 0.0415f))
        put("148", RoomPin(Floor.FIRST, 0.0645f, 0.1813f, 0.0172f, 0.0574f))
        put("149", RoomPin(Floor.FIRST, 0.0735f, 0.1221f, 0.0353f, 0.0659f))
        put("150", RoomPin(Floor.FIRST, 0.1199f, 0.1221f, 0.0610f, 0.0659f))
        put("151", RoomPin(Floor.FIRST, 0.1380f, 0.2314f, 0.0232f, 0.0794f))
        put("152", RoomPin(Floor.FIRST, 0.1702f, 0.2247f, 0.0413f, 0.0708f))
        put("153", RoomPin(Floor.FIRST, 0.2206f, 0.2234f, 0.0610f, 0.0733f))
        put("154", RoomPin(Floor.FIRST, 0.2696f, 0.2259f, 0.0387f, 0.0684f))
        put("155", RoomPin(Floor.FIRST, 0.3401f, 0.2821f, 0.0989f, 0.0733f))

        // --- 2 этаж ---
        put("201", RoomPin(Floor.SECOND, 0.5378f, 0.2739f, 0.0335f, 0.0291f))
        put("201-1", RoomPin(Floor.SECOND, 0.5382f, 0.2461f, 0.0326f, 0.0267f))
        put("201-2", RoomPin(Floor.SECOND, 0.5382f, 0.3176f, 0.0326f, 0.0582f))
        put("202", RoomPin(Floor.SECOND, 0.5365f, 0.3939f, 0.0412f, 0.0364f))
        put("203", RoomPin(Floor.SECOND, 0.5657f, 0.3806f, 0.0172f, 0.0630f))
        put("204", RoomPin(Floor.SECOND, 0.5991f, 0.3830f, 0.0498f, 0.0582f))
        put("205", RoomPin(Floor.SECOND, 0.6446f, 0.3824f, 0.0429f, 0.0570f))
        put("206", RoomPin(Floor.SECOND, 0.6854f, 0.3824f, 0.0421f, 0.0570f))
        put("207", RoomPin(Floor.SECOND, 0.7142f, 0.3830f, 0.0155f, 0.0582f))
        put("208", RoomPin(Floor.SECOND, 0.7399f, 0.3836f, 0.0361f, 0.0594f))
        put("209", RoomPin(Floor.SECOND, 0.7777f, 0.3842f, 0.0429f, 0.0606f))
        put("210", RoomPin(Floor.SECOND, 0.8249f, 0.3842f, 0.0567f, 0.0606f))
        put("211", RoomPin(Floor.SECOND, 0.8622f, 0.3842f, 0.0180f, 0.0606f))
        put("212", RoomPin(Floor.SECOND, 0.8910f, 0.4103f, 0.0429f, 0.0497f))
        put("213", RoomPin(Floor.SECOND, 0.8953f, 0.3461f, 0.0481f, 0.0861f))
        put("214", RoomPin(Floor.SECOND, 0.8910f, 0.2891f, 0.0429f, 0.0303f))
        put("215", RoomPin(Floor.SECOND, 0.8884f, 0.2582f, 0.0343f, 0.0315f))
        put("216", RoomPin(Floor.SECOND, 0.9425f, 0.2733f, 0.0361f, 0.0667f))
        put("217", RoomPin(Floor.SECOND, 0.9403f, 0.3479f, 0.0403f, 0.0824f))
        put("218", RoomPin(Floor.SECOND, 0.9403f, 0.4030f, 0.0403f, 0.0279f))
        put("219", RoomPin(Floor.SECOND, 0.9403f, 0.4473f, 0.0403f, 0.0606f))
        put("220", RoomPin(Floor.SECOND, 0.9403f, 0.4891f, 0.0403f, 0.0230f))
        put("221", RoomPin(Floor.SECOND, 0.9403f, 0.5648f, 0.0403f, 0.1285f))
        put("222", RoomPin(Floor.SECOND, 0.9433f, 0.6552f, 0.0343f, 0.0521f))
        put("223", RoomPin(Floor.SECOND, 0.9421f, 0.6994f, 0.0369f, 0.0364f))
        put("224", RoomPin(Floor.SECOND, 0.8880f, 0.6612f, 0.0369f, 0.0812f))
        put("225", RoomPin(Floor.SECOND, 0.8940f, 0.5485f, 0.0506f, 0.0812f))
        put("225-1", RoomPin(Floor.SECOND, 0.8876f, 0.6042f, 0.0378f, 0.0303f))
        put("226", RoomPin(Floor.SECOND, 0.7768f, 0.4697f, 0.0412f, 0.0667f))
        put("227", RoomPin(Floor.SECOND, 0.7395f, 0.4739f, 0.0352f, 0.0582f))
        put("228", RoomPin(Floor.SECOND, 0.6948f, 0.4685f, 0.0541f, 0.0642f))
        put("228-1", RoomPin(Floor.SECOND, 0.6742f, 0.4752f, 0.0146f, 0.0582f))
        put("229", RoomPin(Floor.SECOND, 0.6571f, 0.4709f, 0.0180f, 0.0691f))
        put("230", RoomPin(Floor.SECOND, 0.6395f, 0.4745f, 0.0206f, 0.0618f))
        put("231", RoomPin(Floor.SECOND, 0.5429f, 0.4939f, 0.0283f, 0.0352f))
        put("232", RoomPin(Floor.SECOND, 0.5197f, 0.7315f, 0.0489f, 0.0497f))
        put("233", RoomPin(Floor.SECOND, 0.4837f, 0.4933f, 0.0266f, 0.0339f))
        put("234", RoomPin(Floor.SECOND, 0.4854f, 0.4018f, 0.0300f, 0.0303f))
        put("235", RoomPin(Floor.SECOND, 0.4841f, 0.3733f, 0.0309f, 0.0267f))
        put("236", RoomPin(Floor.SECOND, 0.4854f, 0.3230f, 0.0300f, 0.0739f))
        put("237", RoomPin(Floor.SECOND, 0.3258f, 0.2988f, 0.0575f, 0.0715f))
        put("238", RoomPin(Floor.SECOND, 0.2708f, 0.2982f, 0.0163f, 0.0703f))
        put("239", RoomPin(Floor.SECOND, 0.2318f, 0.2982f, 0.0584f, 0.0727f))
        put("240", RoomPin(Floor.SECOND, 0.1794f, 0.3042f, 0.0481f, 0.0558f))
        put("241", RoomPin(Floor.SECOND, 0.1760f, 0.2612f, 0.0343f, 0.0230f))
        put("242", RoomPin(Floor.SECOND, 0.1159f, 0.2297f, 0.0515f, 0.0497f))
        put("242-1", RoomPin(Floor.SECOND, 0.1498f, 0.2297f, 0.0163f, 0.0497f))
        put("243", RoomPin(Floor.SECOND, 0.0614f, 0.2279f, 0.0266f, 0.0533f))
        put("244", RoomPin(Floor.SECOND, 0.0326f, 0.2279f, 0.0326f, 0.0533f))
        put("245", RoomPin(Floor.SECOND, 0.0343f, 0.1261f, 0.0361f, 0.0606f))
        put("246", RoomPin(Floor.SECOND, 0.0725f, 0.1345f, 0.0403f, 0.0727f))
        put("247", RoomPin(Floor.SECOND, 0.1103f, 0.1273f, 0.0352f, 0.0630f))
        put("248", RoomPin(Floor.SECOND, 0.1532f, 0.1333f, 0.0506f, 0.0752f))
        put("249", RoomPin(Floor.SECOND, 0.2000f, 0.1224f, 0.0446f, 0.0533f))
        put("250", RoomPin(Floor.SECOND, 0.2060f, 0.1703f, 0.0326f, 0.0424f))
        put("251", RoomPin(Floor.SECOND, 0.2270f, 0.2273f, 0.0575f, 0.0691f))
        put("252", RoomPin(Floor.SECOND, 0.2833f, 0.2273f, 0.0549f, 0.0691f))
        put("253", RoomPin(Floor.SECOND, 0.3283f, 0.2273f, 0.0386f, 0.0691f))
        put("254", RoomPin(Floor.SECOND, 0.3575f, 0.2424f, 0.0197f, 0.0388f))
        put("255", RoomPin(Floor.SECOND, 0.3760f, 0.2594f, 0.0206f, 0.0461f))
        put("256", RoomPin(Floor.SECOND, 0.4026f, 0.2570f, 0.0326f, 0.0509f))
        put("257", RoomPin(Floor.SECOND, 0.4296f, 0.2570f, 0.0215f, 0.0509f))
        put("258", RoomPin(Floor.SECOND, 0.4541f, 0.2467f, 0.0275f, 0.0303f))
        put("259", RoomPin(Floor.SECOND, 0.4845f, 0.2515f, 0.0335f, 0.0376f))

        // --- 3 и 4 этажи ---
        put("301", RoomPin(Floor.THIRD_FOURTH, 0.4936f, 0.4854f, 0.0189f, 0.0947f))
        put("302", RoomPin(Floor.THIRD_FOURTH, 0.5000f, 0.4102f, 0.0318f, 0.0558f))
        put("303", RoomPin(Floor.THIRD_FOURTH, 0.5000f, 0.3489f, 0.0318f, 0.0667f))
        put("304", RoomPin(Floor.THIRD_FOURTH, 0.5000f, 0.2858f, 0.0318f, 0.0595f))
        put("305", RoomPin(Floor.THIRD_FOURTH, 0.5554f, 0.2852f, 0.0361f, 0.0583f))
        put("306", RoomPin(Floor.THIRD_FOURTH, 0.5528f, 0.3465f, 0.0412f, 0.0643f))
        put("307", RoomPin(Floor.THIRD_FOURTH, 0.5558f, 0.4041f, 0.0335f, 0.0510f))
        put("308", RoomPin(Floor.THIRD_FOURTH, 0.5807f, 0.4126f, 0.0197f, 0.0801f))
        put("309", RoomPin(Floor.THIRD_FOURTH, 0.6197f, 0.4029f, 0.0567f, 0.0607f))
        put("310", RoomPin(Floor.THIRD_FOURTH, 0.6691f, 0.4150f, 0.0455f, 0.0752f))
        put("311", RoomPin(Floor.THIRD_FOURTH, 0.7288f, 0.4035f, 0.0361f, 0.0619f))
        put("311-1", RoomPin(Floor.THIRD_FOURTH, 0.7013f, 0.4029f, 0.0189f, 0.0607f))
        put("312", RoomPin(Floor.THIRD_FOURTH, 0.7691f, 0.4126f, 0.0446f, 0.0801f))
        put("313", RoomPin(Floor.THIRD_FOURTH, 0.8090f, 0.4150f, 0.0352f, 0.0752f))
        put("314", RoomPin(Floor.THIRD_FOURTH, 0.8425f, 0.4126f, 0.0335f, 0.0801f))
        put("315", RoomPin(Floor.THIRD_FOURTH, 0.8760f, 0.4150f, 0.0335f, 0.0752f))
        put("316", RoomPin(Floor.THIRD_FOURTH, 0.9399f, 0.3501f, 0.0172f, 0.1347f))
        put("316-1", RoomPin(Floor.THIRD_FOURTH, 0.9129f, 0.3538f, 0.0369f, 0.1177f))
        put("316-2", RoomPin(Floor.THIRD_FOURTH, 0.9137f, 0.2797f, 0.0386f, 0.0255f))
        put("316-3", RoomPin(Floor.THIRD_FOURTH, 0.9670f, 0.2797f, 0.0283f, 0.0255f))
        put("316-4", RoomPin(Floor.THIRD_FOURTH, 0.9639f, 0.3083f, 0.0412f, 0.0267f))
        put("316-5", RoomPin(Floor.THIRD_FOURTH, 0.9639f, 0.3368f, 0.0412f, 0.0303f))
        put("316-6", RoomPin(Floor.THIRD_FOURTH, 0.9665f, 0.3805f, 0.0361f, 0.0570f))
        put("316-7", RoomPin(Floor.THIRD_FOURTH, 0.9652f, 0.4242f, 0.0352f, 0.0303f))
        put("317", RoomPin(Floor.THIRD_FOURTH, 0.9652f, 0.4678f, 0.0318f, 0.0570f))
        put("318", RoomPin(Floor.THIRD_FOURTH, 0.9592f, 0.5303f, 0.0438f, 0.0631f))
        put("318-1", RoomPin(Floor.THIRD_FOURTH, 0.9657f, 0.5752f, 0.0378f, 0.0267f))
        put("319", RoomPin(Floor.THIRD_FOURTH, 0.9609f, 0.6468f, 0.0472f, 0.1214f))
        put("320", RoomPin(Floor.THIRD_FOURTH, 0.9657f, 0.7233f, 0.0343f, 0.0316f))
        put("321", RoomPin(Floor.THIRD_FOURTH, 0.9155f, 0.7051f, 0.0438f, 0.0777f))
        put("322", RoomPin(Floor.THIRD_FOURTH, 0.9150f, 0.6323f, 0.0429f, 0.0680f))
        put("323", RoomPin(Floor.THIRD_FOURTH, 0.9150f, 0.5570f, 0.0429f, 0.0825f))
        put("324", RoomPin(Floor.THIRD_FOURTH, 0.7970f, 0.4885f, 0.0421f, 0.0692f))
        put("325", RoomPin(Floor.THIRD_FOURTH, 0.7588f, 0.4885f, 0.0361f, 0.0692f))
        put("326", RoomPin(Floor.THIRD_FOURTH, 0.7300f, 0.4958f, 0.0215f, 0.0546f))
        put("327", RoomPin(Floor.THIRD_FOURTH, 0.7043f, 0.4945f, 0.0335f, 0.0570f))
        put("328", RoomPin(Floor.THIRD_FOURTH, 0.6773f, 0.4885f, 0.0223f, 0.0692f))
        put("329", RoomPin(Floor.THIRD_FOURTH, 0.6575f, 0.4885f, 0.0189f, 0.0692f))
        put("330", RoomPin(Floor.THIRD_FOURTH, 0.5635f, 0.4933f, 0.0232f, 0.0789f))
        put("331", RoomPin(Floor.THIRD_FOURTH, 0.3403f, 0.3283f, 0.0352f, 0.0595f))
        put("331-1", RoomPin(Floor.THIRD_FOURTH, 0.3146f, 0.3356f, 0.0146f, 0.0400f))
        put("332", RoomPin(Floor.THIRD_FOURTH, 0.2807f, 0.3161f, 0.0223f, 0.0740f))
        put("333", RoomPin(Floor.THIRD_FOURTH, 0.2412f, 0.3222f, 0.0584f, 0.0619f))
        put("334", RoomPin(Floor.THIRD_FOURTH, 0.1901f, 0.3258f, 0.0455f, 0.0546f))
        put("335", RoomPin(Floor.THIRD_FOURTH, 0.1845f, 0.2852f, 0.0343f, 0.0267f))
        put("336", RoomPin(Floor.THIRD_FOURTH, 0.1210f, 0.2530f, 0.0498f, 0.0522f))
        put("337", RoomPin(Floor.THIRD_FOURTH, 0.0687f, 0.2506f, 0.0258f, 0.0473f))
        put("338", RoomPin(Floor.THIRD_FOURTH, 0.0408f, 0.2506f, 0.0318f, 0.0473f))
        put("339", RoomPin(Floor.THIRD_FOURTH, 0.0412f, 0.1511f, 0.0361f, 0.0619f))
        put("340", RoomPin(Floor.THIRD_FOURTH, 0.0897f, 0.1559f, 0.0627f, 0.0716f))
        put("341", RoomPin(Floor.THIRD_FOURTH, 0.1352f, 0.1559f, 0.0300f, 0.0716f))
        put("342", RoomPin(Floor.THIRD_FOURTH, 0.1674f, 0.1450f, 0.0378f, 0.0498f))
        put("343", RoomPin(Floor.THIRD_FOURTH, 0.2077f, 0.1462f, 0.0429f, 0.0522f))
        put("344", RoomPin(Floor.THIRD_FOURTH, 0.2129f, 0.1930f, 0.0326f, 0.0413f))
        put("345", RoomPin(Floor.THIRD_FOURTH, 0.2193f, 0.2518f, 0.0215f, 0.0765f))
        put("346", RoomPin(Floor.THIRD_FOURTH, 0.2472f, 0.2512f, 0.0343f, 0.0777f))
        put("347", RoomPin(Floor.THIRD_FOURTH, 0.2931f, 0.2458f, 0.0575f, 0.0667f))
        put("348", RoomPin(Floor.THIRD_FOURTH, 0.3399f, 0.2470f, 0.0395f, 0.0643f))
        put("401", RoomPin(Floor.THIRD_FOURTH, 0.6845f, 0.2215f, 0.0438f, 0.0595f))
        put("402", RoomPin(Floor.THIRD_FOURTH, 0.6987f, 0.1602f, 0.0687f, 0.0655f))
        put("403", RoomPin(Floor.THIRD_FOURTH, 0.7622f, 0.1626f, 0.0584f, 0.0607f))
        put("404", RoomPin(Floor.THIRD_FOURTH, 0.7781f, 0.2227f, 0.0352f, 0.0570f))
        put("405", RoomPin(Floor.THIRD_FOURTH, 0.7657f, 0.2937f, 0.0567f, 0.0485f))
    }
}

/**
 * Положение кабинета на плане.
 *
 * [x] и [y] — центр кабинета, [w] и [h] — размеры подсветки.
 * Всё в долях от ширины и высоты плана (не в пикселях):
 * так разметка не поедет, если план пересканируют в другом разрешении.
 *
 * Разметку удобно делать через `tools/editor.html`:
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
