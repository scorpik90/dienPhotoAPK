package ru.delivery.photo.domain

/**
 * Извлечение модели и серийного номера из распознанного текста этикетки.
 *
 * Важно: результат этого разбора — только предложение пользователю.
 * Подстановка модели/серийника в водяной знак выполняется исключительно после
 * ручного подтверждения: ошибка OCR в штампе выглядит как достоверный номер.
 *
 * Ориентир — шильдики дизель-генераторов (EAC, «DIESEL GENERATING SETS»):
 *
 * ```
 * TYPE [DCS-265KVA]  NO. [26100347]
 * ```
 */
object SerialPatterns {

    /** Якоря серийного номера. Совпадение требует отдельного слова. */
    private val SERIAL_ANCHORS = listOf(
        // Поле «NO.» — серийный номер изделия на шильдике.
        Regex("""(?iu)(?<![a-zа-яё0-9])no\.?(?![a-zа-яё0-9])"""),
        Regex("""(?iu)(?<![a-zа-яё0-9])(?:serial|sn|s/n|серийн\S*|серия|номер)(?![a-zа-яё0-9])"""),
        Regex("""(?iu)(?<![a-zа-яё0-9])(?:part|p/n|pn|артикул|арт|код|инвентарный)(?![a-zа-яё0-9])"""),
    )

    /** Якоря модели/типа изделия. */
    private val MODEL_ANCHORS = listOf(
        Regex("""(?iu)(?<![a-zа-яё0-9])(?:type|model|модель|тип)(?![a-zа-яё0-9])"""),
    )

    /** Токен: буквы/цифры/дефис/подчёркивание/точка/слэш. */
    private val TOKEN = Regex("""[A-Za-zА-Яа-яЁё0-9][A-Za-zА-Яа-яЁё0-9\-_/\.]{1,31}""")

    /** Буквы + необязательный разделитель + цифры: АБВ-123456, АБВ 123456. */
    private val LETTERS_DIGITS = Regex("""\b([A-Za-zА-Яа-яЁё]{1,6})\s*[\-–]?\s*(\d{3,14})\b""")

    /** Только цифры, не короче 6 знаков. */
    private val DIGITS = Regex("""\b(\d{6,14})\b""")

    /**
     * Возвращает кандидатов серийного номера в порядке убывания приоритета:
     * сначала то, что стоит после якорного слова, затем буквы+цифры, затем чистые цифры.
     */
    fun extract(rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()

        val out = LinkedHashSet<String>()
        val trimmed = rawText.trim()

        collectAnchored(out, trimmed, SERIAL_ANCHORS)

        for (m in LETTERS_DIGITS.findAll(trimmed)) {
            out += normalize(m.groupValues[1] + "-" + m.groupValues[2])
        }

        for (m in DIGITS.findAll(trimmed)) {
            out += normalize(m.groupValues[1])
        }

        return out
            .filter { it.length >= 3 }
            .filter { it.any(Char::isDigit) }
            .take(12)
    }

    /**
     * Возвращает кандидатов модели/типа в порядке убывания приоритета.
     */
    fun extractModels(rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()

        val out = LinkedHashSet<String>()
        collectAnchored(out, rawText.trim(), MODEL_ANCHORS)

        return out
            .filter { it.length >= 2 }
            .take(8)
    }

    private fun collectAnchored(out: LinkedHashSet<String>, trimmed: String, anchors: List<Regex>) {
        for (anchor in anchors) {
            for (match in anchor.findAll(trimmed)) {
                val tailStart = match.range.last + 1
                if (tailStart >= trimmed.length) continue
                val tail = trimmed.substring(tailStart, (tailStart + 40).coerceAtMost(trimmed.length))
                val cleaned = tail.trimStart(' ', '\t', ':', '=', '-', '–', '.', '#', '№', '[', ']', '(', ')')
                TOKEN.find(cleaned)?.let { out += normalize(it.value) }
            }
        }
    }

    /** Приводит значение к каноническому виду: без пробелов, верхний регистр, без хвостовых знаков. */
    fun normalize(value: String): String =
        value.trim()
            .replace(Regex("""\s+"""), "-")
            .trim('-', '_', '.', '/', '[', ']', '(', ')')
            .uppercase()
}
