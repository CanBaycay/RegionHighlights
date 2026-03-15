package com.canbaycay.regionhighlights

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.awt.Color

@Service(Service.Level.APP)
@State(name = "RegionHighlightSettings", storages = [Storage("regionHighlights.xml")])
class RegionHighlightSettings : PersistentStateComponent<RegionHighlightSettings.State> {

    class State {
        var enabled: Boolean = true
        var topLevelBgArgb: Int = Color(41, 98, 255, 18).rgb
        var nestedBgArgb: Int = Color(46, 204, 113, 18).rgb
        var topLevelAccentArgb: Int = Color(41, 98, 255, 100).rgb
        var nestedAccentArgb: Int = Color(46, 204, 113, 100).rgb
    }

    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) { this.state = state }

    val isEnabled: Boolean get() = state.enabled
    val topLevelBgColor: Color get() = Color(state.topLevelBgArgb, true)
    val nestedBgColor: Color get() = Color(state.nestedBgArgb, true)
    val topLevelAccentColor: Color get() = Color(state.topLevelAccentArgb, true)
    val nestedAccentColor: Color get() = Color(state.nestedAccentArgb, true)

    companion object {
        val instance: RegionHighlightSettings
            get() = ApplicationManager.getApplication().getService(RegionHighlightSettings::class.java)
    }
}
