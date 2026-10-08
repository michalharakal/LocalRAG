package com.ayushig.localrag.android

import com.ayushig.localrag.android.internal.SkaiNetQueryEmbedder
import com.ayushig.localrag.core.bundle.EmbeddingInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkaiNetQueryEmbedderTest {

    private val matchingManifest = EmbeddingInfo(
        modelId = "skainet-hrp-v1",
        dimensions = 256,
        normalized = true,
        queryPrefix = "",
        documentPrefix = "",
    )

    @Test
    fun `creates against a matching manifest and embeds at the declared dimensions`() {
        val warnings = mutableListOf<String>()
        val embedder = SkaiNetQueryEmbedder.createIfCompatible(matchingManifest) { warnings += it }
        assertNotNull(embedder)
        embedder!!.use {
            val vector = it.embed("how do I deposit funds")
            assertNotNull(vector)
            assertEquals(256, vector!!.size)
        }
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `refuses a manifest from a different vector space`() {
        val warnings = mutableListOf<String>()
        val embedder = SkaiNetQueryEmbedder.createIfCompatible(
            matchingManifest.copy(modelId = "embeddinggemma-300m-seq256", dimensions = 768),
        ) { warnings += it }
        assertNull(embedder)
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("model id"))
        assertTrue(warnings.single().contains("dimensions"))
    }

    @Test
    fun `refuses prefixed vectors it would silently mis-embed`() {
        val embedder = SkaiNetQueryEmbedder.createIfCompatible(
            matchingManifest.copy(queryPrefix = "task: search result | query: "),
        ) { }
        assertNull(embedder)
    }
}
