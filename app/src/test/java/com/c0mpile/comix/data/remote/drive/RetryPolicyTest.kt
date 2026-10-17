package com.c0mpile.comix.data.remote.drive

import com.c0mpile.comix.domain.model.ComicError
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RetryPolicy] handling exponential backoff, jitter, and error translation.
 */
class RetryPolicyTest {

    private fun createResponse(
        code: Int,
        body: String = "",
        headers: Map<String, String> = emptyMap()
    ): Response {
        val builder = Response.Builder()
            .request(Request.Builder().url("https://www.googleapis.com/drive/v3/files").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body(body.toResponseBody("application/json".toMediaType()))

        for ((key, value) in headers) {
            builder.addHeader(key, value)
        }
        return builder.build()
    }

    @Test
    fun immediate200OkSucceedsOnFirstAttemptWithZeroDelays() = runTest {
        val recordedDelays = mutableListOf<Long>()
        val policy = RetryPolicy(
            delayFn = { recordedDelays.add(it) },
            randomFn = { 0L }
        )

        var requestCount = 0
        val response = policy.execute {
            requestCount++
            createResponse(200, "{\"kind\":\"drive#fileList\"}")
        }

        assertEquals(200, response.code)
        assertEquals(1, requestCount)
        assertTrue(recordedDelays.isEmpty())
    }

    @Test
    fun http429RetriedUpToMaxRetriesWithExponentialBackoffAndSucceedsOnSubsequentAttempt() = runTest {
        val recordedDelays = mutableListOf<Long>()
        val policy = RetryPolicy(
            maxRetries = 3,
            initialDelayMillis = 500L,
            maxDelayMillis = 8000L,
            delayFn = { recordedDelays.add(it) },
            randomFn = { 0L }
        )

        var attempt = 0
        val response = policy.execute {
            attempt++
            if (attempt <= 3) {
                createResponse(429, "{\"error\":{\"message\":\"Too many requests\"}}")
            } else {
                createResponse(200, "{\"status\":\"ok\"}")
            }
        }

        assertEquals(200, response.code)
        assertEquals(4, attempt)
        assertEquals(listOf(500L, 1000L, 2000L), recordedDelays)
    }

    @Test
    fun http429WithRetryAfterHeaderUsesHeaderDelay() = runTest {
        val recordedDelays = mutableListOf<Long>()
        val policy = RetryPolicy(
            maxRetries = 3,
            delayFn = { recordedDelays.add(it) },
            randomFn = { 0L }
        )

        var attempt = 0
        val response = policy.execute {
            attempt++
            if (attempt == 1) {
                createResponse(
                    code = 429,
                    body = "{\"error\":{\"message\":\"Rate limit\"}}",
                    headers = mapOf("Retry-After" to "3")
                )
            } else {
                createResponse(200, "{\"status\":\"ok\"}")
            }
        }

        assertEquals(200, response.code)
        assertEquals(2, attempt)
        assertEquals(listOf(3000L), recordedDelays)
    }

    @Test
    fun http403WithRateLimitReasonRetriedAndThrowsRateLimitedWhenExhausted() = runTest {
        val recordedDelays = mutableListOf<Long>()
        val policy = RetryPolicy(
            maxRetries = 3,
            initialDelayMillis = 500L,
            delayFn = { recordedDelays.add(it) },
            randomFn = { 0L }
        )

        val rateLimitBody = """
            {
              "error": {
                "errors": [
                  {
                    "domain": "usageLimits",
                    "reason": "userRateLimitExceeded",
                    "message": "User Rate Limit Exceeded"
                  }
                ],
                "code": 403,
                "message": "User Rate Limit Exceeded"
              }
            }
        """.trimIndent()

        var attempt = 0
        val exception = assertThrows(DriveApiException::class.java) {
            runBlocking {
                policy.execute {
                    attempt++
                    createResponse(403, rateLimitBody)
                }
            }
        }

        assertTrue(exception.comicError is ComicError.RateLimited)
        assertEquals(4, attempt)
        assertEquals(listOf(500L, 1000L, 2000L), recordedDelays)
    }

    @Test
    fun http403WithNonRateLimitReasonNotRetriedAndImmediatelyThrowsDriveApiError() = runTest {
        val recordedDelays = mutableListOf<Long>()
        val policy = RetryPolicy(
            maxRetries = 3,
            delayFn = { recordedDelays.add(it) },
            randomFn = { 0L }
        )

        val nonRateLimitBody = """
            {
              "error": {
                "errors": [
                  {
                    "domain": "global",
                    "reason": "insufficientPermissions",
                    "message": "Insufficient Permission"
                  }
                ],
                "code": 403,
                "message": "The user does not have sufficient permissions."
              }
            }
        """.trimIndent()

        var attempt = 0
        val exception = assertThrows(DriveApiException::class.java) {
            runBlocking {
                policy.execute {
                    attempt++
                    createResponse(403, nonRateLimitBody)
                }
            }
        }

        assertTrue(exception.comicError is ComicError.DriveApiError)
        val apiError = exception.comicError as ComicError.DriveApiError
        assertEquals(403, apiError.code)
        assertEquals("The user does not have sufficient permissions.", apiError.message)
        assertEquals(1, attempt)
        assertTrue(recordedDelays.isEmpty())
    }

    @Test
    fun ioExceptionMappedToDriveApiExceptionWithNetworkUnavailable() = runTest {
        val policy = RetryPolicy()

        val exception = assertThrows(DriveApiException::class.java) {
            runBlocking {
                policy.execute {
                    throw IOException("Connection reset by peer")
                }
            }
        }

        assertTrue(exception.comicError is ComicError.NetworkUnavailable)
        assertNotNull(exception.cause)
        assertEquals("Connection reset by peer", exception.cause?.message)
    }

    @Test
    fun http401PassesThroughWithoutThrowing() = runTest {
        val policy = RetryPolicy()
        val response = policy.execute {
            createResponse(401, "{\"error\":{\"message\":\"Unauthorized\"}}")
        }
        assertEquals(401, response.code)
    }
}
