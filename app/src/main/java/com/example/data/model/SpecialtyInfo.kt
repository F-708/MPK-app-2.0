package com.example.data.model

/**
 * Описание специальности колледжа МГПК.
 */
data class SpecialtyInfo(
    val code: Char, // Буквенный код группы ('О', 'Н', 'Э', 'П', 'Т', 'Г', 'Д', 'А', 'С', 'М')
    val cipher: String, // Код специальности по ОКРБ (например "5-04-0715-05")
    val fullName: String, // Полное наименование специальности
    val shortName: String, // Краткое наименование
    val subjectsByCourse: Map<Int, List<String>> // Предметы по курсам (1..4)
)
