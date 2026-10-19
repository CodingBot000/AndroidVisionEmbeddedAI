package dev.robotvision.data.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.robotvision.di.IoDispatcher
import dev.robotvision.domain.AudioInput
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AudioRecordInput @Inject constructor(
    @param:ApplicationContext private val context: Context, @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
) : AudioInput {
    private val recording = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    @Volatile private var active: AudioRecord? = null
    override suspend fun capture(onStarted: () -> Unit): FloatArray = withContext(dispatcher) {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "마이크 권한이 필요합니다." }
        check(recording.compareAndSet(false, true)) { "녹음이 이미 진행 중입니다." }
        cancelled.set(false)
        val samples = FloatArray(16000 * 8)
        var recorder: AudioRecord? = null
        try {
            val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0) { "16 kHz 마이크 입력을 지원하지 않습니다." }
            recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, 6400))
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "오디오 장치를 열 수 없습니다." }
            active = recorder
            if (!recording.get() || cancelled.get()) return@withContext FloatArray(0)
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "녹음을 시작하지 못했습니다." }
            onStarted()
            val buffer = ShortArray(1600); var count = 0
            while (recording.get() && count < samples.size) {
                currentCoroutineContext().ensureActive()
                val n = recorder.read(buffer, 0, minOf(buffer.size, samples.size - count), AudioRecord.READ_BLOCKING)
                if (n < 0) { check(!recording.get()) { "마이크 읽기 실패 ($n)" }; break }
                for (i in 0 until n) samples[count++] = buffer[i] / 32768f
            }
            if (cancelled.get()) FloatArray(0) else samples.copyOf(count)
        } finally {
            active = null; recording.set(false)
            recorder?.let { runCatching { it.stop() }; it.release() }
            samples.fill(0f)
        }
    }
    override fun stop() { recording.set(false); active?.let { runCatching { it.stop() } } }
    override fun cancel() { cancelled.set(true); stop() }
}
