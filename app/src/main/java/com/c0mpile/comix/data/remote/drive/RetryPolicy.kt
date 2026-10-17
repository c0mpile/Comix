package com.c0mpile.comix.data.remote.drive

import com.c0mpile.comix.domain.model.ComicError
import java.io.IOException
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response

/**
 * Handles exponential backoff with random jitter and HTTP error translation for Google Drive REST API calls.
 */
class RetryPolicy(
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val initialDelayMillis: Long = DEFAULT_INITIAL_DELAY_MS,
    val maxDelayMillis: Long = DEFAULT_MAX_DELAY_MS,
    val delayFn: suspend (Long) -> Unit = { delay(it) },
    val randomFn: (Long) -> Long = { max -> if (max > 0) Random.nextLong(max) else 0L }
) {
    @Inject
    constructor() : this(
        maxRetries = DEFAULT_MAX_RETRIES,
        initialDelayMillis = DEFAULT_INITIAL_DELAY_MS,
        maxDelayMillis = DEFAULT_MAX_DELAY_MS
    )

    companion object {
        const val DEFAULT_MAX_RETRIES: Int = 3
        const val DEFAULT_INITIAL_DELAY_MS: Long = 500L
        const val DEFAULT_MAX_DELAY_MS: Long = 8_000L

        private val RATE_LIMIT_REASONS = setOf(
            "userRateLimitExceeded",
            "rateLimitExceeded",
            "quotaExceeded",
            "sharingRateLimitExceeded"
        )
    }

    /**
     * Executes [requestFn], automatically retrying with exponential backoff and jitter on rate limits (429 or rate-limit 403).
     *
     * Non-rate-limit HTTP errors are mapped to [DriveApiException].
     * Network I/O exceptions are mapped to [ComicError.NetworkUnavailable].
     */
    suspend fun execute(requestFn: suspend () -> Response): Response {
        var attempt = 0
        while (true) {
            val response = try {
                requestFn()
            } catch (e: DriveApiException) {
                throw e
            } catch (e: IOException) {
                throw DriveApiException(ComicError.NetworkUnavailable, cause = e)
            }

            if (response.isSuccessful || response.code == 401) {
                return response
            }

            val code = response.code
            val bodyString = try {
                response.peekBody(65536).string()
            } catch (_: Exception) {
                null
            }

            val isRateLimited = isRateLimit(code, bodyString)
            if (isRateLimited) {
                val retryAfterHeader = response.header("Retry-After")
                val delayMillis = computeDelayMillis(attempt, retryAfterHeader)
                response.close()

                if (attempt >= maxRetries) {
                    throw DriveApiException(ComicError.RateLimited(delayMillis))
                }

                delayFn(delayMillis)
                attempt++
            } else {
                val errorDetail = parseErrorMessage(bodyString)
                response.close()
                throw DriveApiException(
                    ComicError.DriveApiError(code, errorDetail)
                )
            }
        }
    }

    /**
     * Returns true if the HTTP response code and body indicate a Google Drive rate-limit error.
     */
    fun isRateLimit(code: Int, bodyString: String?): Boolean {
        if (code == 429) return true
        if (code == 403 && bodyString != null) {
            return RATE_LIMIT_REASONS.any { reason -> bodyString.contains(reason) }
        }
        return false
    }

    /**
     * Calculates the retry delay in milliseconds, prioritizing the `Retry-After` response header if present.
     */
    fun computeDelayMillis(attempt: Int, retryAfterHeader: String?): Long {
        val retryAfterSeconds = retryAfterHeader?.trim()?.toLongOrNull()
        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            return retryAfterSeconds * 1000L
        }
        val exp = 1L shl attempt.coerceIn(0, 30)
        val base = (initialDelayMillis * exp).coerceAtMost(maxDelayMillis)
        val jitter = randomFn(base / 2)
        return (base + jitter).coerceAtMost(maxDelayMillis)
    }

    private fun parseErrorMessage(bodyString: String?): String? {
        if (bodyString.isNullOrBlank()) return null
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(bodyString).jsonObject
            root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }
}
