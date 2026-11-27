package io.github.kdroidfilter.seforimapp.features.settings.display

import io.github.kdroidfilter.seforimapp.core.settings.SettingsGroup

// Pardes (the daily portion's upgrade, part B): the Home's background as the wallpaper or as an acrylic, the settings of
// HomeBackgroundCard.kt's card; the acrylic itself is drawn by HomeAcrylic.kt.

/**
 * What the Home's background is: the wallpaper image (what it was, shown or hidden by its own setting) or, with [acrylic],
 * no image at all, a frosted translucent surface of [acrylicOpacity] and [acrylicBlur]. One setting, every window.
 */
object HomeBackgroundSettings : SettingsGroup("home_background") {
    val acrylic by boolean(false)
    val acrylicOpacity by float(DEFAULT_ACRYLIC_OPACITY)
    val acrylicBlur by float(DEFAULT_ACRYLIC_BLUR)
}

/** How opaque the acrylic's surface is, to choose from: never so clear that the text over it is hard to read. */
internal val ACRYLIC_OPACITIES = listOf(0.45f, 0.6f, 0.75f, 0.9f)

/** How blurred the acrylic's light is, in dp, to choose from; 0 is none. */
internal val ACRYLIC_BLURS = listOf(0f, 16f, 32f, 56f)

internal const val DEFAULT_ACRYLIC_OPACITY = 0.75f
internal const val DEFAULT_ACRYLIC_BLUR = 32f
internal const val MIN_ACRYLIC_OPACITY = 0.3f
internal const val MAX_ACRYLIC_BLUR = 80f

/** [value] kept where the text stays readable (a value hand-edited, or from a later version, is brought back to it). */
internal fun readableOpacity(value: Float): Float =
    value.takeIf { it.isFinite() }?.coerceIn(MIN_ACRYLIC_OPACITY, 1f) ?: DEFAULT_ACRYLIC_OPACITY

/** [value] kept to what the blur can be. */
internal fun usableBlur(value: Float): Float = value.takeIf { it.isFinite() }?.coerceIn(0f, MAX_ACRYLIC_BLUR) ?: DEFAULT_ACRYLIC_BLUR

/** The index in [choices] nearest to [value]: what a combo box shows when the setting holds a value it does not offer. */
internal fun nearestIndex(
    choices: List<Float>,
    value: Float,
): Int = choices.indices.minByOrNull { kotlin.math.abs(choices[it] - value) } ?: 0
