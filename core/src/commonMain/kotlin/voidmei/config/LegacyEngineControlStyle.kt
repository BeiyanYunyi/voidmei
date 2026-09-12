package voidmei.config

/** Java's 24 + offset basis, before system display scaling. */
data class LegacyEngineControlStyle(val offset: Int) {
    init { require(offset in -6..20) }
    val baseSize: Int get() = 24 + offset
    val dimensions: EngineControlDimensions get() = EngineControlDimensions(4 * baseSize, baseSize / 2)
    val textSize: Float get() = ((baseSize + 1) / 2).toFloat()
    fun applyTo(region: HudRegion): HudRegion = region.copy(engineControlDimensions = dimensions,
        readingTextSizes = ReadingTextSizes(textSize, textSize, textSize),
        readingTextWeights = ReadingTextWeights(700, 700, 700), fontScale = 1f)
}
