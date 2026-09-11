package voidmei.desktop

import kotlin.test.*

class ReadingNumberTest {
    @Test fun removesSignOnlyWhenDisplayedNumberIsZero() {
        assertEquals("0", readingNumber(-0.49, 0))
        assertEquals("0.0", readingNumber(-0.049, 1))
        assertEquals("0.00", readingNumber(-0.0049, 2))
        assertEquals("-1", readingNumber(-0.51, 0))
        assertEquals("-0.1", readingNumber(-0.051, 1))
        assertEquals("-0.01", readingNumber(-0.0051, 2))
        assertEquals("0.00", readingNumber(-0.0, 2))
        assertEquals("0.00", readingNumber(0.0, 2))
        for (value in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals("—", readingNumber(value))
        }
    }
}
