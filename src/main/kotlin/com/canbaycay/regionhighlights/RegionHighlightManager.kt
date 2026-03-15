package com.canbaycay.regionhighlights

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FoldingListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.SeparatorPlacement
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import java.util.concurrent.ConcurrentHashMap

class RegionHighlightManager : EditorFactoryListener {

    private val editorData = ConcurrentHashMap<Editor, EditorHighlightData>()

    private class EditorHighlightData(
        val disposable: Disposable,
        var highlighters: List<RangeHighlighter> = emptyList()
    )

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        if (!file.name.endsWith(".cs")) return

        val disposable = Disposer.newDisposable("RegionHighlights:${file.name}")
        val data = EditorHighlightData(disposable)
        editorData[editor] = data

        // Listen for document changes
        editor.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                updateHighlights(editor)
            }
        }, disposable)

        // Listen for folding changes
        if (editor is EditorEx) {
            editor.foldingModel.addListener(object : FoldingListener {
                override fun onFoldProcessingEnd() {
                    updateHighlights(editor)
                }
            }, disposable)
        }

        // Initial highlight
        updateHighlights(editor)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val data = editorData.remove(event.editor) ?: return
        Disposer.dispose(data.disposable)
    }

    private fun updateHighlights(editor: Editor) {
        if (editor.isDisposed) return

        val settings = RegionHighlightSettings.instance
        val data = editorData[editor] ?: return

        // Remove old highlighters
        data.highlighters.forEach { highlighter ->
            if (highlighter.isValid) {
                editor.markupModel.removeHighlighter(highlighter)
            }
        }

        if (!settings.isEnabled) {
            data.highlighters = emptyList()
            return
        }

        // Parse regions
        val regions = RegionParser.parse(editor.document.text)
        val newHighlighters = mutableListOf<RangeHighlighter>()
        val document = editor.document

        for (region in regions) {
            if (region.startLine >= document.lineCount || region.endLine >= document.lineCount) continue

            val isTopLevel = region.depth == 0
            val bgColor = if (isTopLevel) settings.topLevelBgColor else settings.nestedBgColor
            val accentColor = if (isTopLevel) settings.topLevelAccentColor else settings.nestedAccentColor

            val startOffset = document.getLineStartOffset(region.startLine)
            val bgAttrs = TextAttributes().apply { backgroundColor = bgColor }

            if (settings.highlightEntireBlock) {
                // Background highlight for entire region
                val endOffset = document.getLineEndOffset(region.endLine)
                val bgHighlighter = editor.markupModel.addRangeHighlighter(
                    startOffset, endOffset,
                    HighlighterLayer.FIRST - 1 + region.depth,
                    bgAttrs,
                    HighlighterTargetArea.LINES_IN_RANGE
                )
                newHighlighters.add(bgHighlighter)
            } else {
                // Background highlight only on #region and #endregion lines
                val regionLineHighlighter = editor.markupModel.addLineHighlighter(
                    region.startLine, HighlighterLayer.FIRST - 1 + region.depth, bgAttrs
                )
                newHighlighters.add(regionLineHighlighter)
                val endRegionLineHighlighter = editor.markupModel.addLineHighlighter(
                    region.endLine, HighlighterLayer.FIRST - 1 + region.depth, bgAttrs
                )
                newHighlighters.add(endRegionLineHighlighter)
            }

            // Check if collapsed
            val isCollapsed = editor.foldingModel.isOffsetCollapsed(startOffset)

            // Top accent line (above #region)
            val topAccent = editor.markupModel.addLineHighlighter(
                region.startLine, HighlighterLayer.FIRST + 100, null
            )
            topAccent.lineSeparatorRenderer = AccentLineRenderer(accentColor)
            topAccent.lineSeparatorPlacement = SeparatorPlacement.TOP
            newHighlighters.add(topAccent)

            if (isCollapsed) {
                // Both accents on the #region line
                val bottomAccent = editor.markupModel.addLineHighlighter(
                    region.startLine, HighlighterLayer.FIRST + 100, null
                )
                bottomAccent.lineSeparatorRenderer = AccentLineRenderer(accentColor)
                bottomAccent.lineSeparatorPlacement = SeparatorPlacement.BOTTOM
                newHighlighters.add(bottomAccent)
            } else {
                // Bottom accent on #endregion line
                val bottomAccent = editor.markupModel.addLineHighlighter(
                    region.endLine, HighlighterLayer.FIRST + 100, null
                )
                bottomAccent.lineSeparatorRenderer = AccentLineRenderer(accentColor)
                bottomAccent.lineSeparatorPlacement = SeparatorPlacement.BOTTOM
                newHighlighters.add(bottomAccent)
            }
        }

        data.highlighters = newHighlighters
    }
}
