package com.jmsbrain.subcalscriber.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import com.jmsbrain.subcalscriber.data.AgendaItem
import com.jmsbrain.subcalscriber.data.Calendar
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** How far ahead the agenda looks. */
const val AGENDA_DAYS = 60L

/** Feeds older than this get re-downloaded when the app opens. */
private const val STALE_MS = 60 * 60 * 1000L

class AgendaViewModel : LightViewModel<Unit>() {
    /** Each day with its events; null while loading. */
    val days = MutableStateFlow<List<Pair<LocalDate, List<AgendaItem>>>?>(null)
    val showing = MutableStateFlow<String?>(null) // feed id, or null for all
    val refreshing = MutableStateFlow(false)
    private var all: List<AgendaItem> = emptyList()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        if (showing.value != null && Calendar.feed(showing.value!!) == null) showing.value = null
        viewModelScope.launch {
            load()
            val now = System.currentTimeMillis()
            if (Calendar.feeds.value.any { (it.updatedAt ?: 0) < now - STALE_MS }) refresh(onlyStale = true)
        }
    }

    fun refresh(onlyStale: Boolean = false) {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            val now = System.currentTimeMillis()
            Calendar.feeds.value
                .filter { !onlyStale || (it.updatedAt ?: 0) < now - STALE_MS }
                .forEach { Calendar.refresh(it.id) }
            refreshing.value = false
            load()
        }
    }

    /** ALL, then each feed in turn. */
    fun nextFilter() {
        val feeds = Calendar.feeds.value
        val i = feeds.indexOfFirst { it.id == showing.value }
        showing.value = if (i + 1 < feeds.size) feeds[i + 1].id else null
        group()
    }

    private suspend fun load() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        all = Calendar.agenda(today.atStartOfDay(zone), today.plusDays(AGENDA_DAYS).atStartOfDay(zone), zone)
        group()
    }

    // An event shows on every day it covers, from today on.
    private fun group() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val last = today.plusDays(AGENDA_DAYS - 1)
        val byDay = sortedMapOf<LocalDate, MutableList<AgendaItem>>()
        for (item in all) {
            if (showing.value != null && item.feed.id != showing.value) continue
            val e = item.event
            var day = maxOf(e.start.withZoneSameInstant(zone).toLocalDate(), today)
            val endDay = lastDay(e.start, e.end, e.allDay, zone)
            while (day <= endDay && day <= last) {
                byDay.getOrPut(day) { mutableListOf() } += item
                day = day.plusDays(1)
            }
        }
        days.value = byDay.map { (d, items) -> d to items }
    }
}

fun lastDay(start: ZonedDateTime, end: ZonedDateTime, allDay: Boolean, zone: ZoneId): LocalDate {
    if (allDay) return maxOf(start.toLocalDate(), end.toLocalDate().minusDays(1))
    val s = start.withZoneSameInstant(zone)
    val e = end.withZoneSameInstant(zone)
    return if (e <= s) s.toLocalDate() else e.minusNanos(1).toLocalDate()
}

@InitialScreen
class AgendaScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, AgendaViewModel>(sealedActivity) {

    init {
        Calendar.init(lightContext.filesDir)
    }

    override val viewModelClass get() = AgendaViewModel::class.java
    override fun createViewModel() = AgendaViewModel()

    @Composable
    override fun Content() {
        val days by viewModel.days.collectAsState()
        val feeds by Calendar.feeds.collectAsState()
        val showing by viewModel.showing.collectAsState()
        val refreshing by viewModel.refreshing.collectAsState()
        val shownFeed = feeds.firstOrNull { it.id == showing }
        val zone = ZoneId.systemDefault()

        SubcalPage(
            title = shownFeed?.name ?: "SubcalScriber",
            right = LightBarButton.LightIcon(icon = LightIcons.SETTINGS, onClick = { navigateTo({ FeedsScreen(it) }) }),
            bottom = if (feeds.isEmpty()) {
                listOf(LightBarButton.Text(text = "ADD FEED", onClick = { navigateTo({ FeedEditScreen(it, null) }) }))
            } else {
                listOf(
                    LightBarButton.Text(text = if (refreshing) "UPDATING…" else "REFRESH", onClick = { viewModel.refresh() }),
                    LightBarButton.Text(text = "SHOW: ${shownFeed?.tag ?: "ALL"}", onClick = { viewModel.nextFilter() }),
                )
            },
        ) {
            val list = days
            when {
                feeds.isEmpty() -> LightText(
                    "No calendars yet. Tap ADD FEED and paste the web address of an .ics calendar.",
                    LightTextVariant.Paragraph,
                    lighten = true,
                )
                list == null -> LightText("Loading…", LightTextVariant.Copy, lighten = true)
                list.isEmpty() -> LightText(
                    if (refreshing) "Updating…" else "Nothing in the next $AGENDA_DAYS days.",
                    LightTextVariant.Copy,
                    lighten = true,
                )
                else -> list.forEach { (day, items) ->
                    LightText(
                        dayLabel(day),
                        LightTextVariant.Superfine,
                        lighten = true,
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), bottom = 0.25f.gridUnitsAsDp()),
                    )
                    items.forEach { item -> EventRow(item, day, zone) }
                }
            }
        }
    }

    @Composable
    private fun EventRow(item: AgendaItem, day: LocalDate, zone: ZoneId) {
        val e = item.event
        val time = when {
            e.allDay -> "all day"
            e.start.withZoneSameInstant(zone).toLocalDate() == day -> clock(e.start.withZoneSameInstant(zone).toLocalTime())
            else -> "cont."
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .lightClickable { navigateTo({ EventScreen(it, item) }) }
                .padding(vertical = 0.4f.gridUnitsAsDp()),
        ) {
            LightText(time, LightTextVariant.Detail, lighten = true, maxLines = 1, modifier = Modifier.width(4.5f.gridUnitsAsDp()))
            Box(Modifier.weight(1f)) {
                LightText(e.title, LightTextVariant.Copy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LightText(
                item.feed.tag,
                LightTextVariant.Detail,
                lighten = true,
                align = TextAlign.End,
                modifier = Modifier.width(1.5f.gridUnitsAsDp()),
            )
        }
    }
}
