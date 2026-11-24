package com.oamsapps.punchd.util

import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class RetryableTest {
    @Test fun successPassesThrough() = runBlocking {
        val out = retryable(flowOf(0), { "error" }) { flowOf("a", "b") }.toList()
        assertEquals(listOf("a", "b"), out)
    }

    @Test fun failureBecomesErrorValue_thenRetrySucceeds() = runBlocking {
        val retries = MutableStateFlow(0)
        var attempts = 0
        val f = retryable(retries, { "error: ${it.message}" }) {
            flow {
                attempts++
                if (attempts == 1) throw IOException("disk")
                emit("ok")
            }
        }
        val seen = mutableListOf<String>()
        val job = launch { f.collect { seen += it; if (it.startsWith("error")) retries.value = 1 } }
        withTimeout(2_000) { while (seen.size < 2) delay(5) }
        job.cancel()
        assertEquals(listOf("error: disk", "ok"), seen)
        assertEquals(2, attempts)
    }
}
