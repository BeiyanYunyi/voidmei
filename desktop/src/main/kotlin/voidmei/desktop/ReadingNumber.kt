package voidmei.desktop

import java.util.Locale

/** Normalize rounded negative zero only in presentation, preserving source precision. */
internal fun readingNumber(value: Double?, digits: Int = 1): String {
    if (value == null || !value.isFinite()) return "—"
    val formatted = String.format(Locale.ROOT, "%.${digits}f", value)
    return if (formatted.startsWith('-') && formatted.toDoubleOrNull() == 0.0) formatted.drop(1) else formatted
}
