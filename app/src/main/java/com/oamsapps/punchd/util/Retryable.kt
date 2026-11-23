package com.oamsapps.punchd.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Collects [block]'s flow, turning a failure into [onError]'s value instead of crashing the screen.
 * Every new value of [retries] (e.g. a Retry tap) starts [block] again from scratch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> retryable(retries: Flow<Int>, onError: (Throwable) -> T, block: () -> Flow<T>): Flow<T> =
    retries.flatMapLatest {
        block().catch { e ->
            if (e is CancellationException) throw e
            emit(onError(e))
        }
    }
