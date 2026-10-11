package com.jmsbrain.subcalscriber.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.jmsbrain.subcalscriber.data.Calendar
import com.jmsbrain.subcalscriber.data.Feed
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class FeedsScreen(sealedActivity: SealedLightActivity) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val feeds by Calendar.feeds.collectAsState()
        SubcalPage(
            title = "Feeds",
            left = backButton { goBack() },
            bottom = listOf(LightBarButton.Text(text = "ADD FEED", onClick = { navigateTo({ FeedEditScreen(it, null) }) })),
        ) {
            if (feeds.isEmpty()) LightText("No feeds yet.", LightTextVariant.Copy, lighten = true)
            feeds.forEach { FeedRow(it) }
            LightText(
                "SubcalScriber only connects to the addresses above. Nothing else leaves the phone.",
                LightTextVariant.Fine,
                lighten = true,
                modifier = Modifier.padding(top = 2f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp()),
            )
        }
    }

    @Composable
    private fun FeedRow(feed: Feed) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .lightClickable { navigateTo({ FeedEditScreen(it, feed.id) }) }
                .padding(vertical = 0.5f.gridUnitsAsDp()),
        ) {
            LightText(feed.tag, LightTextVariant.Copy, modifier = Modifier.width(2f.gridUnitsAsDp()))
            Column(Modifier.weight(1f)) {
                LightText(feed.name, LightTextVariant.Copy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LightText(status(feed), LightTextVariant.Superfine, lighten = true)
            }
        }
    }
}

private val updatedFmt = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)

fun status(feed: Feed): String = when {
    feed.error != null -> "Couldn't update: ${feed.error}"
    feed.updatedAt == null -> "Not downloaded yet"
    else -> "Updated " + Instant.ofEpochMilli(feed.updatedAt).atZone(ZoneId.systemDefault()).format(updatedFmt)
}
