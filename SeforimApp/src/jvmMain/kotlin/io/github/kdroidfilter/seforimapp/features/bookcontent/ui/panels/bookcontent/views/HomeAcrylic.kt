package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.home.widgets.rememberAccentColor
import org.jetbrains.jewel.foundation.theme.JewelTheme
import java.util.Random

// Pardes (the daily portion's upgrade, part B): the Home's background without any image. The window has nothing of its own
// behind it to blur, so the acrylic is made of what an acrylic is: soft light, blurred; a translucent surface over it; and
// a fine grain on top. The surface is the panel's own colour, so the text over it keeps the contrast the panel gives it.

/**
 * The acrylic background: the accent's light in two soft spots, blurred by [blur] dp (none at 0), under the panel's colour
 * at [opacity] (0.3 to 1: the lower, the more light shows through), with a faint grain. No image is drawn, hidden or loaded.
 */
@Composable
internal fun AcrylicBackground(
    opacity: Float,
    blur: Float,
    modifier: Modifier = Modifier,
) {
    val panel = JewelTheme.globalColors.panelBackground
    val accent = rememberAccentColor(JewelTheme.isDark)
    val grain = remember { ShaderBrush(ImageShader(grainTile(), TileMode.Repeated, TileMode.Repeated)) }
    Box(modifier.fillMaxSize().testTag("home-acrylic")) {
        Box(
            Modifier.fillMaxSize().blur(blur.dp).drawBehind {
                val first = Offset(size.width * 0.18f, size.height * 0.22f)
                val second = Offset(size.width * 0.86f, size.height * 0.78f)
                drawCircle(accent.copy(alpha = 0.35f), radius = size.minDimension * 0.45f, center = first)
                drawCircle(accent.copy(alpha = 0.22f), radius = size.minDimension * 0.55f, center = second)
            },
        )
        Box(Modifier.fillMaxSize().background(panel.copy(alpha = opacity)))
        Box(Modifier.fillMaxSize().drawBehind { drawRect(grain, alpha = GRAIN_ALPHA) })
    }
}

// A small tile of light and dark specks, always the same, tiled over the surface
private fun grainTile(): ImageBitmap {
    val bitmap = ImageBitmap(GRAIN_SIZE, GRAIN_SIZE)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    val random = Random(GRAIN_SEED)
    repeat(GRAIN_SIZE * GRAIN_SIZE / 2) {
        paint.color = if (random.nextBoolean()) Color.White else Color.Black
        val x = random.nextInt(GRAIN_SIZE).toFloat()
        val y = random.nextInt(GRAIN_SIZE).toFloat()
        canvas.drawRect(x, y, x + 1f, y + 1f, paint)
    }
    return bitmap
}

private const val GRAIN_SIZE = 96
private const val GRAIN_SEED = 7L
private const val GRAIN_ALPHA = 0.05f
