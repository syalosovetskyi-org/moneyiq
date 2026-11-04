package org.syalosovetskyi.onemoney.util

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt

/**
 * Розбирає hex-рядок кольору (напр. "#4CAF50") у [Color], повертаючи [fallback]
 * при null/порожньому/некоректному значенні. Раніше ця ідіома
 * (try { Color(hex.toColorInt()) } catch { fallback }) копіювалася у 20+ місцях.
 */
fun parseColorHex(hex: String?, fallback: Color): Color =
    if (hex.isNullOrBlank()) fallback
    else runCatching { Color(hex.toColorInt()) }.onFailure { org.syalosovetskyi.onemoney.CrashReporter.warning("ColorHex", "Invalid stored color; using fallback", it) }.getOrDefault(fallback)
