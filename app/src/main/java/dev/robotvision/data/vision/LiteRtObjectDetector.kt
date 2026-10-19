package dev.robotvision.data.vision

import android.graphics.Bitmap
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import dev.robotvision.domain.EfficientDetOutputParser
import dev.robotvision.domain.Detection
import java.io.File

class LiteRtObjectDetector(file: File, private val labels: List<String>) : VisionDetector {
    override val backend = "LiteRT CompiledModel 2.3.0 / CPU"
    private val environment = Environment.create()
    private var model: CompiledModel? = null
    private var inputs = emptyList<TensorBuffer>()
    private var outputs = emptyList<TensorBuffer>()
    private val pixels = IntArray(320 * 320)
    private val rgb = ByteArray(320 * 320 * 3)
    init {
        try {
            model = CompiledModel.create(file.absolutePath, CompiledModel.Options(Accelerator.CPU), environment)
            inputs = model!!.createInputBuffers(); outputs = model!!.createOutputBuffers()
            check(inputs.size == 1 && outputs.size == 4) { "Unexpected EfficientDet tensor contract" }
            // The pinned model has dynamic outputs. Never run into a scalar allocation: native
            // Detection_PostProcess can overrun it instead of throwing a Kotlin exception.
            EfficientDetOutputParser.requireCapacity(inputs[0].readInt8().size, outputs.map { it.readFloat().size })
            infer() // Only a statically sufficient allocation may execute warm-up.
        } catch (e: Throwable) { close(); throw e }
    }
    override fun detect(bitmap: Bitmap): List<Detection> {
        val scaled = Bitmap.createScaledBitmap(bitmap, 320, 320, true)
        try { scaled.getPixels(pixels, 0, 320, 0, 0, 320, 320) }
        finally { if (scaled !== bitmap) scaled.recycle() }
        pixels.forEachIndexed { i, p ->
            rgb[i * 3] = (p shr 16 and 255).toByte()
            rgb[i * 3 + 1] = (p shr 8 and 255).toByte()
            rgb[i * 3 + 2] = (p and 255).toByte()
        }
        return infer()
    }
    private fun infer(): List<Detection> {
        inputs[0].writeInt8(rgb) // uint8 RGB storage, exact bit pattern; not signed pixel arithmetic.
        model!!.run(inputs, outputs)
        // Pinned detection-metadata-1 signature: boxes, classes, scores, count.
        val boxes = outputs[0].readFloat(); val classes = outputs[1].readFloat()
        val scores = outputs[2].readFloat(); val count = outputs[3].readFloat().first().toInt()
        return EfficientDetOutputParser.parse(boxes, classes, scores, count.toFloat(), labels)
    }

    override fun close() {
        (inputs + outputs).forEach { runCatching { it.close() } }; inputs = emptyList(); outputs = emptyList()
        model?.close(); model = null; environment.close()
    }
}
