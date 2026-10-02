package com.politex

import com.politex.util.FuzzySearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M5: нечёткий поиск — опечатки (Даммерау-Левенштейн), перестановки соседних букв,
 * «ё»→«е», совпадение по префиксу от 4 символов, регистр-тогл «Aa».
 *
 * Чистый JVM-тест: FuzzySearch не трогает Android, Robolectric здесь только замедлил бы прогон.
 */
class FuzzySearchTest {

    // ── Расстояние Даммерау-Левенштейна ──────────────────────────────────────

    @Test
    fun `расстояние считает замены вставки и удаления`() {
        assertEquals(0, FuzzySearch.damerauLevenshtein("abc", "abc"))
        assertEquals(1, FuzzySearch.damerauLevenshtein("abc", "abd"))   // замена
        assertEquals(1, FuzzySearch.damerauLevenshtein("abc", "ab"))    // удаление
        assertEquals(1, FuzzySearch.damerauLevenshtein("abc", "abcd"))  // вставка
        assertEquals(3, FuzzySearch.damerauLevenshtein("kitten", "sitting"))
        assertEquals(3, FuzzySearch.damerauLevenshtein("", "abc"))
    }

    @Test
    fun `перестановка соседних символов стоит единицу`() {
        // Главное отличие Даммерау от Левенштейна: «фиизка» → «физика» одной правкой, а не двумя
        assertEquals(1, FuzzySearch.damerauLevenshtein("физика", "фиизка"))
        assertEquals(1, FuzzySearch.damerauLevenshtein("ab", "ba"))
    }

    @Test
    fun `лимит даёт ранний выход и возвращает лимит плюс один`() {
        // «ранета» и «дерево» разошлись дальше двойки — точное число нам уже не нужно
        assertEquals(3, FuzzySearch.damerauLevenshtein("ранета", "дерево", limit = 2))
        assertEquals(3, FuzzySearch.damerauLevenshtein("abcdef", "uvwxyz", limit = 2))
        // разница длин больше лимита — выход до входа в цикл
        assertEquals(3, FuzzySearch.damerauLevenshtein("а", "абвгд", limit = 2))
    }

    // ── Совпадение слов ──────────────────────────────────────────────────────

    @Test
    fun `опечатка в длинном слове считается совпадением`() {
        assertTrue(FuzzySearch.wordMatches("математика", "матиматика"))
        assertTrue(FuzzySearch.wordMatches("программирование", "програмирование"))
        assertTrue(FuzzySearch.wordMatches("физика", "фиизка"))
    }

    @Test
    fun `две опечатки ещё совпадают, три уже нет`() {
        assertTrue(FuzzySearch.wordMatches("электротехника", "эликтратехника"))
        assertFalse(FuzzySearch.wordMatches("электротехника", "аликтратахника"))
    }

    @Test
    fun `совпадение по префиксу от четырёх символов`() {
        assertTrue(FuzzySearch.wordMatches("программирование", "программиров"))
        assertTrue(FuzzySearch.wordMatches("сопротивление", "сопр"))
        // три символа — слишком общий префикс, иначе «сом» найдёт «сопротивление»
        assertFalse(FuzzySearch.wordMatches("сопротивление", "сом"))
    }

    @Test
    fun `короткие слова ищутся только точно`() {
        assertTrue(FuzzySearch.wordMatches("да", "да"))
        // при допуске 2 для трёхбуквенных слов «да» сматчило бы «до», «ду», «од»… — шум
        assertFalse(FuzzySearch.wordMatches("до", "да"))
        assertFalse(FuzzySearch.wordMatches("сон", "сын"))
    }

    // ── Нормализация ─────────────────────────────────────────────────────────

