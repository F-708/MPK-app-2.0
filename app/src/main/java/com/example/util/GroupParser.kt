package com.example.util

import com.example.data.model.GroupInfo

/**
 * Парсер и валидатор учебных групп ГУО «МГПК».
 *
 * Формат групп: [Курс (1-4)][Номер группы (1-9)][Буква специальности (О, Н, Э, П, Т, Г, Д, А, С, М)]
 * Примеры: 41О, 11Э, 23О, 31Т, 12С, 41А, 22Д, 31Г, 11Н, 21М.
 */
object GroupParser {

    private val GROUP_REGEX = Regex("^[\\s]*([1-4])\\s*([1-9])\\s*[-_\\s]*([а-яА-Яa-zA-ZёЁ])[\\s]*$")

    // Маппинг латинских букв в соответствующие кириллические буквы специальностей колледжа
    private val LATIN_TO_CYRILLIC: Map<Char, Char> = mapOf(
        'O' to 'О', 'o' to 'О',
        'H' to 'Н', 'h' to 'Н',
        'N' to 'Н', 'n' to 'Н',
        'E' to 'Э', 'e' to 'Э',
        'P' to 'П', 'p' to 'П',
        'T' to 'Т', 't' to 'Т',
        'G' to 'Г', 'g' to 'Г',
        'D' to 'Д', 'd' to 'Д',
        'A' to 'А', 'a' to 'А',
        'C' to 'С', 'c' to 'С',
        'S' to 'С', 's' to 'С',
        'M' to 'М', 'm' to 'М'
    )

    /**
     * Создает регулярное выражение для поиска группы в тексте документов с учетом
     * пробелов, дефисов и латинских/кириллических букв.
     */
    fun createGroupSearchRegex(targetGroup: String): Regex {
        val parsed = parse(targetGroup)
        val course = parsed?.course?.toString() ?: targetGroup.filter { it.isDigit() }.take(1)
        val number = parsed?.groupNumber?.toString() ?: targetGroup.filter { it.isDigit() }.drop(1).take(1)
        val letter = parsed?.specialtyCode ?: targetGroup.filter { it.isLetter() }.firstOrNull()?.let { normalizeSpecialtyChar(it) } ?: 'О'

        val latinVariants = when (letter) {
            'О' -> "[ОоOo]"
            'Н' -> "[НнHhNn]"
            'Э' -> "[ЭэEe]"
            'П' -> "[ПпPp]"
            'Т' -> "[ТтTt]"
            'Г' -> "[ГгGg]"
            'Д' -> "[ДдDd]"
            'А' -> "[АаAa]"
            'С' -> "[СсCcSs]"
            'М' -> "[МмMm]"
            else -> "[$letter]"
        }

        return Regex("(?i)(?:\\b|[^0-9а-яa-z])$course\\s*$number\\s*[-_\\s]*$latinVariants(?:\\b|[^0-9а-яa-z])")
    }

    /**
     * Проверяет, содержится ли группа в тексте ячейки или строки.
     */
    fun matchesGroup(text: String, targetGroup: String): Boolean {
        val cleanTarget = cleanRawGroupName(targetGroup)
        val cleanText = text.replace("\\s+".toRegex(), "").uppercase()
        if (cleanText.contains(cleanTarget)) return true
        val regex = createGroupSearchRegex(targetGroup)
        return regex.containsMatchIn(text)
    }

    /**
     * Парсит строку названия группы в объект GroupInfo.
     * Возвращает null, если формат или специальность не соответствуют правилам колледжа.
     */
    fun parse(rawGroupName: String): GroupInfo? {
        val trimmed = rawGroupName.trim()
        val match = GROUP_REGEX.find(trimmed) ?: return null

        val course = match.groupValues[1].toIntOrNull() ?: return null
        val groupNumber = match.groupValues[2].toIntOrNull() ?: return null
        val rawChar = match.groupValues[3].first()

        val normalizedChar = normalizeSpecialtyChar(rawChar)

        if (!MpkCurriculum.isValidSpecialtyCode(normalizedChar)) {
            return null
        }

        val specialty = MpkCurriculum.getSpecialty(normalizedChar)

        return GroupInfo(
            rawName = trimmed,
            course = course,
            groupNumber = groupNumber,
            specialtyCode = normalizedChar,
            specialtyCipher = specialty?.cipher ?: "",
            specialtyFullName = specialty?.fullName ?: ""
        )
    }

    /**
     * Проверяет, валидно ли название группы.
     */
    fun isValid(rawGroupName: String): Boolean {
        return parse(rawGroupName) != null
    }

    /**
     * Очищает и нормализует сырую строку названия группы (например " 4 1 o " -> "41О").
     */
    fun cleanRawGroupName(rawGroupName: String): String {
        return parse(rawGroupName)?.canonicalName ?: rawGroupName.trim().replace("\\s+".toRegex(), "").uppercase()
    }

    /**
     * Преобразует символ в верхний регистр кириллицы.
     */
    fun normalizeSpecialtyChar(char: Char): Char {
        val upper = char.uppercaseChar()
        return LATIN_TO_CYRILLIC[upper] ?: LATIN_TO_CYRILLIC[char] ?: upper
    }

    /**
     * Возвращает канонический список типовых групп для быстрого выбора студентом.
     */
    fun generateCommonGroups(): List<GroupInfo> {
        val groups = mutableListOf<GroupInfo>()
        val specialtyCodes = listOf('О', 'Н', 'Э', 'П', 'Т', 'Г', 'Д', 'А', 'С', 'М')
        
        for (course in 1..4) {
            for (code in specialtyCodes) {
                val specialty = MpkCurriculum.getSpecialty(code)
                groups.add(
                    GroupInfo(
                        rawName = "${course}1$code",
                        course = course,
                        groupNumber = 1,
                        specialtyCode = code,
                        specialtyCipher = specialty?.cipher ?: "",
                        specialtyFullName = specialty?.fullName ?: ""
                    )
                )
                // Для некоторых специальностей есть 2-я подгруппа/поток
                if (code in listOf('О', 'Э', 'Т', 'Д', 'С')) {
                    groups.add(
                        GroupInfo(
                            rawName = "${course}2$code",
                            course = course,
                            groupNumber = 2,
                            specialtyCode = code,
                            specialtyCipher = specialty?.cipher ?: "",
                            specialtyFullName = specialty?.fullName ?: ""
                        )
                    )
                }
            }
        }
        return groups
    }
}
