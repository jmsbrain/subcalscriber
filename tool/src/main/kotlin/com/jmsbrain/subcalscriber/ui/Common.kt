package com.jmsbrain.subcalscriber.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp

/** Theme + background shared by every screen. */
@Composable
fun SubcalTheme(content: @Composable () -> Unit) {
    val colors by LightThemeController.colors.collectAsState()
    LightTheme(colors = colors) {
        Box(Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) { content() }
    }
}

/** A screen with a top bar, scrolling content and an optional bottom bar. */
@Composable
fun SubcalPage(
    title: String,
    left: LightBarButton? = null,
    right: LightBarButton? = null,
    bottom: List<LightBarButton> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    SubcalTheme {
        Column(Modifier.fillMaxSize()) {
            LightTopBar(
                leftButton = left,
                center = LightTopBarCenter.Text(title),
                rightButton = right,
                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
            )
            LightScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
            ) { content() }
            if (bottom.isNotEmpty()) LightBottomBar(bottom)
        }
    }
}

fun backButton(onClick: () -> Unit) = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onClick)
