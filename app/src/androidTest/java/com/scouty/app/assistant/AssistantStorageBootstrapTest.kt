package com.scouty.app.assistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantStorageBootstrapTest {

    @Test
    fun ensureModelDirectoriesExist() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.noBackupFilesDir, "models/qwen-2.5-1.5b").mkdirs()
        context.getExternalFilesDir(null)?.let { externalRoot ->
            File(externalRoot, "models/qwen-2.5-1.5b").mkdirs()
        }
    }
}
