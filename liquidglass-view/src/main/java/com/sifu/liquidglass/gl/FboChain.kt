package com.sifu.liquidglass.gl

/**
 * A mip-like chain of [FboPair]s. Level 0 matches the base size; each higher level halves
 * both dimensions. Used by the multi-level "dual-filter" blur: downsample through the chain,
 * blur H+V at the smallest level, upsample happens for free via `GL_LINEAR` at composite time.
 */
internal class FboChain {

    var baseWidth: Int = 0
        private set
    var baseHeight: Int = 0
        private set
    var levelCount: Int = 0
        private set

    private val pairs = ArrayList<FboPair>()

    /** Allocates `numLevels` pairs (level 0 = base, level i = base >> i). Reuses if unchanged. */
    fun ensureSize(baseW: Int, baseH: Int, numLevels: Int) {
        val safeLevels = numLevels.coerceAtLeast(1)
        if (baseW == baseWidth && baseH == baseHeight && pairs.size == safeLevels) return
        release()
        baseWidth = baseW
        baseHeight = baseH
        levelCount = safeLevels
        for (i in 0 until safeLevels) {
            val scale = 1 shl i
            val w = (baseW / scale).coerceAtLeast(1)
            val h = (baseH / scale).coerceAtLeast(1)
            pairs.add(FboPair().apply { ensureSize(w, h) })
        }
    }

    fun pair(level: Int): FboPair = pairs[level]

    fun release() {
        pairs.forEach { it.release() }
        pairs.clear()
        baseWidth = 0
        baseHeight = 0
        levelCount = 0
    }
}
