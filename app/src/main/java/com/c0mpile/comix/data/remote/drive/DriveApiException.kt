package com.c0mpile.comix.data.remote.drive

import com.c0mpile.comix.domain.model.ComicError
import java.io.IOException

/**
 * Exception representing an error encountered during Google Drive API communication,
 * carrying a structured [ComicError] payload.
 */
class DriveApiException(
    val comicError: ComicError,
    message: String = comicError.toString(),
    cause: Throwable? = null
) : IOException(message, cause)
