package com.jochana.driverule.core.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jochana.driverule.BuildConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only live check. Enable explicitly with runner argument backendSmoke=true. */
@RunWith(AndroidJUnit4::class)
class BackendPublicApiSmokeTest {
    @Test
    fun publicGetsAndConditionalManifestWorkFromTheApp() = runBlocking {
        assumeTrue("Explicit opt-in required for live backend requests",
            InstrumentationRegistry.getArguments().getString("backendSmoke") == "true")
        BackendApiClient.create(BuildConfig.API_BASE_URL).use { client ->
            val sync = client.syncCards()
            assertTrue("Card sync must return HTTP success; got ${sync::class.simpleName}",
                sync is BackendResult.Success)
            sync as BackendResult.Success
            assertEquals(200, sync.statusCode)
            assertTrue(sync.value.fullSync)
            assertFalse(sync.value.syncToken.isBlank())

            val manifest = client.schoolZoneManifest()
            assertTrue("Manifest must return HTTP success; got ${manifest::class.simpleName}",
                manifest is BackendResult.Success)
            manifest as BackendResult.Success
            assertEquals(200, manifest.statusCode)
            assertEquals("school-zones", manifest.value.dataset)
            assertEquals(1, manifest.value.schema)
            assertTrue(manifest.value.count >= 0)
            assertTrue(manifest.value.sha256.matches(Regex("[0-9a-f]{64}")))
            val etag = requireNotNull(manifest.etag)
            assertFalse(etag.isBlank())
            assertTrue(client.schoolZoneManifest(etag) is BackendResult.NotModified)
        }
    }
}
