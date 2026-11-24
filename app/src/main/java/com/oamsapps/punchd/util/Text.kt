package com.oamsapps.punchd.util

import java.util.Locale

/**
 * Sentence case for user-entered labels: "test job" -> "Test job". Only the first letter changes,
 * so names like "McDonald's", "IKEA" or "café 24" keep the user's own capitals elsewhere.
 */
fun String.sentenceCase(locale: Locale = Locale.getDefault()): String =
    trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
