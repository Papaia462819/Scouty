package com.scouty.app.map

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scouty.app.utils.MapPackRegistryManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapPacksRuntimeTest {
    @Test
    fun installedMapPacksAreAvailableToRuntime() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val registry = MapPackRegistryManager.load(context)

        assertTrue("Romania base map pack is not ready.", registry.basePack().isReady)
        assertTrue("Bucegi high-detail map pack is not ready.", registry.demoPack().isReady)
        assertTrue("Local glyph assets are missing.", registry.hasLocalGlyphs)
        assertTrue("Romania base map pack is empty.", registry.basePack().sizeBytes > 0L)
        assertTrue("Bucegi high-detail map pack is empty.", registry.demoPack().sizeBytes > 0L)
    }
}
