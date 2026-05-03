package com.scouty.app.tracks.data

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackModelAssetsMetadataTest {
    private val assetRoot = listOf(
        File("app/src/main/scouty_assets/ml"),
        File("src/main/scouty_assets/ml"),
    ).first { it.exists() }

    @Test
    fun metadataHashMatchesPackagedModelAndRuntimeConstant() {
        val model = File(assetRoot, TrackModelAssets.MODEL_FILE_NAME)
        val metadata = File(assetRoot, "track_model_metadata.json").readText(charset = Charsets.UTF_8)
        val actualHash = sha256(model)

        assertEquals(TrackModelAssets.MODEL_FILE_NAME, metadata.jsonString("asset_name"))
        assertEquals(TrackModelAssets.MODEL_SHA256, metadata.jsonString("sha256"))
        assertEquals(TrackModelAssets.MODEL_SHA256, actualHash)
    }

    @Test
    fun calibrationThresholdsMatchConfidencePolicyDefaults() {
        val calibration = File(assetRoot, "track_model_calibration.json").readText(charset = Charsets.UTF_8)

        assertEquals(0.70f, calibration.jsonNumber("probabil"))
        assertEquals(0.40f, calibration.jsonNumber("posibil"))
        assertEquals(0.08f, calibration.jsonNumber("ambiguous_margin"))
    }

    @Test
    fun metadataDocumentsSevenClassYoloOutput() {
        val metadata = File(assetRoot, "track_model_metadata.json").readText(charset = Charsets.UTF_8)

        TrackPostprocessorClassNames.forEach { className ->
            assertTrue("Missing class in metadata: $className", metadata.contains("\"$className\""))
        }
        assertTrue(
            Regex("\"shape\"\\s*:\\s*\\[\\s*1\\s*,\\s*11\\s*,\\s*8400\\s*]")
                .containsMatchIn(metadata),
        )
    }

    private fun String.jsonString(key: String): String {
        return Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"")
            .find(this)
            ?.groupValues
            ?.get(1)
            ?: error("Missing JSON string key: $key")
    }

    private fun String.jsonNumber(key: String): Float {
        return Regex("\"$key\"\\s*:\\s*([0-9.]+)")
            .find(this)
            ?.groupValues
            ?.get(1)
            ?.toFloat()
            ?: error("Missing JSON number key: $key")
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

    private companion object {
        val TrackPostprocessorClassNames = listOf(
            "bear_brown",
            "wolf_gray",
            "lynx_eurasian",
            "red_deer",
            "wild_boar",
            "red_fox",
            "dog_domestic",
        )
    }
}
