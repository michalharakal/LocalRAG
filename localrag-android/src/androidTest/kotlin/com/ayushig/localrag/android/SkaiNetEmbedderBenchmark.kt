package com.ayushig.localrag.android

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayushig.localrag.embedder.skainet.SkaiNetHashedProjectionEmbedder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device timing for the SKaiNET query embedder: the number that matters is embed latency on
 * the retrieval path (one query per user question), with corpus-chunk latency as the bulk case.
 *
 * A deliberate timing loop rather than androidx.benchmark: the result feeds a PR discussion, not
 * a CI dashboard, and one less plugin keeps the module buildable everywhere. Medians over many
 * iterations after warmup are stable enough for that purpose.
 */
@RunWith(AndroidJUnit4::class)
class SkaiNetEmbedderBenchmark {

    // Length-representative of the demo corpus: ~60-token documentation chunk and a short query.
    private val chunkText =
        "title: Deposit funds | text: To deposit funds into your account, open the funds tab " +
            "and choose a payment method. Bank transfers settle within one business day, card " +
            "payments are instant but carry a conversion fee for non-euro accounts. Deposits " +
            "below the minimum amount are rejected by the payment provider and returned to the " +
            "source account within three business days."
    private val queryText = "how do I deposit funds into my account"

    @Test
    fun embedLatencyOnDevice() {
        SkaiNetHashedProjectionEmbedder(dimensions = 256).use { embedder ->
            // Warmup: JIT, first-touch allocations, SKaiNET context spin-up.
            repeat(WARMUP) {
                embedder.embed(chunkText)
                embedder.embed(queryText)
            }

            val queryMicros = measure(ITERATIONS) { embedder.embed(queryText) }
            val chunkMicros = measure(ITERATIONS) { embedder.embed(chunkText) }

            val vector = embedder.embed(queryText)
            assertEquals(256, vector.size)

            report("query (${queryText.length} chars)", queryMicros)
            report("chunk (${chunkText.length} chars)", chunkMicros)

            val corpusMillis = chunkMicros.median() * CORPUS_CHUNKS / 1000.0
            Log.i(TAG, "full ${CORPUS_CHUNKS}-chunk corpus at chunk median: ${"%.1f".format(corpusMillis)} ms")

            // The retrieval budget: a query embed must be UI-invisible. Generous bound so the
            // assertion only fails when something is genuinely wrong, not on a busy device.
            assertTrue(
                "query embed median ${queryMicros.median()} µs exceeds 50 ms",
                queryMicros.median() < 50_000,
            )
        }
    }

    private fun measure(iterations: Int, block: () -> Unit): LongArray {
        val samples = LongArray(iterations)
        for (i in 0 until iterations) {
            val start = System.nanoTime()
            block()
            samples[i] = (System.nanoTime() - start) / 1_000
        }
        return samples
    }

    private fun report(label: String, micros: LongArray) {
        val sorted = micros.sorted()
        Log.i(
            TAG,
            "$label: median ${sorted[sorted.size / 2]} µs, " +
                "p90 ${sorted[(sorted.size * 9) / 10]} µs, " +
                "min ${sorted.first()} µs over ${micros.size} iterations",
        )
    }

    private fun LongArray.median(): Long = sorted()[size / 2]

    private companion object {
        const val TAG = "SkaiNetBench"
        const val WARMUP = 50
        const val ITERATIONS = 300
        const val CORPUS_CHUNKS = 53
    }
}
