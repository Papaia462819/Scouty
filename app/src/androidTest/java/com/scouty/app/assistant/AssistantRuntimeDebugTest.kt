package com.scouty.app.assistant

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scouty.app.assistant.data.KnowledgePackManager
import com.scouty.app.assistant.domain.ModelManager
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantRuntimeDebugTest {

    @Test
    fun dumpKnowledgePackAndModelState() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val knowledgePackManager = KnowledgePackManager(context)
            val packStatus = knowledgePackManager.ensureReady()
            Log.d(LogTag, "packStatus=$packStatus isReady=${packStatus.isReady}")

            val databaseFile = File(context.noBackupFilesDir, "knowledge_pack/knowledge_pack.sqlite")
            Log.d(
                LogTag,
                "database exists=${databaseFile.exists()} size=${databaseFile.length()} path=${databaseFile.absolutePath}"
            )
            if (databaseFile.exists()) {
                SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
                    val integrityRows = buildList {
                        database.rawQuery("PRAGMA integrity_check", emptyArray()).use { cursor ->
                            while (cursor.moveToNext()) {
                                add(cursor.getString(0))
                            }
                        }
                    }
                    Log.d(LogTag, "integrityRows=$integrityRows")

                    val queryRows = buildList {
                        database.rawQuery(
                            """
                            SELECT kc.chunk_id, kc.domain, kc.language, kc.title
                            FROM knowledge_chunks kc
                            JOIN knowledge_chunks_fts fts ON kc.row_id = fts.rowid
                            WHERE knowledge_chunks_fts MATCH ?
                            ORDER BY kc.language, kc.domain, kc.title
                            """.trimIndent(),
                            arrayOf("focul* OR foc*")
                        ).use { cursor ->
                            while (cursor.moveToNext()) {
                                add(
                                    listOf(
                                        cursor.getString(0),
                                        cursor.getString(1),
                                        cursor.getString(2),
                                        cursor.getString(3)
                                    ).joinToString("|")
                                )
                            }
                        }
                    }
                    Log.d(LogTag, "ftsRows=$queryRows")
                }
            }

            val internalModelDir = File(context.noBackupFilesDir, "models/gemma-3-1b")
            val internalDirListing = internalModelDir.listFiles()?.map { file ->
                "${file.name}:${file.length()}:${file.canRead()}"
            }
            Log.d(
                LogTag,
                "internalModelDir=${internalModelDir.absolutePath} exists=${internalModelDir.exists()} " +
                    "canRead=${internalModelDir.canRead()} canExecute=${internalModelDir.canExecute()} " +
                    "listing=$internalDirListing"
            )

            val externalModelDir = context.getExternalFilesDir(null)?.let { File(it, "models/gemma-3-1b") }
            val externalDirListing = externalModelDir?.listFiles()?.map { file ->
                "${file.name}:${file.length()}:${file.canRead()}"
            }
            Log.d(
                LogTag,
                "externalModelDir=${externalModelDir?.absolutePath} exists=${externalModelDir?.exists()} " +
                    "canRead=${externalModelDir?.canRead()} canExecute=${externalModelDir?.canExecute()} " +
                    "listing=$externalDirListing"
            )

            val preferredPaths = PreferredModelNames.map { name ->
                externalModelDir?.let { dir ->
                    val file = File(dir, name)
                    "${file.name}:exists=${file.exists()}:len=${file.length()}:canRead=${file.canRead()}"
                }
            }
            Log.d(LogTag, "preferredModelPaths=$preferredPaths")

            val modelManager = ModelManager(context)
            val modelStatus = modelManager.refreshStatus()
            Log.d(LogTag, "modelStatus=$modelStatus generationMode=${modelManager.currentGenerationMode()}")
            if (modelStatus.availableOnDisk) {
                val loadedStatus = modelManager.ensureLoaded()
                Log.d(LogTag, "loadedModelStatus=$loadedStatus generationMode=${modelManager.currentGenerationMode()}")
            }
        }
    }

    private companion object {
        private const val LogTag = "ScoutyRuntimeTest"
        private val PreferredModelNames = listOf(
            "gemma-3-1b-it-int4.task",
            "gemma3-1b-it-int4.task",
            "gemma-3-1b-it.task",
            "gemma3-1b-it.task",
            "gemma-3-1b.task",
            "gemma3-1b.task",
            "gemma-3-1b-it-int4.litertlm",
            "gemma3-1b-it-int4.litertlm",
            "gemma-3-1b-it.litertlm",
            "gemma3-1b-it.litertlm",
            "gemma-3-1b.litertlm",
            "gemma3-1b.litertlm"
        )
    }
}
