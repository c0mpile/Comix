package com.c0mpile.comix.ui.reader

import android.graphics.Bitmap
import com.c0mpile.comix.domain.model.ComicError

/**
 * Per-page decoding state inside the Reader screen.
 */
sealed interface PageLoadState {
    data object Idle : PageLoadState
    data object Loading : PageLoadState
    data class Loaded(val bitmap: Bitmap) : PageLoadState
    data class Error(val error: ComicError) : PageLoadState
}

/**
 * Immutable UI state for the Reader screen.
 */
data class ReaderUiState(
    val comicKey: String = "",
    val title: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val isArchiveLoading: Boolean = true,
    val isHudVisible: Boolean = false,
    val viewportWidthPx: Int = 0,
    val viewportHeightPx: Int = 0,
    val pageStates: Map<Int, PageLoadState> = emptyMap(),
    val fatalError: ComicError? = null
)

/**
 * User intents emitted from the Reader screen.
 */
sealed interface ReaderUiEvent {
    data class ViewportSizeChanged(val widthPx: Int, val heightPx: Int) : ReaderUiEvent
    data class PageChanged(val pageIndex: Int) : ReaderUiEvent
    data class RetryPage(val pageIndex: Int) : ReaderUiEvent
    data object ToggleHud : ReaderUiEvent
    data object RetryOpenArchive : ReaderUiEvent
}
