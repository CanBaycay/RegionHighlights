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
