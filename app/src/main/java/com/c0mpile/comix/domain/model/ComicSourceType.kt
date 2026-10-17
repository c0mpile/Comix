package com.c0mpile.comix.domain.model

/**
 * Storage providers from which a comic can originate.
 */
enum class ComicSourceType {
    LOCAL_SAF,
    GOOGLE_DRIVE;

    companion object {
        val DRIVE: ComicSourceType = GOOGLE_DRIVE
    }
}
