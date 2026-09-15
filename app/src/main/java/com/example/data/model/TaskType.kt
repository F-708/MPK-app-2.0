package com.example.data.model

/**
 * Типы учебных заданий колледжа.
 * Бейдж для домашнего задания строго: «ДЗ».
 */
enum class TaskType(val displayName: String, val badge: String) {
    HOMEWORK("Домашнее задание", "ДЗ"),
    LAB_WORK("Лабораторная работа", "Лаб"),
    PRACTICAL_WORK("Практическая работа", "Практ"),
    CONTROL_WORK("Контрольная работа", "КР"),
    COURSE_WORK("Курсовая работа", "Курсовая"),
    DIPLOMA("Дипломное проектирование", "Диплом");

    companion object {
        fun fromDisplayName(name: String): TaskType {
            return entries.firstOrNull { 
                it.displayName.equals(name, ignoreCase = true) || 
                it.badge.equals(name, ignoreCase = true) ||
                it.name.equals(name, ignoreCase = true)
            } ?: HOMEWORK
        }

        /**
         * Список доступных типов заданий для конкретного курса:
         * - 1 курс: ДЗ, Лабораторная, Практическая, Контрольная
         * - 2-3 курсы: + Курсовая
         * - 4 курс: + Диплом
         */
        fun getAvailableForCourse(course: Int): List<TaskType> {
            val list = mutableListOf(
                HOMEWORK,
                LAB_WORK,
                PRACTICAL_WORK,
                CONTROL_WORK
            )
            if (course in 2..4) {
                list.add(COURSE_WORK)
            }
            if (course == 4) {
                list.add(DIPLOMA)
            }
            return list
        }
    }
}
