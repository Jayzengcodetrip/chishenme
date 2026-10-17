package com.mealpick.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable

/**
 * Flat 64×64 spot illustrations for the home, ways and quote screens.
 * Shapes are vector paths so they stay crisp at any density and need no image assets.
 */
internal object Art {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}A$r $r 0 1 0 ${cx + r} ${cy}A$r $r 0 1 0 ${cx - r} ${cy}Z"

    private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} ${y}H${x + w - r}A$r $r 0 0 1 ${x + w} ${y + r}V${y + h - r}A$r $r 0 0 1 ${x + w - r} ${y + h}" +
            "H${x + r}A$r $r 0 0 1 $x ${y + h - r}V${y + r}A$r $r 0 0 1 ${x + r} ${y}Z"

    private class Draw(name: String) {
        val builder = ImageVector.Builder(name, 64.dp, 64.dp, 64f, 64f)
        fun fill(d: String, color: Long, alpha: Float = 1f) {
            builder.addPath(addPathNodes(d), fill = SolidColor(Color(color)), fillAlpha = alpha)
        }
        fun stroke(d: String, color: Long, width: Float, alpha: Float = 1f) {
            builder.addPath(
                addPathNodes(d), stroke = SolidColor(Color(color)), strokeAlpha = alpha, strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        fun rotated(degrees: Float, pivotX: Float, pivotY: Float, block: Draw.() -> Unit) {
            builder.addGroup(rotate = degrees, pivotX = pivotX, pivotY = pivotY)
            block()
            builder.clearGroup()
        }
    }

    private fun build(name: String, block: Draw.() -> Unit): ImageVector = Draw(name).apply(block).builder.build()

    val takeout: ImageVector by lazy {
        build("takeout") {
            stroke("M37.5 6.5 32 25M44.5 8.5 35.5 26", 0xFF6B4628, 2.6f)
            fill("M14 24H50L45.6 53.4A3 3 0 0 1 42.6 56H21.4A3 3 0 0 1 18.4 53.4Z", 0xFFF2BC3F)
            fill(rrect(11f, 19.5f, 42f, 7f, 3.5f), 0xFFD9971C)
            fill(circle(32f, 40.5f, 7.5f), 0xFFFFFFFF)
            fill("M32 44.6C28.5 42.3 27.5 40.9 27.5 39.2A2.3 2.3 0 0 1 32 38.3A2.3 2.3 0 0 1 36.5 39.2C36.5 40.9 35.5 42.3 32 44.6Z", 0xFFD23A2C)
        }
    }

    val dining: ImageVector by lazy {
        build("dining") {
            fill("M50 7.5 51.5 11.5 55.5 13 51.5 14.5 50 18.5 48.5 14.5 44.5 13 48.5 11.5Z", 0xFFF2BC3F)
            fill(circle(32f, 20.5f, 3.8f), 0xFFA32A1F)
            fill("M11 45A21 21 0 0 1 53 45Z", 0xFFD23A2C)
            stroke("M18.5 40A14 14 0 0 1 25.5 30.5", 0xFFFFFFFF, 3f, .7f)
            fill(rrect(6f, 45f, 52f, 6.5f, 3.25f), 0xFFA32A1F)
        }
    }

    val snacks: ImageVector by lazy {
        build("snacks") {
            fill("M48.5 6A9 9 0 1 0 57.5 18A7.5 7.5 0 0 1 48.5 6Z", 0xFFF2BC3F)
            fill("M41 10.5 41.9 12.6 44 13.5 41.9 14.4 41 16.5 40.1 14.4 38 13.5 40.1 12.6Z", 0xFFF2BC3F)
            stroke("M37 5.5 33.5 24", 0xFFD23A2C, 3.4f)
            fill("M18.5 26H45.5L42.3 53.3A3.4 3.4 0 0 1 38.9 56.3H25.1A3.4 3.4 0 0 1 21.7 53.3Z", 0xFFFFFFFF)
            fill("M20.4 38H43.6L42 53.3A3.4 3.4 0 0 1 38.6 56.3H25.4A3.4 3.4 0 0 1 22 53.3Z", 0xFFB68155)
            fill("M16 26.5A16 7 0 0 1 48 26.5Z", 0xFF6B4628)
            fill(rrect(15f, 25f, 34f, 3.5f, 1.75f), 0xFF6B4628)
            listOf(Triple(27f, 50f, 2.3f), Triple(32.5f, 51.8f, 2.3f), Triple(38f, 49.5f, 2.3f), Triple(31f, 46f, 2.3f))
                .forEach { (x, y, r) -> fill(circle(x, y, r), 0xFF4A2F1A) }
        }
    }

    val cooking: ImageVector by lazy {
        build("cooking") {
            stroke("M22 6.5C19.8 9.1 24.2 11.1 22 13.9M30 4.5C27.8 7.1 32.2 9.1 30 11.9", 0xFF2E8A67, 2.4f)
            rotated(-8f, 41f, 36.5f) { fill(rrect(41f, 33f, 20f, 7f, 3.5f), 0xFF0F4A38) }
            fill(circle(26f, 38f, 19f), 0xFF0F4A38)
            fill(circle(26f, 38f, 15f), 0xFF2E8A67)
            fill("M16.5 36.5C15.9 30.1 22.1 26 28.1 27.5C33.8 28.9 36.7 34.2 34.7 39.1C32.8 44 26.8 45.8 22.3 44.4C19.1 43.4 16.8 40.4 16.5 36.5Z", 0xFFFFFFFF)
            fill(circle(26f, 36.5f, 5f), 0xFFF2BC3F)
            fill(circle(24.4f, 34.9f, 1.3f), 0xFFFFFFFF, .85f)
        }
    }

    val breakfast: ImageVector by lazy {
        build("breakfast") {
            fill(rrect(9f, 14f, 34f, 36f, 11f), 0xFFD9971C)
            fill(rrect(13f, 18f, 26f, 28f, 8f), 0xFFFFE7A8)
            fill("M33 30C33 23 40 19 47 21C55 23 58 31 55 38C52 46 43 49 37 45C33 42 33 36 33 30Z", 0xFFFFFFFF)
            fill(circle(45f, 34f, 6f), 0xFFF2BC3F)
            fill(circle(43.4f, 32.4f, 1.6f), 0xFFFFFFFF, .85f)
        }
    }

    val lunch: ImageVector by lazy {
        build("lunch") {
            stroke("M40 6 30 26M48 9 34 27", 0xFF6B4628, 2.6f)
            fill("M13 30A19 10 0 0 1 51 30Z", 0xFFFFFFFF)
            fill(circle(24f, 24f, 2f), 0xFFE6EFEA)
            fill("M9 30H55C55 43 45 54 32 54C19 54 9 43 9 30Z", 0xFF2E8A67)
            fill(rrect(24f, 53f, 16f, 5f, 2.5f), 0xFF0F4A38)
            stroke("M16 38H48", 0xFFFFFFFF, 2.4f, .55f)
        }
    }

    val dinner: ImageVector by lazy {
        build("dinner") {
            stroke("M22 6C19.8 8.6 24.2 10.6 22 13.4M32 4C29.8 6.6 34.2 8.6 32 11.4M42 6C39.8 8.6 44.2 10.6 42 13.4", 0xFFD23A2C, 2.4f)
            stroke("M14 24C18 21 22 27 26 24S34 21 38 24 46 27 50 24", 0xFFF2BC3F, 3f)
            fill("M8 28H56C56 42 45 54 32 54C19 54 8 42 8 28Z", 0xFFD23A2C)
            fill(rrect(23f, 53f, 18f, 5f, 2.5f), 0xFFA32A1F)
            fill(circle(22f, 38f, 3f), 0xFFFFFFFF, .8f)
        }
    }

    val afternoonTea: ImageVector by lazy {
        build("afternoonTea") {
            stroke("M24 7C21.8 9.6 26.2 11.6 24 14.4M32 5C29.8 7.6 34.2 9.6 32 12.4", 0xFFB68155, 2.4f)
            stroke("M46 26H50A7 7 0 0 1 50 40H45", 0xFF6B4628, 4f)
            fill("M12 20H48V36A14 14 0 0 1 34 50H26A14 14 0 0 1 12 36Z", 0xFFFFFFFF)
            fill("M13.2 28H46.8V36A12.8 12.8 0 0 1 34 48.8H26A12.8 12.8 0 0 1 13.2 36Z", 0xFFB68155)
            fill(rrect(6f, 51f, 48f, 6f, 3f), 0xFF6B4628)
        }
    }

    val lateNight: ImageVector by lazy {
        build("lateNight") {
            fill("M36 8A20 20 0 1 0 56 34A16 16 0 0 1 36 8Z", 0xFFF2BC3F)
            fill("M14 10 15.3 13.7 19 15 15.3 16.3 14 20 12.7 16.3 9 15 12.7 13.7Z", 0xFFFDF0CF)
            fill(circle(22f, 48f, 2f), 0xFFFDF0CF)
            fill(circle(52f, 12f, 1.6f), 0xFFFDF0CF)
        }
    }

    val canteen: ImageVector by lazy {
        build("canteen") {
            fill(rrect(6f, 18f, 52f, 34f, 7f), 0xFF17614A)
            fill(rrect(11f, 23f, 18f, 11f, 3f), 0xFFFFFFFF)
            fill(rrect(33f, 23f, 20f, 11f, 3f), 0xFFF2BC3F)
            fill(rrect(11f, 38f, 12f, 9f, 3f), 0xFFD23A2C)
            fill(rrect(27f, 38f, 26f, 9f, 3f), 0xFFCFE7DB)
            fill(circle(18f, 28.5f, 1.6f), 0xFFE3ECE7)
            fill(circle(22f, 27.5f, 1.6f), 0xFFE3ECE7)
            stroke("M44 6 40 15M50 7 44 16", 0xFF6B4628, 2.4f)
        }
    }

    val family: ImageVector by lazy {
        build("family") {
            stroke("M8 30 32 10 56 30", 0xFFD23A2C, 5f)
            fill("M14 28 32 13.5 50 28V54H14Z", 0xFFFFFFFF)
            fill(rrect(42f, 12f, 6f, 11f, 1.5f), 0xFF6B4628)
            fill("M32 46C25.5 41.6 23.5 38.6 23.5 35.4A4.3 4.3 0 0 1 32 33.6A4.3 4.3 0 0 1 40.5 35.4C40.5 38.6 38.5 41.6 32 46Z", 0xFFD23A2C)
            fill(rrect(10f, 53f, 44f, 4f, 2f), 0xFFB68155)
        }
    }
}

/** Mint tiles with white grout, the backdrop of the shop-sign header and the card backs. */
@Composable
internal fun TileWall(modifier: Modifier = Modifier, tile: Dp = 26.dp, grout: Dp = 2.dp) {
    Canvas(modifier) {
        drawRect(Mp.Tile)
        val step = tile.toPx()
        val width = grout.toPx()
        var x = -1f
        while (x < size.width) {
            drawLine(Color.White, Offset(x + width / 2, 0f), Offset(x + width / 2, size.height), width)
            x += step
        }
        var y = -1f
        while (y < size.height) {
            drawLine(Color.White, Offset(0f, y + width / 2), Offset(size.width, y + width / 2), width)
            y += step
        }
    }
}
