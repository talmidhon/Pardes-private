package io.github.kdroidfilter.seforimapp.features.settings.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.settings_home_background
import seforimapp.seforimapp.generated.resources.settings_home_background_acrylic
import seforimapp.seforimapp.generated.resources.settings_home_background_acrylic_blur
import seforimapp.seforimapp.generated.resources.settings_home_background_acrylic_opacity
import seforimapp.seforimapp.generated.resources.settings_home_background_description
import seforimapp.seforimapp.generated.resources.settings_home_background_image

// Pardes (the daily portion's upgrade, part B): "רקע אקרילי ללא תמונה", beside the wallpaper's own card.

/** Settings › תצוגה: the Home's background, the wallpaper image or an acrylic with no image, its opacity and its blur. */
@Composable
internal fun HomeBackgroundCard() {
    val appSettings = LocalAppGraph.current.appSettings
    val acrylic by appSettings.flow(HomeBackgroundSettings.acrylic).collectAsState()
    val opacity by appSettings.flow(HomeBackgroundSettings.acrylicOpacity).collectAsState()
    val blur by appSettings.flow(HomeBackgroundSettings.acrylicBlur).collectAsState()
    val shape = RoundedCornerShape(8.dp)
    val styles =
        listOf(
            stringResource(Res.string.settings_home_background_image),
            stringResource(Res.string.settings_home_background_acrylic),
        )
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, JewelTheme.globalColors.borders.normal, shape)
                .background(JewelTheme.globalColors.panelBackground)
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChoiceRow(
            title = stringResource(Res.string.settings_home_background),
            items = styles,
            selectedIndex = if (acrylic) 1 else 0,
            tag = "settings-home-background",
        ) { appSettings[HomeBackgroundSettings.acrylic] = it == 1 }
        Text(
            text = stringResource(Res.string.settings_home_background_description),
            fontSize = 12.sp,
            color = JewelTheme.globalColors.text.info,
        )
        if (acrylic) {
            ChoiceRow(
                title = stringResource(Res.string.settings_home_background_acrylic_opacity),
                items = ACRYLIC_OPACITIES.map { "${(it * 100).toInt()}%" },
                selectedIndex = nearestIndex(ACRYLIC_OPACITIES, readableOpacity(opacity)),
                tag = "settings-acrylic-opacity",
            ) { appSettings[HomeBackgroundSettings.acrylicOpacity] = ACRYLIC_OPACITIES[it] }
            ChoiceRow(
                title = stringResource(Res.string.settings_home_background_acrylic_blur),
                items = ACRYLIC_BLURS.map { if (it == 0f) "0" else "${it.toInt()}" },
                selectedIndex = nearestIndex(ACRYLIC_BLURS, usableBlur(blur)),
                tag = "settings-acrylic-blur",
            ) { appSettings[HomeBackgroundSettings.acrylicBlur] = ACRYLIC_BLURS[it] }
        }
    }
}

// A line of the card: its title, and a combo box to choose from
@Composable
private fun ChoiceRow(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    tag: String,
    onChoose: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, modifier = Modifier.weight(1f))
        ListComboBox(
            items = items,
            selectedIndex = selectedIndex,
            onSelectedItemChange = onChoose,
            modifier = Modifier.width(140.dp).testTag(tag),
        )
    }
}
