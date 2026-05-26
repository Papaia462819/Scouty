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
    fun onlineMapSourceIsAvailableToRuntime() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val registry = MapPackRegistryManager.load(context, isOnline = true)

        assertTrue("Remote Romania map source is not ready.", registry.basePack().isReady)
        assertTrue("Remote PMTiles source is not configured.", registry.activeSourceUri?.startsWith("pmtiles://https://") == true)
        assertTrue("Local glyph assets are missing.", registry.hasLocalGlyphs)
    }
}
