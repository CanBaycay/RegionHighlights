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
