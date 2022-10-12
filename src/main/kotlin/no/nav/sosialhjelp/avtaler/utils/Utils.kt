package no.nav.sosialhjelp.avtaler.utils

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal const val DEFAULT_RETRY_ATTEMPTS = 5
internal const val DEFAULT_DELAY_DURATION = 500L

object Utils {
    suspend fun <T> retry(numberOfTries: Int = DEFAULT_RETRY_ATTEMPTS, interval: Duration = DEFAULT_DELAY_DURATION.milliseconds, block: suspend () -> T): T {
        var attempt = 0
        var error: Throwable?
        do {
            try {
                return block()
            } catch (e: Throwable) {
                error = e
            }
            attempt++
            delay(interval)
        } while (attempt < numberOfTries)

        throw error ?: IllegalStateException("Retry failed without error")
    }
}
