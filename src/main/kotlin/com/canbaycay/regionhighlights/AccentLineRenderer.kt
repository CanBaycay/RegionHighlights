package com.canbaycay.regionhighlights

import com.intellij.openapi.editor.markup.LineSeparatorRenderer
import java.awt.Color
import java.awt.Graphics

class AccentLineRenderer(private val color: Color, private val thickness: Int = 2) : LineSeparatorRenderer {
    override fun drawLine(g: Graphics, x1: Int, x2: Int, y: Int) {
        g.color = color
        g.fillRect(x1, y, x2 - x1, thickness)
    }
}
