package ru.delivery.photo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверка разбора серийных номеров из распознанного текста этикетки.
 *
 * Ориентир — шильдики дизель-генераторов (EAC):
 *
 * ```
 * DIESEL GENERATING SETS EAC
 * TYPE [DCS-265KVA]  NO. [26100347]
 * P [212] kW [265] kVA [400] V [382.5] A
 * ...
 * ```
 *
 * Логика намеренно консервативна: лучше не предложить кандидата, чем предложить
 * мусор — результат всё равно подтверждает человек.
 */
class SerialPatternsTest {

    @Test
    fun `номер после якоря извлекается`() {
        val text = "Серийный номер: АБВ-123456\nПроизводитель: ООО Ромашка"
        val result = SerialPatterns.extract(text)
        assertTrue("Ожидался кандидат АБВ-123456, получено $result", result.contains("АБВ-123456"))
    }

    @Test
    fun `латинский якольный номер извлекается`() {
        val result = SerialPatterns.extract("SN: XJ-99-4412")
        assertTrue(result.contains("XJ-99-4412"))
    }

    @Test
    fun `буквы и цифры без якоря распознаются`() {
        val result = SerialPatterns.extract("Партия 4711 Изделие АБВ 123456 Год 2024")
        assertTrue("Ожидался АБВ-123456, получено $result", result.contains("АБВ-123456"))
    }

    @Test
    fun `только цифры распознаются`() {
        val result = SerialPatterns.extract("Код 987654321")
        assertTrue("Ожидался 987654321, получено $result", result.contains("987654321"))
    }

    @Test
    fun `приоритет якоря выше общего разбора`() {
        val text = "Изделие АБВ 111111\nСерийный номер: ZZ-999999"
        val result = SerialPatterns.extract(text)
        assertEquals("ZZ-999999", result.first())
    }

    @Test
    fun `поле NO шильдика даёт серийный номер`() {
        val text = "DIESEL GENERATING SETS EAC\nTYPE DCS-265KVA NO. 26100347\n" +
            "P 212 kW 265 kVA 400 V 382.5 A\nDATE 2026.10\nSTANDARD GB/T2820.5-2009"
        val result = SerialPatterns.extract(text)
        assertEquals("Ожидался 26100347 первым кандидатом, получено $result", "26100347", result.first())
    }

    @Test
    fun `поле NO с другими числами на шильдике`() {
        val text = "TYPE GFS-20KVA NO. 26100337\nP 16 kW 20 kVA 400 V 28.9 A\n" +
            "IP 23 670 kg DIMENSIONS(LxWxH) 2000*740*1210 mm"
        val result = SerialPatterns.extract(text)
        assertEquals("Ожидался 26100337 первым кандидатом, получено $result", "26100337", result.first())
    }

    @Test
    fun `якорь no не цепляется к середине слова`() {
        val result = SerialPatterns.extract("KNOB SET 12345678")
        assertTrue(
            "Якорь не должен срабатывать внутри слова, получено $result",
            result.none { it.startsWith("KNOB") },
        )
    }

    @Test
    fun `пустой текст не даёт кандидатов`() {
        assertTrue(SerialPatterns.extract("").isEmpty())
        assertTrue(SerialPatterns.extract("   \n  ").isEmpty())
    }

    @Test
    fun `короткие строки без цифр отбрасываются`() {
        val result = SerialPatterns.extract("Made in Russia")
        assertTrue("Не должно быть кандидатов без цифр, получено $result", result.isEmpty())
    }

    @Test
    fun `нормализация убирает пробелы и приводит к верхнему регистру`() {
        assertEquals("АБВ-123", SerialPatterns.normalize("  абв 123 "))
        assertEquals("XJ-99", SerialPatterns.normalize("xj-99"))
    }

    @Test
    fun `ведущие нули сохраняются`() {
        val result = SerialPatterns.extract("SN: 001234")
        assertTrue("Ожидался 001234, получено $result", result.contains("001234"))
    }
}
