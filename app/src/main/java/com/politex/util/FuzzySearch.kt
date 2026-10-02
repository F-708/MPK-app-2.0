package com.politex.util

import kotlin.math.abs

/**
 * M5: нечёткий поиск по тексту материалов.
 *
 * Модель простая и потому предсказуемая: запрос разбивается на слова, текст — на множество
 * слов; [score] возвращает, СКОЛЬКО слов запроса нашло пару в тексте (для бейджа «3/4» и
 * сортировки выдачи по убыванию совпадений). 0 = материал не подходит совсем.
 *
 * Частичное совпадение отдаётся намеренно: задание требует «подсветку числа совпадений»,
 * а жёсткое «все слова обязаны найтись» резало бы полезную выдачу по длинным запросам.
 *
 * Слово запроса `q` матчит слово текста `t`, если:
 *  1. `t == q` (точное), ИЛИ
 *  2. общий префикс `t` и `q` ≥ 4 символов (словоформа/длинное слово: «программиров» ↔ «программирование»), ИЛИ
 *  3. расстояние Даммерау-Левенштейна ≤ 2 И длина `q` ≥ 4 (опечатки: «математика» ↔ «матиматика»).
 *
 * Пункт 3 ограничен длиной не случайно: при ≤2 для коротких слов «да» матчило бы «до», «ду»,
 * «он»… — поиск превращается в шум. Короткие слова ищутся точно и по префиксу.
 *
 * Регистр и «ё» нормализуются ([normalize]); тогл «Aa» в UI отключает нормализацию регистра.
 *
 * ponytail: LIKE/FTS не нужны — текст перебирается в памяти. Для сотен материалов это единицы
 * миллисекунд. Переходить на Room @Fts4 имеет смысл за ~1000+ материалов или если тексты
 * станут мегабайтными (тогда индекс слов стоит предпосчитать при импорте, а не на каждый запрос).
 */
object FuzzySearch {

    /** Порог «длинного слова», для которого разрешены опечатки (правило 3). */
    const val MIN_FUZZY_WORD_LENGTH = 4

    /** Максимальное расстояние Даммерау-Левенштейна, при котором слова считаются совпавшими. */
    const val MAX_DISTANCE = 2

    /**
     * Нормализация для сравнения: lowercase (если не включён регистр-фильтр) + «ё»→«е».
     * «ё» приводится отдельно, потому что в русском тексте она стабильно теряется при вводе
     * («учёба» ↔ «учеба» — одна и та же словоформа с точки зрения студента).
     */
    fun normalize(raw: String, caseSensitive: Boolean = false): String {
        val lowered = if (caseSensitive) raw else raw.lowercase()
        return lowered.replace('ё', 'е').replace('Ё', 'Е')
    }

    /** Словарь текста: слова из букв/цифр длиной ≥ 2 (одиночные буквы — шум). */
    fun wordsOf(text: String, caseSensitive: Boolean = false): Set<String> {
        val normalized = normalize(text, caseSensitive)
        val out = HashSet<String>(normalized.length / 6 + 8)
        var start = -1
        for (i in normalized.indices) {
            val c = normalized[i]
            if (c.isLetterOrDigit()) {
                if (start < 0) start = i
            } else if (start >= 0) {
                if (i - start >= 2) out.add(normalized.substring(start, i))
                start = -1
            }
        }
        if (start >= 0 && normalized.length - start >= 2) out.add(normalized.substring(start))
        return out
    }

    /** Матчит ли одно слово текста одно слово запроса (правила 1–3 из KDoc). */
    fun wordMatches(textWord: String, queryWord: String): Boolean {
        if (textWord == queryWord) return true
        if (commonPrefixLength(textWord, queryWord) >= MIN_FUZZY_WORD_LENGTH) return true
        if (queryWord.length < MIN_FUZZY_WORD_LENGTH) return false
        return damerauLevenshtein(textWord, queryWord, MAX_DISTANCE) <= MAX_DISTANCE
    }

    /**
     * Число слов запроса, нашедших пару в тексте. 0 = материал не подходит.
     * Пустой/пробельный запрос → 0 (ничего не фильтруем, но и не «совпадение»).
     */
    fun score(text: String, query: String, caseSensitive: Boolean = false): Int {
        val queryWords = wordsOf(query, caseSensitive)
        if (queryWords.isEmpty()) return 0
        val textWords = wordsOf(text, caseSensitive)
        if (textWords.isEmpty()) return 0
        var matched = 0
        for (q in queryWords) {
            if (textWords.any { wordMatches(it, q) }) matched++
        }
        return matched
    }

    fun queryWordCount(query: String, caseSensitive: Boolean = false): Int =
        wordsOf(query, caseSensitive).size

    fun commonPrefixLength(a: String, b: String): Int {
        val n = minOf(a.length, b.length)
        var i = 0
        while (i < n && a[i] == b[i]) i++
        return i
    }

    /**
     * Расстояние Даммерау-Левенштейна (вариант OSA: вставка, удаление, замена, перестановка
     * двух соседних символов). `limit` — ранний выход: как только минимум в строке DP превысил
     * лимит, возвращаем `limit + 1` (точный результат дальше не нужен).
     *
     * ponytail: OSA, а не «неограниченный» DL с переносом через уже правленые символы — для
     * человеческих опечаток разницы нет, а код втрое короче. Полная матрица O(n·m), но
     * n,m ≤ ~30 (слова), так что это микросекунды.
     */
    fun damerauLevenshtein(a: String, b: String, limit: Int = Int.MAX_VALUE): Int {
        if (a == b) return 0
        if (abs(a.length - b.length) > limit) return limit + 1
        val n = a.length
        val m = b.length
        if (n == 0) return m
        if (m == 0) return n

        // Три строки DP переиспользуются по кругу. Все три — полной длины: после ротации
        // `cur` получает бывший `twoBack`, и нулевая длина дала бы ArrayIndexOutOfBounds
        // на второй итерации. Значения twoBack читаются только при i>1, поэтому нули безопасны.
        var twoBack = IntArray(m + 1)
        var prev = IntArray(m + 1) { it }                          // строка i-1
        var cur = IntArray(m + 1)                                  // строка i

        for (i in 1..n) {
            cur[0] = i
            var rowMin = i
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = minOf(v, twoBack[j - 2] + 1)               // перестановка соседей
                }
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > limit) return limit + 1
            val recycled = twoBack
            twoBack = prev
            prev = cur
            cur = recycled
        }
        return prev[m]
    }
}
