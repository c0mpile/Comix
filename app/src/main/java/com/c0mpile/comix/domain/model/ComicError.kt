package com.c0mpile.comix.domain.model

/**
 * Explicit domain errors mapped from storage, permission, archive, network, auth, and memory failures.
 */
sealed interface ComicError {
    data class CorruptArchive(val fileName: String?, val reason: String) : ComicError
    data class EmptyArchive(val fileName: String?) : ComicError
    data class PermissionRevoked(val uri: String) : ComicError
    data class FileNotFound(val uri: String) : ComicError
    data class OutOfMemory(val pageIndex: Int?) : ComicError
    data class UnknownIoError(val message: String?) : ComicError
    data object AuthExpired : ComicError
    data object AuthRevoked : ComicError
    data object NetworkUnavailable : ComicError
    data class RateLimited(val retryAfterMillis: Long? = null) : ComicError
    data class DriveApiError(val code: Int, val message: String? = null) : ComicError
}
