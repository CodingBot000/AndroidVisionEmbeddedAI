package dev.robotvision.domain

/** Contract pinned by host invocation: boxes, classes, scores, count; NMS is in the graph. */
object EfficientDetOutputParser {
    /** Must run before model.run: dynamic scalar output allocations cannot hold detector tensors. */
    fun requireCapacity(inputBytes: Int, outputElements: List<Int>) {
        require(inputBytes == 320 * 320 * 3 && outputElements == listOf(100, 25, 25, 1)) {
            "LiteRT buffer capacity incompatible: input=$inputBytes, outputs=$outputElements; expected 307200 / [100,25,25,1]. Native inference skipped before any output write; using MediaPipe CPU."
        }
    }

    fun parse(boxes: FloatArray, classes: FloatArray, scores: FloatArray, count: Float, labels: List<String>): List<Detection> {
        require(boxes.size == scores.size * 4 && classes.size == scores.size && count.isFinite()) { "EfficientDet output contract mismatch: boxes=${boxes.size}, classes=${classes.size}, scores=${scores.size}, count=$count; expected boxes=100, classes=25, scores=25, count scalar" }
        return (0 until count.toInt().coerceIn(0, scores.size)).mapNotNull { i ->
            if (!classes[i].isFinite() || !scores[i].isFinite() || scores[i] !in .3f..1f) return@mapNotNull null
            val label=labels.getOrNull(classes[i].toInt()) ?: return@mapNotNull null
            if(label=="???") return@mapNotNull null
            val raw=boxes.sliceArray(i*4 until i*4+4)
            if(raw.any { !it.isFinite() }) return@mapNotNull null
            val box=Box(raw[1].coerceIn(0f,1f),raw[0].coerceIn(0f,1f),raw[3].coerceIn(0f,1f),raw[2].coerceIn(0f,1f))
            if(box.left>=box.right || box.top>=box.bottom) return@mapNotNull null
            Detection(label,scores[i],box)
        }
    }
}
/** The analyzer owns close even when conversion, inference, or cancellation throws. */
inline fun <T> withOwnedFrame(close: () -> Unit, consume: () -> T): T = try { consume() } finally { close() }
