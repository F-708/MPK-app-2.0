package com.example.util

import java.security.MessageDigest
import java.util.Locale

/**
 * Токен разблокировки приложения.
 *
 * Код выводится из номера текущей минуты и секрета, зашитого в сборку. Сервер не
 * нужен: админская и обычная версии считают одно и то же на любом телефоне.
 * Каждую минуту код меняется, поэтому старый перестаёт работать сам собой —
 * отзывать отдельно нечего, достаточно не передавать свежий код дальше.
 *
 * Это защита от «разошлось по чужим рукам», а не от взлома: цель — чтобы
 * приложение не уехало в свободное плавание, а не чтобы его нельзя было
 * разобрать.
 */
object UnlockToken {

    /**
     * Секрет. Меняем при выпуске новой версии — тогда все ранее выданные коды
     * перестают подходить, даже если их успели сохранить.
     */
    private const val SECRET = "MPK-2026-vyqzt-unlock-7f3a91"

    /** Длина кода. 6 цифр удобно диктовать голосом и вводить вручную. */
    const val DIGITS = 6

    /** Границы окна, в котором код считается верным (в минутах от текущей). */
    private const val BACKWARD_TOLERANCE_MINUTES = 1L

    /** Номер минуты по UTC — код не должен зависеть от часового пояса телефона. */
    fun minuteIndex(nowMs: Long = System.currentTimeMillis()): Long = nowMs / 60_000L

    /** Код для конкретной минуты. */
    fun codeForMinute(minute: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$SECRET:$minute".toByteArray(Charsets.UTF_8))

        // Берём первые 4 байта хеша как число и сворачиваем в 6 цифр
        var value = 0L
        for (i in 0 until 4) {
            value = (value shl 8) or (digest[i].toLong() and 0xFF)
        }
        return String.format(Locale.ROOT, "%06d", value % 1_000_000L)
    }

    /** Код, действующий прямо сейчас. */
    fun currentCode(nowMs: Long = System.currentTimeMillis()): String =
        codeForMinute(minuteIndex(nowMs))

    /** Сколько секунд осталось до смены кода. */
    fun secondsUntilChange(nowMs: Long = System.currentTimeMillis()): Int =
        (60 - (nowMs / 1000L) % 60L).toInt()

    /**
     * Проверяет введённый код.
     *
     * Принимаем текущую минуту и одну предыдущую: код, набранный на последних
     * секундах минуты, иначе не успевал сработать — пользователь начинает вводить
     * в 12:00:58, а подтверждает уже в 12:01:02.
     */
    fun isValid(input: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val clean = input.filter { it.isDigit() }
        if (clean.length != DIGITS) return false
        val now = minuteIndex(nowMs)
        for (offset in 0..BACKWARD_TOLERANCE_MINUTES) {
            if (clean == codeForMinute(now - offset)) return true
        }
        return false
    }
}
