package com.example.data.model

/**
 * Информация об учебной группе МГПК.
 * Формат группы: [Курс][Номер][Буква] (например: 41О, 11Э, 23О).
 */
data class GroupInfo(
    val rawName: String,
    val course: Int,
    val groupNumber: Int,
    val specialtyCode: Char,
    val specialtyCipher: String = "",
    val specialtyFullName: String = ""
) {
    /**
     * 1 курс: шестидневка (учатся по субботам, true).
     * 2, 3, 4 курсы: пятидневка (субботы нет, false).
     */
    val hasSaturdayClasses: Boolean
        get() = course == 1

    /**
     * Курсовые работы разрешены только для 2, 3 и 4 курсов.
     */
    val canHaveCourseWork: Boolean
        get() = course in 2..4

    /**
     * Дипломное проектирование разрешено ТОЛЬКО для 4 курса.
     */
    val canHaveDiploma: Boolean
        get() = course == 4

    /**
     * Каноническое отображение названия группы: e.g. "41О"
     */
    val canonicalName: String
        get() = "${course}${groupNumber}${specialtyCode.uppercaseChar()}"
}
