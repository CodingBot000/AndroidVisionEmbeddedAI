package dev.robotvision
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.robotvision.data.modelassets.ModelAssetManager
import dev.robotvision.data.speech.SherpaOnnxSpeechRecognizer
import dev.robotvision.data.vision.VisionController
import dev.robotvision.domain.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
@RunWith(AndroidJUnit4::class)
class NativeEngineTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val assets=ModelAssetManager(context)
    private val clock=object:Clock { override fun now()=android.os.SystemClock.elapsedRealtime();override fun wallTime()=System.currentTimeMillis() }
    @Test fun realVisionNativeInference()= runBlocking {
        val controller=VisionController(context,assets,clock)
        val bitmap=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888)
        try {
            controller.load()
            val status=controller.state.value
            android.util.Log.i("RobotVisionTest","Vision backend=${status.backend}; fallback=${status.reason}; error=${status.error}")
            assertTrue(status.error ?: "Vision not ready",status.ready)
            val frame=async(start=CoroutineStart.UNDISPATCHED) { controller.frames.first() }
            controller.process(bitmap,clock.now(),System.nanoTime())
            val result=withTimeout(30_000) {frame.await()}
            assertFalse(result.simulated);assertNotNull(result.inferenceMs);assertEquals(320,result.width)
        } finally {bitmap.recycle();controller.close()}
    }
    @Test fun realKoreanFileTranscription()= runBlocking {
        val engine=SherpaOnnxSpeechRecognizer(assets)
        val pcm=InstrumentationRegistry.getInstrumentation().context.assets.open("korean16k.pcm").use {it.readBytes()}
        val bytes=ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        val samples=FloatArray(pcm.size/2) {bytes.short/32768f}
        try {
            val text=engine.transcribe(samples)
            android.util.Log.i("RobotVisionTest","Official Korean test wav transcription=$text")
            assertTrue("Expected Korean reference words, got $text", text.contains("조국"))
        } finally {samples.fill(0f);engine.close()}
    }
}
