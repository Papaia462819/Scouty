package com.scouty.app.assistant

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scouty.app.assistant.domain.AssistantRepository
import com.scouty.app.assistant.model.DeviceContextSnapshot
import com.scouty.app.assistant.model.GenerationMode
import com.scouty.app.assistant.model.ModelRuntimeState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantRepositoryRuntimeTest {

    @Test
    fun answerCampfireQuery() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val repository = AssistantRepository(context)
            val response = repository.answer(
                query = "cum fac focul",
                context = DeviceContextSnapshot(
                    batteryPercent = 67,
                    batterySafe = false,
                    isOnline = false,
                    gpsFixed = false,
                    localeTag = "ro-RO"
                )
            )

            Log.d(
                LogTag,
                "response generationMode=${response.generationMode} " +
                    "modelState=${response.modelRuntimeState} citations=${response.citations.map { "${it.sourceTitle}|${it.sectionTitle}" }}"
            )
            Log.d(LogTag, "response text=${response.answerText.replace("\n", "\\n").take(3000)}")

            assertEquals(GenerationMode.LOCAL_LLM, response.generationMode)
            assertEquals(ModelRuntimeState.LOADED, response.modelRuntimeState)
            assertTrue(response.answerText.contains("foc", ignoreCase = true))
            assertFalse(response.answerText.contains("Raspuns prudent", ignoreCase = true))
            assertFalse(response.answerText.contains("Cautious answer", ignoreCase = true))
            assertTrue(
                response.citations.any {
                    it.sourceTitle.contains("Campfires") &&
                        it.sectionTitle.contains("Foc de tabara", ignoreCase = true)
                }
            )
            assertFalse(
                response.citations.any {
                    it.sourceTitle.contains("Circuitul Galbenei", ignoreCase = true) ||
                        it.sectionTitle.contains("Galbenei", ignoreCase = true)
                }
            )
        }
    }

    @Test
    fun romanianTypoQuery_staysRomanian() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val repository = AssistantRepository(context)
            val response = repository.answer(
                query = "Am facut o entorsa, cuim sa procedez?",
                context = DeviceContextSnapshot(
                    batteryPercent = 67,
                    batterySafe = false,
                    isOnline = false,
                    gpsFixed = false,
                    localeTag = "ro-RO"
                )
            )

            Log.d(LogTag, "romanianTypo response=${response.answerText.replace("\n", "\\n").take(3000)}")
            assertEquals(GenerationMode.LOCAL_LLM, response.generationMode)
            assertEquals(ModelRuntimeState.LOADED, response.modelRuntimeState)
            assertFalse(response.answerText.contains("what should", ignoreCase = true))
            assertFalse(response.answerText.contains("call salvamont", ignoreCase = true))
            assertTrue(
                response.answerText.contains("salvamont", ignoreCase = true) ||
                    response.answerText.contains("procede", ignoreCase = true) ||
                    response.answerText.contains("glezna", ignoreCase = true) ||
                    response.answerText.contains("entorsa", ignoreCase = true)
            )
        }
    }

    private companion object {
        private const val LogTag = "ScoutyRepositoryTest"
    }
}
