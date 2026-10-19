package dev.robotvision.domain

import kotlin.math.ceil

data class Metrics(
    val processed: Long = 0, val skipped: Long = 0, val p50: Double? = null, val p95: Double? = null,
    val resultHz: Double? = null, val modelLoadMs: Double? = null, val speechMs: Double? = null,
    val totalMs: Double? = null,
)
class PerformanceMetrics {
    private val latencies = ArrayDeque<Double>()
    private val timestamps = ArrayDeque<Long>()
    var value = Metrics(); private set
    @Synchronized fun record(frame: DetectionFrame): Metrics {
        if (frame.simulated || frame.inferenceMs == null) return value
        latencies.addLast(frame.inferenceMs); timestamps.addLast(frame.timestamp)
        while (latencies.size > 120) latencies.removeFirst()
        while (timestamps.size > 120) timestamps.removeFirst()
        val sorted = latencies.sorted()
        fun percentile(p: Double) = sorted[(ceil(sorted.size * p).toInt() - 1).coerceAtLeast(0)]
        val seconds = (timestamps.last() - timestamps.first()) / 1000.0
        value = value.copy(processed = value.processed + 1, p50 = percentile(.5), p95 = percentile(.95),
            resultHz = if (seconds > 0) (timestamps.size - 1) / seconds else null, totalMs = frame.totalMs)
        return value
    }
    @Synchronized fun skipped() { value = value.copy(skipped = value.skipped + 1) }
    @Synchronized fun loaded(ms: Double) { value = value.copy(modelLoadMs = ms) }
    @Synchronized fun speech(ms: Double) { value = value.copy(speechMs = ms) }
}
