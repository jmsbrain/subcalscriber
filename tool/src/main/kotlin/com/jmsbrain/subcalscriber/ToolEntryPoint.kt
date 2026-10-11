package com.jmsbrain.subcalscriber

import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.toolmanager.ClientLeafNode
import com.thelightphone.toolmanager.ClientToolManifest
import com.thelightphone.toolmanager.FileBrowserSpec

/** Folder shown in LightOS's Tool Manager. */
const val IMPORT_FOLDER = "add-feeds"

@EntryPoint
object ToolEntryPoint : LightEntryPoint {
    override fun getToolManagerManifest() = ClientToolManifest(
        title = "SubcalScriber",
        roots = listOf(
            ClientLeafNode(
                FileBrowserSpec(
                    label = "Add feeds",
                    path = IMPORT_FOLDER,
                    headerText = "Upload a text file with your calendar links (https:// or webcal://), one per line. " +
                        "To name one, write the name first: \"Work: https://…\". " +
                        "Open SubcalScriber and they're added automatically.",
                ),
            ),
        ),
    )
}
