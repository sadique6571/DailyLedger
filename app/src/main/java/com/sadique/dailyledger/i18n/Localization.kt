package com.sadique.dailyledger.i18n

import android.content.Context
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.isSpecified
import com.sadique.dailyledger.R
import org.json.JSONObject

/** Presentation-only translation. Stored category keys, IDs, dates and user records are never rewritten. */
class LedgerStrings(val urdu: Boolean = false, private val labels: Map<String, String> = emptyMap()) {
    private val placeholder = Regex("\\{(\\d+)\\}")
    private val templates by lazy {
        labels.filterKeys { placeholder.containsMatchIn(it) }.entries.sortedByDescending { it.key.length }.map { (source, translated) ->
            val parts = placeholder.split(source)
            val slots = placeholder.findAll(source).map { it.groupValues[1] }.toList()
            Triple(Regex(parts.joinToString("(.*?)") { Regex.escape(it) }, RegexOption.DOT_MATCHES_ALL), translated, slots)
        }
    }
    private val phrases by lazy {
        labels.keys.filter { it.length >= 3 && !placeholder.containsMatchIn(it) }.sortedByDescending { it.length }.joinToString("|", prefix = "(?<![\\p{L}])(?:", postfix = ")(?![\\p{L}])") { Regex.escape(it) }.toRegex()
    }
    fun text(value: String): String {
        if (!urdu || value.isBlank()) return value
        labels[value]?.let { return it }
        templates.forEach { (pattern, translated, slots) ->
            pattern.matchEntire(value)?.let { match ->
                val values = slots.zip(match.groupValues.drop(1)).toMap()
                return placeholder.replace(translated) { values[it.groupValues[1]].orEmpty() }
            }
        }
        // Dynamic labels (amount/date/count) retain their literal values and localized known fragments.
        if (labels.isEmpty()) return value
        return phrases.replace(value) { labels[it.value] ?: it.value }
    }
    companion object {
        fun load(context: Context): Map<String, String> {
            val json = JSONObject(context.assets.open("urdu-ui.json").bufferedReader().use { it.readText() })
            return json.keys().asSequence().associateWith { json.getString(it) }
        }
    }
}
val LocalStrings = staticCompositionLocalOf { LedgerStrings() }
/** Compact, bidi-safe percent text; keep financial ratios unchanged. */
fun progressPercent(percent: Int, urdu: Boolean): String {
    if (!urdu) return "$percent%"
    val urduDigits = "۰۱۲۳۴۵۶۷۸۹"
    val amount = percent.toString().map { digit ->
        if (digit in '0'..'9') urduDigits[digit - '0'] else digit
    }.joinToString("")
    return "$amount٪"
}
val Nastaleeq = FontFamily(Font(R.font.noto_nastaliq_urdu))

@Composable
fun LocalizedText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
    translate: Boolean = true,
) {
    val strings = LocalStrings.current
    val value = if (translate) strings.text(text) else text
    val arabic = value.any { it in '\u0600'..'\u08ff' }
    val nastaleeq = strings.urdu && arabic
    val baseSize = if (fontSize.isSpecified) fontSize else if (style.fontSize.isSpecified) style.fontSize else 14.sp
    val size = if (nastaleeq) maxOf(14f, baseSize.value).sp else fontSize
    androidx.compose.material3.Text(
        text = value, modifier = modifier, color = color,
        fontSize = size, fontStyle = fontStyle, fontWeight = fontWeight,
        fontFamily = if (nastaleeq) Nastaleeq else if (strings.urdu && !arabic) FontFamily.SansSerif else fontFamily,
        letterSpacing = if (nastaleeq) 0.sp else letterSpacing,
        textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = if (nastaleeq) (size.value * 2f).sp else lineHeight,
        overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        onTextLayout = onTextLayout, style = style,
    )
}
