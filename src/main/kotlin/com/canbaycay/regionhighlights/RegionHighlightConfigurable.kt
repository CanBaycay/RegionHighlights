package com.canbaycay.regionhighlights

import com.intellij.openapi.options.Configurable
import com.intellij.ui.ColorPanel
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Color
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class RegionHighlightConfigurable : Configurable {

    private var panel: JPanel? = null
    private var enabledCheckBox: JCheckBox? = null
    private var highlightEntireBlockCheckBox: JCheckBox? = null
    private var topLevelBgColorPanel: ColorPanel? = null
    private var nestedBgColorPanel: ColorPanel? = null
    private var topLevelAccentColorPanel: ColorPanel? = null
    private var nestedAccentColorPanel: ColorPanel? = null

    override fun getDisplayName(): String = "Region Highlights"

    override fun createComponent(): JComponent {
        val p = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
        }

        val enabled = JCheckBox("Enable region highlighting")
        val entireBlock = JCheckBox("Highlight entire block between #region and #endregion")
        val topBg = ColorPanel()
        val nestedBg = ColorPanel()
        val topAccent = ColorPanel()
        val nestedAccent = ColorPanel()

        var row = 0

        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2
        p.add(enabled, gbc)
        row++

        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2
        p.add(entireBlock, gbc)
        row++

        gbc.gridwidth = 1
        gbc.gridx = 0; gbc.gridy = row
        p.add(JLabel("Top-level background color:"), gbc)
        gbc.gridx = 1
        p.add(topBg, gbc)
        row++

        gbc.gridx = 0; gbc.gridy = row
        p.add(JLabel("Nested background color:"), gbc)
        gbc.gridx = 1
        p.add(nestedBg, gbc)
        row++

        gbc.gridx = 0; gbc.gridy = row
        p.add(JLabel("Top-level accent line color:"), gbc)
        gbc.gridx = 1
        p.add(topAccent, gbc)
        row++

        gbc.gridx = 0; gbc.gridy = row
        p.add(JLabel("Nested accent line color:"), gbc)
        gbc.gridx = 1
        p.add(nestedAccent, gbc)
        row++

        val resetButton = JButton("Reset to Defaults")
        resetButton.addActionListener { resetToDefaults() }
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2
        p.add(resetButton, gbc)

        enabledCheckBox = enabled
        highlightEntireBlockCheckBox = entireBlock
        topLevelBgColorPanel = topBg
        nestedBgColorPanel = nestedBg
        topLevelAccentColorPanel = topAccent
        nestedAccentColorPanel = nestedAccent
        panel = p

        reset()
        return p
    }

    override fun isModified(): Boolean {
        val s = RegionHighlightSettings.instance
        return enabledCheckBox?.isSelected != s.isEnabled ||
                highlightEntireBlockCheckBox?.isSelected != s.highlightEntireBlock ||
                topLevelBgColorPanel?.selectedColor != s.topLevelBgColor ||
                nestedBgColorPanel?.selectedColor != s.nestedBgColor ||
                topLevelAccentColorPanel?.selectedColor != s.topLevelAccentColor ||
                nestedAccentColorPanel?.selectedColor != s.nestedAccentColor
    }

    override fun apply() {
        val s = RegionHighlightSettings.instance.state
        s.enabled = enabledCheckBox?.isSelected ?: true
        s.highlightEntireBlock = highlightEntireBlockCheckBox?.isSelected ?: false
        topLevelBgColorPanel?.selectedColor?.let { s.topLevelBgArgb = it.rgb }
        nestedBgColorPanel?.selectedColor?.let { s.nestedBgArgb = it.rgb }
        topLevelAccentColorPanel?.selectedColor?.let { s.topLevelAccentArgb = it.rgb }
        nestedAccentColorPanel?.selectedColor?.let { s.nestedAccentArgb = it.rgb }
    }

    override fun reset() {
        val s = RegionHighlightSettings.instance
        enabledCheckBox?.isSelected = s.isEnabled
        highlightEntireBlockCheckBox?.isSelected = s.highlightEntireBlock
        topLevelBgColorPanel?.selectedColor = s.topLevelBgColor
        nestedBgColorPanel?.selectedColor = s.nestedBgColor
        topLevelAccentColorPanel?.selectedColor = s.topLevelAccentColor
        nestedAccentColorPanel?.selectedColor = s.nestedAccentColor
    }

    private fun resetToDefaults() {
        val defaults = RegionHighlightSettings.State()
        enabledCheckBox?.isSelected = defaults.enabled
        highlightEntireBlockCheckBox?.isSelected = defaults.highlightEntireBlock
        topLevelBgColorPanel?.selectedColor = Color(defaults.topLevelBgArgb, true)
        nestedBgColorPanel?.selectedColor = Color(defaults.nestedBgArgb, true)
        topLevelAccentColorPanel?.selectedColor = Color(defaults.topLevelAccentArgb, true)
        nestedAccentColorPanel?.selectedColor = Color(defaults.nestedAccentArgb, true)
    }

    override fun disposeUIResources() {
        panel = null
        enabledCheckBox = null
        highlightEntireBlockCheckBox = null
        topLevelBgColorPanel = null
        nestedBgColorPanel = null
        topLevelAccentColorPanel = null
        nestedAccentColorPanel = null
    }
}
