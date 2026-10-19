package dev.robotvision.domain

import kotlinx.coroutines.flow.Flow

data class Target(val label: String, val korean: String, val aliases: List<String>)
object Targets {
    val all = listOf(
        Target("cup", "컵", listOf("컵", "커피잔", "찻잔", "cup")),
        Target("bottle", "물병", listOf("물병", "병", "bottle")),
        Target("person", "사람", listOf("사람", "person")),
        Target("chair", "의자", listOf("의자", "chair")),
        Target("laptop", "노트북", listOf("노트북", "랩톱", "laptop")),
        Target("keyboard", "키보드", listOf("키보드", "keyboard")),
        Target("book", "책", listOf("책", "book")),
        Target("cell phone", "휴대폰", listOf("휴대폰", "핸드폰", "스마트폰", "cellphone")),
    )
    fun byLabel(label: String?) = all.find { it.label == label }
}
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float)
data class Detection(val label: String, val score: Float, val box: Box)
data class DetectionFrame(
    val timestamp: Long, val width: Int, val height: Int, val detections: List<Detection>,
    val backend: String, val inferenceMs: Double? = null, val totalMs: Double? = null,
    val simulated: Boolean = false,
)
data class Event(
    val id: Long = 0, val time: Long, val type: String, val label: String? = null,
    val score: Float? = null, val backend: String = "--", val latencyMs: Double? = null,
    val detail: String = "", val simulated: Boolean = false,
)
interface EventRepository {
    val events: Flow<List<Event>>
    suspend fun save(event: Event)
    suspend fun clear()
}
interface Clock { fun now(): Long; fun wallTime(): Long }
data class Settings(val confidence: Float = .5f, val haptic: Boolean = true)
interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun setConfidence(value: Float)
    suspend fun setHaptic(value: Boolean)
}
sealed interface VoiceCommand {
    data class Find(val target: Target) : VoiceCommand
    data object Stop : VoiceCommand
    data object Retry : VoiceCommand
    data object Clear : VoiceCommand
    data class Unsupported(val text: String) : VoiceCommand
    data class Unknown(val text: String) : VoiceCommand
}
interface SpeechRecognizerEngine {
    suspend fun load()
    suspend fun transcribe(samples: FloatArray): String
    suspend fun close()
}
interface AudioInput {
    suspend fun capture(onStarted: () -> Unit): FloatArray
    fun stop()
    fun cancel()
}
