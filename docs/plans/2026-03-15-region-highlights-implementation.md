# Region Highlights Plugin — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a Rider plugin that highlights `#region`/`#endregion` blocks with background colors and accent lines.

**Architecture:** EditorFactoryListener detects `.cs` file editors, DocumentListener and FoldingListener trigger re-parsing, RegionParser scans document text via string matching, and RangeHighlighters on the MarkupModel render background colors and accent lines via LineSeparatorRenderer. Note: we use MarkupModel directly instead of ExternalAnnotator because AnnotationHolder doesn't expose RangeHighlighter for LineSeparatorRenderer/accent lines.

**Tech Stack:** Kotlin, Gradle with IntelliJ Platform Gradle Plugin 2.x, targeting Rider 2025.3

---

### Task 1: Project Scaffolding

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `src/main/resources/META-INF/plugin.xml`

**Step 1: Create settings.gradle.kts**

```kotlin
rootProject.name = "RiderRegionHighlights"

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.13.1"
}
```

**Step 2: Create build.gradle.kts**

```kotlin
plugins {
    id("org.jetbrains.intellij.platform") version "2.13.1"
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
}

group = "com.canbaycay"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        rider("2025.3.3")
        instrumentationTools()
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}
```

**Step 3: Create gradle.properties**

```properties
org.gradle.configuration-cache=true
org.gradle.caching=true
kotlin.stdlib.default.dependency=false
```

**Step 4: Create plugin.xml**

```xml
<idea-plugin>
    <id>com.canbaycay.regionhighlights</id>
    <name>Region Highlights</name>
    <vendor>Can Baycay</vendor>
    <description><![CDATA[
        Highlights #region/#endregion blocks in C# files with background colors and accent lines.
    ]]></description>

    <depends>com.intellij.modules.platform</depends>
</idea-plugin>
```

**Step 5: Generate Gradle wrapper**

Run: `gradle wrapper --gradle-version 8.13`
Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/` created

Note: Use 8.13 (not 9.x) for broader compatibility. Adjust if build fails.

**Step 6: Verify project compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (may take a while first time — downloads Rider SDK)

**Step 7: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/ gradlew gradlew.bat src/main/resources/META-INF/plugin.xml
git commit -m "feat: scaffold Gradle project targeting Rider"
```

---

### Task 2: RegionBlock and RegionParser with Tests (TDD)

**Files:**
- Create: `src/test/kotlin/com/canbaycay/regionhighlights/RegionParserTest.kt`
- Create: `src/main/kotlin/com/canbaycay/regionhighlights/RegionBlock.kt`
- Create: `src/main/kotlin/com/canbaycay/regionhighlights/RegionParser.kt`

**Step 1: Write the failing tests**

```kotlin
package com.canbaycay.regionhighlights

import org.junit.Assert.*
import org.junit.Test

class RegionParserTest {

    @Test
    fun `empty text returns no regions`() {
        assertEquals(emptyList<RegionBlock>(), RegionParser.parse(""))
    }

    @Test
    fun `single region block`() {
        val text = """
            #region Public Methods
            void Foo() {}
            #endregion
        """.trimIndent()

        val regions = RegionParser.parse(text)
        assertEquals(1, regions.size)
        assertEquals(RegionBlock(startLine = 0, endLine = 2, depth = 0), regions[0])
    }

    @Test
    fun `nested regions`() {
        val text = """
            #region Outer
            #region Inner
            code
            #endregion
            #endregion
        """.trimIndent()

        val regions = RegionParser.parse(text)
        assertEquals(2, regions.size)

        val inner = regions.find { it.depth == 1 }!!
        assertEquals(RegionBlock(startLine = 1, endLine = 3, depth = 1), inner)

        val outer = regions.find { it.depth == 0 }!!
        assertEquals(RegionBlock(startLine = 0, endLine = 4, depth = 0), outer)
    }

    @Test
    fun `region with leading whitespace`() {
        val text = "    #region Indented\n    code\n    #endregion"

        val regions = RegionParser.parse(text)
        assertEquals(1, regions.size)
        assertEquals(RegionBlock(startLine = 0, endLine = 2, depth = 0), regions[0])
    }

    @Test
    fun `region without name`() {
        val text = "#region\ncode\n#endregion"

        val regions = RegionParser.parse(text)
        assertEquals(1, regions.size)
    }

    @Test
    fun `unmatched endregion is ignored`() {
        val text = "#endregion\n#region A\ncode\n#endregion"

        val regions = RegionParser.parse(text)
        assertEquals(1, regions.size)
        assertEquals(RegionBlock(startLine = 1, endLine = 3, depth = 0), regions[0])
    }

    @Test
    fun `unmatched region is ignored`() {
        val text = "#region A\ncode"

        val regions = RegionParser.parse(text)
        assertEquals(0, regions.size)
    }

    @Test
    fun `regionName without space is not a region`() {
        val text = "#regionName\ncode\n#endregion"

        val regions = RegionParser.parse(text)
        assertEquals(0, regions.size)
    }

    @Test
    fun `sibling regions`() {
        val text = "#region A\n#endregion\n#region B\n#endregion"

        val regions = RegionParser.parse(text)
        assertEquals(2, regions.size)
        assertEquals(0, regions[0].depth)
        assertEquals(0, regions[1].depth)
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test`
Expected: FAIL — classes not found

