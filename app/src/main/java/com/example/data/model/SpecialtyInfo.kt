package com.example.data.model

/**
 * Описание специальности колледжа МГПК.
 */
data class SpecialtyInfo(
    val code: Char, // Буквенный код группы ('О', 'Н', 'Э', 'П', 'Т', 'Г', 'Д', 'А', 'С', 'М')
    val cipher: String, // Код специальности по ОКРБ (например "5-04-0715-05")
    val fullName: String, // Полное наименование специальности
    val shortName: String, // Краткое наименование
    val qualification: String = "", // Квалификация выпускника
    val workerProfessions: List<String> = emptyList(), // Рабочие профессии, получаемые вместе со специальностью
    val subjectsByCourse: Map<Int, List<String>>, // Предметы по курсам (1..4)
    /**
     * Курсы, по которым достоверного перечня предметов нет.
     * Например, у «Маркетинговой деятельности» в материалах колледжа на месте
     * 3-го курса ошибочно лежат предметы машиностроителей.
     */
    val incompleteCourses: Set<Int> = emptySet()
)
