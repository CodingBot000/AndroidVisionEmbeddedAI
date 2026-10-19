package dev.robotvision.domain

enum class SearchStatus { IDLE, SEARCHING, FOUND, NOT_FOUND, STOPPED }
data class SearchState(val target: Target? = null, val status: SearchStatus = SearchStatus.IDLE, val startedAt: Long = 0)
sealed interface SearchAction {
    data class Start(val target: Target, val now: Long) : SearchAction
    data object Found : SearchAction
    data object Timeout : SearchAction
    data object Stop : SearchAction
    data object Clear : SearchAction
}
object VisionReducer {
    fun reduce(state: SearchState, action: SearchAction): SearchState = when (action) {
        is SearchAction.Start -> SearchState(action.target, SearchStatus.SEARCHING, action.now)
        SearchAction.Found -> if (state.status == SearchStatus.SEARCHING) state.copy(status = SearchStatus.FOUND) else state
        SearchAction.Timeout -> if (state.status == SearchStatus.SEARCHING) state.copy(status = SearchStatus.NOT_FOUND) else state
        SearchAction.Stop -> state.copy(status = SearchStatus.STOPPED)
        SearchAction.Clear -> SearchState()
    }
}
/** Three hits in the last five unique, chronologically increasing frames . */
class StabilizeDetection {
    private val window = ArrayDeque<Pair<Long, Detection?>>()
    private var lastTimestamp = Long.MIN_VALUE
    fun reset() { window.clear(); lastTimestamp = Long.MIN_VALUE }
    fun accept(frame: DetectionFrame, target: Target, threshold: Float): Detection? {
        if (frame.timestamp <= lastTimestamp) return null
        lastTimestamp = frame.timestamp
        val hit = frame.detections.filter { it.label == target.label && it.score >= threshold }.maxByOrNull { it.score }
        window.addLast(frame.timestamp to hit)
        while (window.size > 5) window.removeFirst()
        return if (hit != null && window.count { it.second != null } >= 3) hit else null
    }
}
class EventCooldown(private val periodMs: Long = 5000) {
    private val last = mutableMapOf<String, Long>()
    fun allow(key: String, now: Long): Boolean {
        val prior = last[key]
        if (prior != null && now - prior < periodMs) return false
        last[key] = now
        return true
    }
}
