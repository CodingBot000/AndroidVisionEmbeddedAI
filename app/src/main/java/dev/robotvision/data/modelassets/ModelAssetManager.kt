package dev.robotvision.data.modelassets

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton class ModelAssetManager @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val manifest by lazy { context.assets.open("models/model-manifest.json").bufferedReader().use { JSONObject(it.readText()) } }
    fun description(): String = "${manifest.getJSONObject("vision").getString("id")} / ${manifest.getJSONObject("speech").getString("id")}"
    fun hashSummary(): String = manifest.getJSONArray("files").let { files ->
        (0 until files.length()).joinToString("\n") { i -> files.getJSONObject(i).let {
            "${it.getString("asset")}: ${it.getString("sha256").take(12)}"
        } }
    }
    /** Called only on engine worker threads. Validate the exact bundled asset before each native load. */
    @Synchronized fun requireFile(name: String): File {
        val entries = manifest.getJSONArray("files")
        val entry = (0 until entries.length()).map { entries.getJSONObject(it) }.first { it.getString("asset") == name }
        val expected = entry.getString("sha256")
        val directory = File(context.noBackupFilesDir, "models/$expected").apply { mkdirs() }
        val file = File(directory, name)
        if (file.exists() && file.length() == entry.getLong("size") && sha(file) == expected) return file
        val temp = File(directory, "$name.partial")
        try {
            context.assets.open("models/$name").use { input -> temp.outputStream().use(input::copyTo) }
            check(temp.length() == entry.getLong("size") && sha(temp) == expected) { "MODEL_UNAVAILABLE: integrity check failed for $name" }
            check(temp.renameTo(file)) { "Cannot install $name in private model directory" }
            return file
        } finally { temp.delete() }
    }
    private fun sha(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
