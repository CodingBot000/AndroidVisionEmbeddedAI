package dev.robotvision.debug
import dev.robotvision.domain.*
object DebugScenario {
    const val available = true
    fun frame(now: Long): DetectionFrame = DetectionFrame(now, 720, 960,
        listOf(Detection("cup", .94f, Box(.30f,.38f,.64f,.69f))), "SIMULATION", simulated = true)
    const val transcript = "컵 찾아줘"
}