    @Test
    fun `ё приводится к е`() {
        assertEquals("учеба", FuzzySearch.normalize("учёба"))
        assertEquals("Ё→е работает и в регистр-чувствительном режиме", "Учеба", FuzzySearch.normalize("Учёба", caseSensitive = true))
        assertTrue("запрос с «е» находит текст с «ё»", FuzzySearch.score("Учёба в политехе", "учеба") > 0)
        assertTrue("и наоборот", FuzzySearch.score("Учеба в политехе", "учёба") > 0)
    }

    @Test
    fun `регистр по умолчанию не важен`() {
        assertEquals(1, FuzzySearch.score("ЛЕКЦИЯ по ТОЭ", "лекция"))
        assertEquals(1, FuzzySearch.score("лекция по тоэ", "ЛЕКЦИЯ"))
    }

    @Test
    fun `тогл Aa включает регистр-чувствительность`() {
        assertEquals(1, FuzzySearch.score("ЛЕКЦИЯ по ТОЭ", "ЛЕКЦИЯ", caseSensitive = true))
        assertEquals("с включённым Aa другой регистр не совпадает", 0, FuzzySearch.score("ЛЕКЦИЯ по ТОЭ", "лекция", caseSensitive = true))
    }

    // ── score: мультисловные запросы ─────────────────────────────────────────

    @Test
    fun `считаются только найденные слова запроса`() {
        val text = "Методичка по математике, лекция 3, пределы"
        assertEquals(3, FuzzySearch.score(text, "математика лекция пределы"))
        // частичное совпадение отдаётся честно: 3 из 4 слов найдены, «физики» в тексте нет.
        // Бейдж «3/4» в выдаче показывает ровно это, а сортировка по убыванию выводит полные выше.
        assertEquals(3, FuzzySearch.score(text, "математика лекция пределы физика"))
        assertEquals("ни одно слово не найдено — материал не подходит", 0, FuzzySearch.score(text, "физика химия"))
    }

    @Test
    fun `словоформа находится по префиксу`() {
        assertEquals(1, FuzzySearch.score("Методичка по математике", "математик"))
    }

    @Test
    fun `число совпадений возвращается для подсветки`() {
        assertEquals(2, FuzzySearch.score("ТОЭ лабораторная работа 4", "тоэ лабараторная"))
        assertEquals(2, FuzzySearch.queryWordCount("тоэ лабараторная"))
    }

    @Test
    fun `повторяющиеся слова запроса считаются один раз`() {
        assertEquals(1, FuzzySearch.queryWordCount("лекция лекция ЛЕКЦИЯ"))
        assertEquals(1, FuzzySearch.score("первая лекция", "лекция лекция"))
    }

    @Test
    fun `пустой запрос и пустой текст дают ноль`() {
        assertEquals(0, FuzzySearch.score("какой-то текст", ""))
        assertEquals(0, FuzzySearch.score("какой-то текст", "   "))
        assertEquals(0, FuzzySearch.score("", "запрос"))
    }

    @Test
    fun `разбивка на слова режет пунктуацию и одиночные буквы`() {
        val words = FuzzySearch.wordsOf("ТОЭ, лаба №4 — сдал! я и a")
        assertTrue(words.contains("тоэ"))
        assertTrue(words.contains("лаба"))
        assertTrue(words.contains("сдал"))
        assertTrue("цифры — часть слова", FuzzySearch.wordsOf("лаба 12").contains("12"))
        assertFalse("одиночная цифра — шум", FuzzySearch.wordsOf("лаба №4").contains("4"))
        assertFalse("одиночная буква — шум", words.contains("я"))
        assertFalse("одиночная латинская буква — шум", words.contains("a"))
        assertFalse("знаки препинания не слова", words.contains("—"))
    }

    @Test
    fun `поиск по длинному тексту с опечаткой в середине`() {
        val text = buildString {
            repeat(200) { append("обычный текст конспекта номер $it. ") }
            append("Электротехника и электроника — раздел про цепи")
        }
        assertTrue(FuzzySearch.score(text, "эликтротехника цепи") > 0)
    }
}
