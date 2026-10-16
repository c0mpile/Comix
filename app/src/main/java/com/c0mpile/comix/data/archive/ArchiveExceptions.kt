package com.c0mpile.comix.data.archive

import java.io.IOException

/**
 * Thrown when an archive is malformed, missing a valid End of Central Directory record,
 * or contains corrupted compressed data.
 */
class CorruptArchiveException(
    val fileName: String? = null,
    val reason: String,
    cause: Throwable? = null
) : IOException(
    if (fileName != null) "Corrupt archive ($fileName): $reason" else "Corrupt archive: $reason",
    cause
) {
    constructor(reason: String, cause: Throwable? = null) : this(
        fileName = null,
        reason = reason,
        cause = cause
    )
}

/**
 * Thrown when an archive has zero bytes or contains no supported image page entries.
 */
class EmptyArchiveException(
    val fileName: String? = null,
    cause: Throwable? = null
) : IOException(
    if (fileName != null) "Empty archive ($fileName)" else "Empty archive",
    cause
)
