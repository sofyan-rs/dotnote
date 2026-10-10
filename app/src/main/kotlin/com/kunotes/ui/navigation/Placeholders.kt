/**
 * Temporary screens for tabs that are built in later phases (see PLAN.md).
 */
package com.kunotes.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kunotes.ui.dotline.DotlineIcons
import com.kunotes.ui.dotline.MainTab
import com.kunotes.ui.dotline.ScreenTitle
import com.kunotes.ui.dotline.ScreenTitleAction
import com.kunotes.ui.theme.Dotline

@Composable
fun PlaceholderTab(
    tab: MainTab,
    onTabClick: (MainTab) -> Unit,
) {
    MainTabs(selected = tab, onTabClick = onTabClick) {
        Column {
            ScreenTitle(title = tab.label)
            Text("Coming soon.", style = Dotline.type.body, color = Dotline.colors.inkMuted, modifier = Modifier.padding(Dotline.space.s4))
        }
    }
}

@Composable
fun PlaceholderEditor(
    path: String,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Dotline.colors.bg).safeDrawingPadding()) {
        ScreenTitle(title = "EDITOR", actions = { ScreenTitleAction(DotlineIcons.Back, "Back", onBack) })
        Text(path, style = Dotline.type.data, color = Dotline.colors.inkMuted, modifier = Modifier.padding(Dotline.space.s4))
    }
}
