package com.c0mpile.comix.domain.model

/**
 * Lightweight folder item returned while browsing Google Drive folders in the folder picker.
 */
data class DriveBrowseItem(
    val id: String,
    val name: String,
    val driveId: String? = null,
    val isShortcut: Boolean = false
)