**Step 3: Write RegionBlock**

```kotlin
package com.canbaycay.regionhighlights

data class RegionBlock(
    val startLine: Int,
    val endLine: Int,
    val depth: Int
)
```

**Step 4: Write RegionParser**

```kotlin
package com.canbaycay.regionhighlights

object RegionParser {

    fun parse(text: String): List<RegionBlock> {
        if (text.isEmpty()) return emptyList()

        val lines = text.lines()
        val stack = mutableListOf<Pair<Int, Int>>() // (lineNumber, depth)
        val regions = mutableListOf<RegionBlock>()
        var currentDepth = 0

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trimStart()
            when {
                isRegionStart(trimmed) -> {
                    stack.add(index to currentDepth)
                    currentDepth++
                }
                isRegionEnd(trimmed) -> {
                    if (stack.isNotEmpty()) {
                        val (startLine, depth) = stack.removeLast()
                        currentDepth--
                        regions.add(RegionBlock(startLine, index, depth))
                    }
                }
            }
        }

        return regions
    }

    private fun isRegionStart(trimmed: String): Boolean {
        return trimmed.startsWith("#region") &&
                (trimmed.length == 7 || trimmed[7].isWhitespace())
    }

    private fun isRegionEnd(trimmed: String): Boolean {
        return trimmed.startsWith("#endregion") &&
                (trimmed.length == 10 || trimmed[10].isWhitespace())
    }
}
```

**Step 5: Run tests to verify they pass**

Run: `./gradlew test`
Expected: ALL PASS

**Step 6: Commit**

```bash
git add src/main/kotlin/com/canbaycay/regionhighlights/RegionBlock.kt \
        src/main/kotlin/com/canbaycay/regionhighlights/RegionParser.kt \
        src/test/kotlin/com/canbaycay/regionhighlights/RegionParserTest.kt
git commit -m "feat: add RegionBlock and RegionParser with tests"
```

---

### Task 3: Settings Persistence

**Files:**
- Create: `src/main/kotlin/com/canbaycay/regionhighlights/RegionHighlightSettings.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

**Step 1: Write RegionHighlightSettings**

```kotlin
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
```

**Step 2: Register application service in plugin.xml**

Add inside `<extensions defaultExtensionNs="com.intellij">`:
```xml
<applicationService serviceImplementation="com.canbaycay.regionhighlights.RegionHighlightSettings"/>
```

Note: Depending on IntelliJ Platform version, `@Service` annotation may be sufficient without XML registration. Include both for compatibility.

**Step 3: Verify build compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/kotlin/com/canbaycay/regionhighlights/RegionHighlightSettings.kt \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat: add settings persistence with color defaults"
```

---

### Task 4: Accent Line Renderer

**Files:**
- Create: `src/main/kotlin/com/canbaycay/regionhighlights/AccentLineRenderer.kt`

**Step 1: Write AccentLineRenderer**

```kotlin
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
```

**Step 2: Commit**

```bash
git add src/main/kotlin/com/canbaycay/regionhighlights/AccentLineRenderer.kt
git commit -m "feat: add AccentLineRenderer for region border lines"
```

---

### Task 5: Core Highlighting Manager

