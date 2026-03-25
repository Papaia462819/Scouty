package com.scouty.app.assistant.domain

import com.scouty.app.assistant.model.GenerationMode
import com.scouty.app.assistant.model.ModelRuntimeState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModelManagerTest {
    @Test
    fun missingBundle_reportsMissingAndFallbackMode() = runBlocking {
        val manager = ModelManager(
            modelLocator = FakeLocalModelLocator(
                discovery = LocalModelDiscovery(
                    details = "missing bundle"
                )
            ),
            runtimeAdapter = FakeRuntimeAdapter()
        )

        val status = manager.ensureLoaded()

        assertEquals(ModelRuntimeState.MISSING, status.state)
        assertEquals(GenerationMode.FALLBACK_STRUCTURED, manager.currentGenerationMode())
    }

    @Test
    fun availableBundle_preparesLoadsAndUnloads() = runBlocking {
        val tempDir = Files.createTempDirectory("scouty-model-test").toFile()
        val sourceFile = File(tempDir, "gemma-3-1b-it-int4.task").apply { writeText("bundle") }
        val preparedFile = File(tempDir, "prepared.task")
        val locator = FakeLocalModelLocator(
            discovery = LocalModelDiscovery(
                modelVersion = "gemma-3-1b-it-int4",
                availableOnDisk = true,
                needsPrepare = true,
                sourceFile = sourceFile,
                preparedFile = preparedFile,
                details = "ready"
            ),
            preparedArtifact = LocalModelArtifact(
                modelVersion = "gemma-3-1b-it-int4",
                sourceFile = sourceFile,
                preparedFile = preparedFile
            )
        )
        val runtimeAdapter = FakeRuntimeAdapter(response = "{\"summary\":\"ok\",\"sections\":[{\"title\":\"A\",\"body\":\"B\",\"style\":\"GUIDANCE\"}]}")
        val manager = ModelManager(locator, runtimeAdapter)

        val loaded = manager.ensureLoaded()
        val generated = manager.generate("prompt")
        val unloaded = manager.unloadModel()

        assertEquals(ModelRuntimeState.LOADED, loaded.state)
        assertTrue(preparedFile.exists())
        assertEquals("{\"summary\":\"ok\",\"sections\":[{\"title\":\"A\",\"body\":\"B\",\"style\":\"GUIDANCE\"}]}", generated.text)
        assertEquals(ModelRuntimeState.UNLOADED, unloaded.state)
    }

    @Test
    fun runtimeLoadFailure_marksFailedState() = runBlocking {
        val tempDir = Files.createTempDirectory("scouty-model-failure").toFile()
        val modelFile = File(tempDir, "gemma-3-1b-it-int4.task").apply { writeText("bundle") }
        val manager = ModelManager(
            modelLocator = FakeLocalModelLocator(
                discovery = LocalModelDiscovery(
                    modelVersion = "gemma-3-1b-it-int4",
                    availableOnDisk = true,
                    sourceFile = modelFile,
                    preparedFile = modelFile,
                    details = "ready"
                ),
                preparedArtifact = LocalModelArtifact(
                    modelVersion = "gemma-3-1b-it-int4",
                    sourceFile = modelFile,
                    preparedFile = modelFile
                )
            ),
            runtimeAdapter = FakeRuntimeAdapter(loadError = IllegalStateException("boom"))
        )

        val status = manager.ensureLoaded()

        assertEquals(ModelRuntimeState.FAILED, status.state)
        assertTrue(status.lastError?.contains("boom") == true)
    }
}

class FakeLocalModelLocator(
    private val discovery: LocalModelDiscovery,
    private val preparedArtifact: LocalModelArtifact? = null
) : LocalModelLocator {
    override suspend fun inspect(): LocalModelDiscovery = discovery

    override suspend fun prepare(discovery: LocalModelDiscovery): LocalModelArtifact {
        val artifact = preparedArtifact ?: error("preparedArtifact required")
        if (artifact.preparedFile.absolutePath != artifact.sourceFile.absolutePath) {
            artifact.sourceFile.copyTo(artifact.preparedFile, overwrite = true)
        }
        return artifact
    }
}

class FakeRuntimeAdapter(
    private val response: String = "ok",
    private val loadError: Throwable? = null
) : LocalLlmRuntimeAdapter {
    override val runtimeLabel: String = "Fake runtime"

    override suspend fun load(artifact: LocalModelArtifact): LocalLlmLoadedModelHandle {
        loadError?.let { throw it }
        return object : LocalLlmLoadedModelHandle {
            override suspend fun generate(prompt: String): String = response

            override suspend fun close() = Unit
        }
    }
}
