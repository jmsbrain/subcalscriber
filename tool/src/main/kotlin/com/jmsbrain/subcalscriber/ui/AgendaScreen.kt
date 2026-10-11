package com.jmsbrain.subcalscriber.ui

import androidx.compose.runtime.Composable
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant

class AgendaViewModel : LightViewModel<Unit>()

@InitialScreen
class AgendaScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, AgendaViewModel>(sealedActivity) {

    override val viewModelClass get() = AgendaViewModel::class.java
    override fun createViewModel() = AgendaViewModel()

    @Composable
    override fun Content() {
        SubcalPage(title = "SubcalScriber") {
            LightText("No calendars yet.", LightTextVariant.Copy, lighten = true)
        }
    }
}