**Files:**
- Create: `src/main/kotlin/com/canbaycay/regionhighlights/RegionHighlightManager.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

**Step 1: Write RegionHighlightManager**

```kotlin
package com.canbaycay.regionhighlights

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.SeparatorPlacement
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import java.awt.Color
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
        val foldingModel = editor.foldingModel
        if (foldingModel is FoldingModelEx) {
            foldingModel.addListener(object : com.intellij.openapi.editor.FoldingGroup.FoldingListener {
                // Note: the actual listener interface may vary by platform version.
                // See Step 2 for the correct interface.
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

            // Background highlight for entire region
            val startOffset = document.getLineStartOffset(region.startLine)
            val endOffset = document.getLineEndOffset(region.endLine)
            val bgAttrs = TextAttributes().apply { backgroundColor = bgColor }
            val bgHighlighter = editor.markupModel.addRangeHighlighter(
                startOffset, endOffset,
                HighlighterLayer.FIRST - 1 + region.depth,
                bgAttrs,
                HighlighterTargetArea.LINES_IN_RANGE
            )
            newHighlighters.add(bgHighlighter)

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
```

**Step 2: Handle FoldingListener registration**

The FoldingListener API varies across platform versions. The correct approach is:

```kotlin
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.impl.FoldingModelImpl

// In editorCreated, replace the folding listener block with:
val foldingModel = editor.foldingModel
if (foldingModel is FoldingModelEx) {
    foldingModel.addListener(object : com.intellij.openapi.editor.FoldingGroup.FoldingListener {
        override fun onFoldRegionStateChange(region: com.intellij.openapi.editor.FoldRegion) {
            updateHighlights(editor)
        }

        override fun onFoldProcessingEnd() {}
    }, data.disposable)
}
```

Note: If `FoldingGroup.FoldingListener` doesn't exist in the target platform version, check for `com.intellij.openapi.editor.ex.FoldingListener` or `EditorFoldingListener` on the message bus. The implementor should look up the correct listener interface for the target Rider SDK version and adapt accordingly.

**Step 3: Register listener in plugin.xml**

Add inside `<idea-plugin>`:
```xml
<applicationListeners>
    <listener class="com.canbaycay.regionhighlights.RegionHighlightManager"
              topic="com.intellij.openapi.editor.event.EditorFactoryListener"/>
</applicationListeners>
```

**Step 4: Verify build compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/kotlin/com/canbaycay/regionhighlights/RegionHighlightManager.kt \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat: add core RegionHighlightManager with background and accent lines"
```

---

### Task 6: Settings UI

**Files:**
- Create: `src/main/kotlin/com/canbaycay/regionhighlights/RegionHighlightConfigurable.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

**Step 1: Write RegionHighlightConfigurable**

```kotlin
package com.canbaycay.regionhighlights

import com.intellij.openapi.options.Configurable
import com.intellij.ui.ColorPanel
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class RegionHighlightConfigurable : Configurable {

    private var panel: JPanel? = null
    private var enabledCheckBox: JCheckBox? = null
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
        val topBg = ColorPanel()
        val nestedBg = ColorPanel()
        val topAccent = ColorPanel()
        val nestedAccent = ColorPanel()

        var row = 0

        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2
        p.add(enabled, gbc)
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

        enabledCheckBox = enabled
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
                topLevelBgColorPanel?.selectedColor != s.topLevelBgColor ||
                nestedBgColorPanel?.selectedColor != s.nestedBgColor ||
                topLevelAccentColorPanel?.selectedColor != s.topLevelAccentColor ||
                nestedAccentColorPanel?.selectedColor != s.nestedAccentColor
    }

    override fun apply() {
        val s = RegionHighlightSettings.instance.state
        s.enabled = enabledCheckBox?.isSelected ?: true
        topLevelBgColorPanel?.selectedColor?.let { s.topLevelBgArgb = it.rgb }
        nestedBgColorPanel?.selectedColor?.let { s.nestedBgArgb = it.rgb }
        topLevelAccentColorPanel?.selectedColor?.let { s.topLevelAccentArgb = it.rgb }
        nestedAccentColorPanel?.selectedColor?.let { s.nestedAccentArgb = it.rgb }
    }

    override fun reset() {
        val s = RegionHighlightSettings.instance
        enabledCheckBox?.isSelected = s.isEnabled
        topLevelBgColorPanel?.selectedColor = s.topLevelBgColor
        nestedBgColorPanel?.selectedColor = s.nestedBgColor
        topLevelAccentColorPanel?.selectedColor = s.topLevelAccentColor
        nestedAccentColorPanel?.selectedColor = s.nestedAccentColor
    }

    override fun disposeUIResources() {
        panel = null
        enabledCheckBox = null
        topLevelBgColorPanel = null
        nestedBgColorPanel = null
        topLevelAccentColorPanel = null
        nestedAccentColorPanel = null
    }
}
```

**Step 2: Register configurable in plugin.xml**

Add inside `<extensions defaultExtensionNs="com.intellij">`:
```xml
<applicationConfigurable instance="com.canbaycay.regionhighlights.RegionHighlightConfigurable"
                         id="com.canbaycay.regionhighlights.settings"
                         displayName="Region Highlights"
                         parentId="editor"/>
```

**Step 3: Verify build compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/kotlin/com/canbaycay/regionhighlights/RegionHighlightConfigurable.kt \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat: add settings UI with color pickers"
```

---

### Task 7: Build Plugin and Test

**Step 1: Build the plugin ZIP**

Run: `./gradlew buildPlugin`
Expected: Plugin ZIP created in `build/distributions/RiderRegionHighlights-1.0.0.zip`

**Step 2: Install in Rider**

1. Open Rider
2. Go to **Settings > Plugins**
3. Click gear icon > **Install Plugin from Disk...**
4. Select `build/distributions/RiderRegionHighlights-1.0.0.zip`
5. Restart Rider

**Step 3: Manual test checklist**

- [ ] Open a `.cs` file with `#region`/`#endregion` blocks
- [ ] Verify background color appears for top-level regions
- [ ] Add a nested region — verify different background color
- [ ] Verify accent line appears above `#region` line
- [ ] Verify accent line appears below `#endregion` line
- [ ] Collapse a region — verify both accent lines visible on the collapsed line
- [ ] Expand the region — verify accent lines return to normal positions
- [ ] Open **Settings > Editor > Region Highlights** — verify color pickers work
- [ ] Change a color, click Apply — verify editor updates immediately
- [ ] Uncheck Enable — verify highlights disappear
- [ ] Open a non-`.cs` file — verify no highlighting applied
- [ ] Edit text inside a region — verify highlights update correctly

**Step 4: Fix any issues found during testing and commit**

```bash
git add -A
git commit -m "fix: address issues found during manual testing"
```

**Step 5: Final commit**

```bash
git add -A
git commit -m "chore: finalize v1.0.0 build"
```
