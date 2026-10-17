package com.mealpick.app.ui

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Check the text against the space it actually occupies, rather than the wider
 * parent constraint returned by Compose's String Text semantics adapter.
 *
 * Verified against this project's foundation runtime bytecode:
 * ParagraphLayoutCache.slowCreateTextLayoutResultOrNull rebuilds MultiParagraph
 * with prevConstraints, then pairs it with the real, potentially narrower
 * layoutSize. TextLayoutResult.didOverflowWidth compares those two widths, so
 * short, fully visible labels can report overflow. Re-measuring in layoutSize
 * avoids that adapter mismatch without ignoring actual clipping or lost text.
 */
internal fun assertFullTextFits(
    label: String,
    reported: TextLayoutResult,
    actualSize: IntSize = reported.size,
) {
    val input = reported.layoutInput
    val original = "reportedSize=${reported.size}, actualSize=$actualSize, " +
        "paragraph=${reported.multiParagraph.width}x${reported.multiParagraph.height}, " +
        "constraints=${input.constraints}, text=${input.text.text}"
    assertTrue("$label has no drawable text area: $original", actualSize.width > 0 && actualSize.height > 0)
    val measured = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection).measure(
        text = input.text,
        style = input.style,
        overflow = input.overflow,
        softWrap = input.softWrap,
        maxLines = input.maxLines,
        placeholders = input.placeholders,
        constraints = Constraints(maxWidth = actualSize.width, maxHeight = actualSize.height),
    )
    val lines = (0 until measured.lineCount).joinToString { line ->
        "$line:[${measured.getLineLeft(line)},${measured.getLineTop(line)}.." +
            "${measured.getLineRight(line)},${measured.getLineBottom(line)}] end=${measured.getLineEnd(line)}"
    }
    val details = "$label: $original; measuredSize=${measured.size}, " +
        "paragraph=${measured.multiParagraph.width}x${measured.multiParagraph.height}; lines=$lines"
    assertFalse("Text overflows its actual rendered size. $details", measured.hasVisualOverflow)
    assertFalse("Text exceeded its allowed line count. $details", measured.multiParagraph.didExceedMaxLines)
    assertTrue("Nonempty text must have a line. $details", measured.lineCount > 0)
    assertEquals("The final character must be laid out. $details", input.text.length,
        measured.getLineEnd(measured.lineCount - 1))
    for (line in 0 until measured.lineCount) {
        assertFalse("Line $line must not be ellipsized. $details", measured.isLineEllipsized(line))
        assertTrue("Line $line must fit horizontally. $details",
            measured.getLineLeft(line) >= -0.5f && measured.getLineRight(line) <= actualSize.width + 0.5f)
        assertTrue("Line $line must fit vertically. $details",
            measured.getLineTop(line) >= -0.5f && measured.getLineBottom(line) <= actualSize.height + 0.5f)
    }
    // Text coordinates are fractional pixels; allow only subpixel rounding, not a
    // missing line or clipped character. Each character box must fit the node.
    for (offset in input.text.text.indices) {
        val box = measured.getBoundingBox(offset)
        assertTrue("Character $offset is outside the rendered text bounds: $box. $details",
            box.left >= -0.5f && box.right <= actualSize.width + 0.5f &&
                box.top >= -0.5f && box.bottom <= actualSize.height + 0.5f)
    }
}
