package com.jmsbrain.subcalscriber.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.lifecycle.viewModelScope
import com.jmsbrain.subcalscriber.data.Calendar
import com.jmsbrain.subcalscriber.data.Feed
import com.jmsbrain.subcalscriber.data.findLinks
import com.jmsbrain.subcalscriber.data.freeTag
import com.jmsbrain.subcalscriber.data.tagFrom
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class FeedEditViewModel(id: String?) : LightViewModel<Unit>() {
    private val original = id?.let(Calendar::feed)
    val isNew = original == null
    val draft = MutableStateFlow(original ?: Feed())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    // A new feed's letter follows its name until it's picked by hand.
    private var tagPicked = original != null

    private fun autoTag(name: String) =
        freeTag(name, Calendar.feeds.value.filter { it.id != draft.value.id }.map { it.tag }.toSet())

    fun setName(name: String) {
        val d = draft.value
        draft.value = d.copy(name = name.trim(), tag = if (tagPicked) d.tag else autoTag(name))
    }

    fun setTag(text: String) {
        tagFrom(text).takeIf { it.isNotEmpty() }?.let {
            tagPicked = true
            draft.value = draft.value.copy(tag = it)
        }
    }

    fun setUrl(url: String) {
        draft.value = draft.value.copy(url = url.trim())
    }

    /** Takes the first link in the copied text (and a name written before it, if any). */
    fun paste(text: String) {
        val link = findLinks(text).firstOrNull()
        if (link == null) {
            message.value = "There's no link on the clipboard. Copy the calendar link first " +
                "(in Signal: long-press the message, then Copy text)."
            return
        }
        setUrl(link.url)
        if (draft.value.name.isBlank()) link.name?.let(::setName)
    }

    /** Saves, then downloads the feed so a bad address shows up right away. */
    fun save(done: () -> Unit) {
        val d = draft.value
        if (d.url.isBlank()) {
            message.value = "Add the feed's link first."
            return
        }
        Calendar.feeds.value.firstOrNull { it.id != d.id && Calendar.sameFeed(it.url, d.url) }?.let {
            message.value = "That calendar is already added as \"${it.name}\"."
            return
        }
        viewModelScope.launch {
            busy.value = true
            // No name yet: use the website for now, then the calendar's own name once downloaded.
            val unnamed = d.name.isBlank()
            val name = d.name.ifBlank { Calendar.hostOf(Calendar.httpUrl(d.url)) }
            val saved = Calendar.save(d.copy(name = name, tag = d.tag.ifBlank { autoTag(name) }))
            draft.value = saved
            if (original == null || original.url != saved.url || saved.updatedAt == null) Calendar.refresh(saved.id)
            if (unnamed) Calendar.nameAfterCalendar(saved.id, pickTag = !tagPicked)
            busy.value = false
            draft.value = Calendar.feed(saved.id) ?: saved
            val error = draft.value.error
            if (error == null) done() else message.value = "Saved, but couldn't download it.\n\n$error"
        }
    }

    fun delete(done: () -> Unit) {
        viewModelScope.launch {
            Calendar.delete(draft.value.id)
            done()
        }
    }
}

class FeedEditScreen(sealedActivity: SealedLightActivity, private val id: String?) :
    LightScreen<Unit, FeedEditViewModel>(sealedActivity) {

    override val viewModelClass get() = FeedEditViewModel::class.java
    override fun createViewModel() = FeedEditViewModel(id)

    @Composable
    override fun Content() {
        val d by viewModel.draft.collectAsState()
        val busy by viewModel.busy.collectAsState()
        val message by viewModel.message.collectAsState()
        val clipboard = LocalClipboard.current
        val scope = rememberCoroutineScope()

        SubcalTheme {
            SubcalPage(
                title = if (viewModel.isNew) "Add feed" else "Edit feed",
                left = backButton { goBack() },
                bottom = buildList {
                    if (d.id.isNotBlank()) add(LightBarButton.Text(text = "DELETE", onClick = {
                        navigateTo({ ConfirmScreen(it, "Remove \"${d.name}\" and its events from SubcalScriber?", "DELETE") }) { yes ->
                            if (yes == true) viewModel.delete { goBack() }
                        }
                    }))
                    add(LightBarButton.Text(text = if (busy) "CHECKING…" else "SAVE", onClick = { if (!busy) viewModel.save { goBack() } }))
                },
            ) {
                TapField("ADDRESS", d.url, "Tap to type the link") {
                    navigateTo({ TextEditScreen(it, "Feed address", d.url, caps = false) }) { text -> text?.let(viewModel::setUrl) }
                }
                // Read only when tapped.
                LightText(
                    "PASTE LINK",
                    LightTextVariant.Copy,
                    underline = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .lightClickable {
                            scope.launch {
                                val clip = runCatching { clipboard.getClipEntry()?.clipData }.getOrNull()
                                val text = clip?.let { c -> (0 until c.itemCount).joinToString("\n") { c.getItemAt(it).text?.toString().orEmpty() } }
                                viewModel.paste(text.orEmpty())
                            }
                        }
                        .padding(top = 0.5f.gridUnitsAsDp(), bottom = 0.5f.gridUnitsAsDp()),
                )
                TapField("NAME", d.name, "Optional: uses the calendar's name") {
                    navigateTo({ TextEditScreen(it, "Name", d.name) }) { text -> text?.let(viewModel::setName) }
                }
                TapField("LETTER", d.tag, "Picked from the name") {
                    navigateTo({ TextEditScreen(it, "One letter", d.tag) }) { text -> text?.let(viewModel::setTag) }
                }
                if (!viewModel.isNew) TapField("STATUS", status(d), "") {}
            }
            message?.let { LightFullscreenModal(message = it, onClose = { viewModel.message.value = null }) }
        }
    }
}
