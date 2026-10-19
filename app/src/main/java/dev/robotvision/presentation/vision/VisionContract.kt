package dev.robotvision.presentation.vision

import dev.robotvision.domain.*
import dev.robotvision.domain.Target
import dev.robotvision.data.vision.EngineState

enum class ListeningStatus { IDLE, PREPARING, RECORDING, TRANSCRIBING }
data class VisionUiState(
    val search: SearchState = SearchState(), val engine: EngineState = EngineState(),
    val listening: ListeningStatus = ListeningStatus.IDLE, val speechReady: Boolean = false,
    val cameraStatus: String = "IDLE", val cameraPermission: Boolean = false, val microphonePermission: Boolean = false,
    val transcript: String = "", val message: String? = null, val settings: Settings = Settings(),
    val simulation: Boolean = false, val modelDescription: String = "Loading manifest…", val hashes: String = "--",
)
data class OverlayState(val frame: DetectionFrame? = null)
sealed interface VisionIntent {
    data class SelectTarget(val label: String) : VisionIntent
    data object StopSearch : VisionIntent
    data object RetrySearch : VisionIntent
    data object ClearTarget : VisionIntent
    data object StartListening : VisionIntent
    data object StopListening : VisionIntent
    data object CancelListening : VisionIntent
    data object ReloadEngine : VisionIntent
    data object Background : VisionIntent
    data object Foreground : VisionIntent
    data class Permissions(val camera: Boolean, val microphone: Boolean) : VisionIntent
    data class CameraStatus(val value: String) : VisionIntent
    data class SetConfidence(val value: Float) : VisionIntent
    data class SetHaptic(val value: Boolean) : VisionIntent
    data object ClearHistory : VisionIntent
    data object ToggleSimulation : VisionIntent
}
sealed interface VisionEffect {
    data class Snackbar(val message: String) : VisionEffect
    data object TargetFound : VisionEffect
}
