package com.scouty.app.assistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scouty.app.assistant.domain.LocalLlmGenerationOptions
import com.scouty.app.assistant.domain.LocalLlmSamplerParams
import com.scouty.app.assistant.domain.ModelManager
import com.scouty.app.assistant.domain.RuntimeFeatureFlags
import com.scouty.app.assistant.model.ModelRuntimeState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LlamaCppRuntimeDebugTest {
    @Test
    fun qwenBundle_loadsAndGeneratesRomanian() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = ModelManager(context, RuntimeFeatureFlags(useLlamaCpp = true))
        val discovered = manager.refreshStatus()
        assumeTrue("Qwen GGUF bundle is not installed on this device.", discovered.availableOnDisk)

        val loaded = manager.ensureLoaded()
        assertEquals(ModelRuntimeState.LOADED, loaded.state)

        val prompt = """
            Raspunde doar in romana, in maximum doua propozitii.
            Intrebare: Cum aprind un foc cu lemne ude?
        """.trimIndent()

        val response = manager.generate(
            prompt,
            LocalLlmGenerationOptions(
                sampler = LocalLlmSamplerParams(
                    maxTokens = 50,
                    temperature = 0.1f,
                    topK = 20,
                    topP = 0.9f,
                    randomSeed = 7
                )
            )
        )

        assertTrue(response.text.length >= 40)
        assertTrue(
            "Expected Romanian fire-starting answer, got: ${response.text}",
            response.text.contains("lemn", ignoreCase = true) ||
                response.text.contains("foc", ignoreCase = true) ||
                response.text.contains("aprind", ignoreCase = true)
        )
    }
}
