package ru.delivery.photo.domain

/**
 * Извлечение кандидатов серийного номера из распознанного текста этикетки.
 *
 * Важно: результат этого разбора — только предложение пользователю.
 * Подстановка серийника в водяной знак выполняется исключительно после
 * ручного подтверждения: ошибка OCR в штампе выглядит как достоверный номер.
 *
 * Ориентир — шильдики дизель-генераторов (EAC, «DIESEL GENERATING SETS»),
 * где серийный номер стоит в поле «NO.» и состоит из 8 цифр:
 *
 * ```
 * TYPE [DCS-265KVA]  NO. [26100347]
 * ```
 */
object SerialPatterns {

    /**
     * Якоря в порядке убывания приоритета. Совпадение требует, чтобы якорь
     * был отдельным словом: иначе «no» цепляется к «knob» или «nominal».
     */
    private val ANCHORS = listOf(
        // Поле «NO.» — серийный номер изделия на шильдике.
        Regex("""(?iu)(?<![a-zа-яё0-9])no\.?(?![a-zа-яё0-9])"""),
        Regex("""(?iu)(?<![a-zа-яё0-9])(?:serial|sn|s/n|серийн\S*|серия|номер)(?![a-zа-яё0-9])"""),
        Regex("""(?iu)(?<![a-zа-яё0-9])(?:part|p/n|pn|артикул|арт|код|model|модель|инвентарный)(?![a-zа-яё0-9])"""),
    )

    /** Токен: буквы/цифры/дефис/подчёркивание/точка/слэш. */
    private val TOKEN = Regex("""[A-Za-zА-Яа-яЁё0-9][A-Za-zА-Яа-яЁё0-9\-_/\.]{2,31}""")

    /** Буквы + необязательный разделитель + цифры: АБВ-123456, АБВ 123456. */
    private val LETTERS_DIGITS = Regex("""\b([A-Za-zА-Яа-яЁё]{1,6})\s*[\-–]?\s*(\d{3,14})\b""")

    /** Только цифры, не короче 6 знаков. */
    private val DIGITS = Regex("""\b(\d{6,14})\b""")

    /**
     * Возвращает кандидатов в порядке убывания приоритета:
     * сначала то, что стоит после якорного слова, затем буквы+цифры, затем чистые цифры.
     */
    fun extract(rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()

        val out = LinkedHashSet<String>()
        val trimmed = rawText.trim()

        for (anchor in ANCHORS) {
            for (match in anchor.findAll(trimmed)) {
                val tailStart = match.range.last + 1
                if (tailStart >= trimmed.length) continue
                val tail = trimmed.substring(tailStart, (tailStart + 40).coerceAtMost(trimmed.length))
                val cleaned = tail.trimStart(' ', '\t', ':', '=', '-', '–', '.', '#', '№')
                TOKEN.find(cleaned)?.let { out += normalize(it.value) }
            }
        }

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

    /** Приводит значение к каноническому виду: без пробелов, верхний регистр, без хвостовых знаков. */
    fun normalize(value: String): String =
        value.trim()
            .replace(Regex("""\s+"""), "-")
            .trim('-', '_', '.', '/')
            .uppercase()
}
