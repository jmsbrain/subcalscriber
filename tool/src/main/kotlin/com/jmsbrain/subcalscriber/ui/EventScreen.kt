package com.jmsbrain.subcalscriber.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jmsbrain.subcalscriber.data.AgendaItem
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import java.time.ZoneId

class EventScreen(sealedActivity: SealedLightActivity, private val item: AgendaItem) :
    SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val e = item.event
        SubcalPage(title = "", left = backButton { goBack() }) {
            LightText(e.title, LightTextVariant.Heading)
            Section("WHEN", whenText(e, ZoneId.systemDefault()))
            Section("CALENDAR", "${item.feed.tag} · ${item.feed.name}")
            if (e.location.isNotBlank()) Section("WHERE", e.location)
            if (e.description.isNotBlank()) Section("NOTES", e.description)
        }
    }

    @Composable
    private fun Section(label: String, value: String) {
        LightText(label, LightTextVariant.Superfine, lighten = true, modifier = Modifier.padding(top = 1f.gridUnitsAsDp()))
        LightText(value, LightTextVariant.Paragraph)
    }
}
