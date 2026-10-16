package com.c0mpile.comix.domain.model

/**
 * Explicit domain errors mapped from storage, permission, archive, and memory failures.
 */
sealed interface ComicError {
    data class CorruptArchive(val fileName: String?, val reason: String) : ComicError
    data class EmptyArchive(val fileName: String?) : ComicError
    data class PermissionRevoked(val uri: String) : ComicError
    data class FileNotFound(val uri: String) : ComicError
    data class OutOfMemory(val pageIndex: Int?) : ComicError
    data class UnknownIoError(val message: String?) : ComicError
}
