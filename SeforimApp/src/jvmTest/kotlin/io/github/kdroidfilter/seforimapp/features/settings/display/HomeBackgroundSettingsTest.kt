package io.github.kdroidfilter.seforimapp.features.settings.display

import com.russhwolf.settings.PropertiesSettings
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Home's background as the wallpaper or an acrylic: its settings, kept across a restart, and what is made of a value. */
class HomeBackgroundSettingsTest {
    private val store = PropertiesSettings(Properties())

    @Test
    fun `the wallpaper is the background until the acrylic is chosen, and no image is its default`() {
        val settings = AppSettings(store)
        assertFalse(settings[HomeBackgroundSettings.acrylic])
        // The wallpaper's own switch is untouched by it
        assertTrue(settings.isShowHomeWallpaperEnabled())
        assertEquals(DEFAULT_ACRYLIC_OPACITY, settings[HomeBackgroundSettings.acrylicOpacity])
        assertEquals(DEFAULT_ACRYLIC_BLUR, settings[HomeBackgroundSettings.acrylicBlur])
    }

    @Test
    fun `the acrylic, its opacity and its blur are kept after a restart, and the wallpaper's switch with them`() {
        val settings = AppSettings(store)
        settings.setShowHomeWallpaperEnabled(false)
        settings[HomeBackgroundSettings.acrylic] = true
        settings[HomeBackgroundSettings.acrylicOpacity] = 0.6f
        settings[HomeBackgroundSettings.acrylicBlur] = 16f
        val reread = AppSettings(store)
        assertTrue(reread[HomeBackgroundSettings.acrylic])
        assertEquals(0.6f, reread[HomeBackgroundSettings.acrylicOpacity])
        assertEquals(16f, reread[HomeBackgroundSettings.acrylicBlur])
        assertFalse(reread.isShowHomeWallpaperEnabled())
        // Back to the image: the acrylic's choices stay for the next time
        reread[HomeBackgroundSettings.acrylic] = false
        assertFalse(AppSettings(store)[HomeBackgroundSettings.acrylic])
        assertEquals(0.6f, AppSettings(store)[HomeBackgroundSettings.acrylicOpacity])
    }

    @Test
    fun `the setting is observed, and the background is portable with the other preferences`() {
        val settings = AppSettings(store)
        val flow = settings.flow(HomeBackgroundSettings.acrylic)
        settings[HomeBackgroundSettings.acrylic] = true
        assertTrue(flow.value)
        val exported = AppSettings.exportPortablePreferences(store)
        listOf(HomeBackgroundSettings.acrylic, HomeBackgroundSettings.acrylicOpacity, HomeBackgroundSettings.acrylicBlur)
            .forEach { assertTrue(it.key in exported, it.key) }
    }

    @Test
    fun `resetting the group brings the wallpaper back`() {
        val settings = AppSettings(store)
        settings[HomeBackgroundSettings.acrylic] = true
        settings.reset(HomeBackgroundSettings)
        assertFalse(settings[HomeBackgroundSettings.acrylic])
        assertFalse(store.hasKey("home_background.acrylic"))
    }

    @Test
    fun `an opacity or a blur that cannot be used is brought to one that can, so the text stays readable`() {
        assertEquals(MIN_ACRYLIC_OPACITY, readableOpacity(0f))
        assertEquals(1f, readableOpacity(7f))
        assertEquals(0.6f, readableOpacity(0.6f))
        assertEquals(DEFAULT_ACRYLIC_OPACITY, readableOpacity(Float.NaN))
        assertEquals(0f, usableBlur(-3f))
        assertEquals(MAX_ACRYLIC_BLUR, usableBlur(1000f))
        assertEquals(DEFAULT_ACRYLIC_BLUR, usableBlur(Float.POSITIVE_INFINITY))
        assertTrue(ACRYLIC_OPACITIES.all { it >= MIN_ACRYLIC_OPACITY && it <= 1f })
        assertTrue(ACRYLIC_BLURS.all { it in 0f..MAX_ACRYLIC_BLUR })
    }

    @Test
    fun `a value the combo box does not offer shows as the nearest one`() {
        assertEquals(2, nearestIndex(ACRYLIC_OPACITIES, 0.7f))
        assertEquals(0, nearestIndex(ACRYLIC_BLURS, 3f))
        assertEquals(3, nearestIndex(ACRYLIC_BLURS, 80f))
        assertEquals(0, nearestIndex(emptyList(), 1f))
    }
}
