package com.c0mpile.comix.domain.model

/**
 * Metadata for the currently signed-in Google Drive account.
 */
data class DriveAccount(
    val email: String,
    val signedInAtEpochMillis: Long
)
