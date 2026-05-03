package com.scouty.app.tracks.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

class TrackModelAssets(
    private val context: Context,
) {
    fun ensureModelReady(): File {
        val targetDir = File(context.filesDir, "ml").apply { mkdirs() }
        val target = File(targetDir, MODEL_FILE_NAME)
        if (!target.exists() || sha256(target) != MODEL_SHA256) {
            context.assets.open(ASSET_PATH).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val actualHash = sha256(target)
        require(actualHash == MODEL_SHA256) {
            "Track model checksum mismatch. Expected $MODEL_SHA256, got $actualHash"
        }
        return target
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MODEL_FILE_NAME = "track_model_v1.onnx"
        const val ASSET_PATH = "ml/$MODEL_FILE_NAME"
        const val MODEL_SHA256 = "25cb2b16e07e21890447da99347240caad3f1b85790e2a902a6f8eae305e0cd8"
    }
}
