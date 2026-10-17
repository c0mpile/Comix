package com.c0mpile.comix.ui.reader

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.c0mpile.comix.R
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.ui.components.ErrorStateView
import com.c0mpile.comix.ui.components.LoadingStateView
import com.c0mpile.comix.ui.theme.ComixTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Stateful host for the Comic Reader screen.
 */
@Composable
fun ReaderScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ReaderScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

/**
 * Stateless content composable for the Comic Reader screen.
 */
@Composable
fun ReaderScreenContent(
    uiState: ReaderUiState,
    onEvent: (ReaderUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window

    DisposableEffect(view, uiState.keepScreenOn) {
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = uiState.keepScreenOn
        onDispose {
            view.keepScreenOn = previousKeepScreenOn
        }
    }

    if (window != null) {
        DisposableEffect(uiState.isHudVisible, uiState.fatalError) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            val shouldHideSystemBars = !uiState.isHudVisible && uiState.fatalError == null
            if (shouldHideSystemBars) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }

            onDispose {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val fatalError = uiState.fatalError
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    onEvent(ReaderUiEvent.ViewportSizeChanged(size.width, size.height))
                }
            }
    ) {
        when {
            fatalError != null -> {
                val (title, message) = when (fatalError) {
                    is ComicError.FileNotFound ->
                        stringResource(R.string.error_file_not_found_title) to stringResource(R.string.error_file_not_found_message)
                    is ComicError.PermissionRevoked ->
                        stringResource(R.string.error_permission_revoked_title) to stringResource(R.string.error_permission_revoked_message)
                    is ComicError.CorruptArchive ->
                        stringResource(R.string.error_corrupt_archive_title) to fatalError.reason
                    is ComicError.EmptyArchive ->
                        stringResource(R.string.error_empty_archive_title) to stringResource(R.string.error_empty_archive_message)
                    is ComicError.OutOfMemory ->
                        stringResource(R.string.error_oom_title) to stringResource(R.string.error_oom_message)
                    is ComicError.UnknownIoError ->
                        stringResource(R.string.error_unknown) to (fatalError.message ?: stringResource(R.string.error_unknown))
                    is ComicError.AuthExpired,
                    is ComicError.AuthRevoked,
                    is ComicError.NetworkUnavailable,
                    is ComicError.RateLimited ->
                        stringResource(R.string.error_unknown) to stringResource(R.string.error_unknown)
                    is ComicError.DriveApiError ->
                        stringResource(R.string.error_unknown) to (fatalError.message ?: stringResource(R.string.error_unknown))
                }

                ErrorStateView(
                    title = title,
                    message = message,
                    retryLabel = stringResource(R.string.action_retry),
                    onRetryClick = { onEvent(ReaderUiEvent.RetryOpenArchive) },
                    secondaryLabel = stringResource(R.string.action_back_to_library),
                    onSecondaryClick = onNavigateBack
                )
            }

            uiState.isArchiveLoading -> {
                LoadingStateView(message = stringResource(R.string.reader_loading_archive))
            }

            uiState.pageCount > 0 -> {
                val coroutineScope = rememberCoroutineScope()
                val pagerState = rememberPagerState(
                    initialPage = uiState.currentPage,
                    pageCount = { uiState.pageCount }
                )

                LaunchedEffect(uiState.currentPage) {
                    if (pagerState.currentPage != uiState.currentPage &&
                        uiState.currentPage in 0 until uiState.pageCount
                    ) {
                        pagerState.scrollToPage(uiState.currentPage)
                    }
                }

                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.settledPage }
                        .distinctUntilChanged()
                        .collect { settledPage ->
                            onEvent(ReaderUiEvent.PageChanged(settledPage))
                        }
                }

                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    reverseLayout = (uiState.readingDirection == ReadingDirection.RIGHT_TO_LEFT),
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val pageState = uiState.pageStates[pageIndex] ?: PageLoadState.Idle

                    when (pageState) {
                        is PageLoadState.Loaded -> {
                            ZoomablePageImage(
                                bitmap = pageState.bitmap,
                                contentDescription = stringResource(
                                    R.string.reader_page_image_cd,
                                    pageIndex + 1,
                                    uiState.pageCount
                                ),
                                isCurrentPage = pagerState.currentPage == pageIndex,
                                scaleMode = uiState.scaleMode,
                                readingDirection = uiState.readingDirection,
                                onPreviousPage = {
                                    if (pageIndex > 0) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pageIndex - 1)
                                        }
                                    }
                                },
                                onNextPage = {
                                    if (pageIndex < uiState.pageCount - 1) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pageIndex + 1)
                                        }
                                    }
                                },
                                onToggleHud = { onEvent(ReaderUiEvent.ToggleHud) }
                            )
                        }

                        is PageLoadState.Error -> {
                            val errorMessage = (pageState.error as? ComicError.CorruptArchive)?.reason
                                ?: stringResource(R.string.error_unknown)
                            ErrorStateView(
                                title = stringResource(R.string.error_page_failed, pageIndex + 1),
                                message = errorMessage,
                                retryLabel = stringResource(R.string.action_retry_page),
                                onRetryClick = { onEvent(ReaderUiEvent.RetryPage(pageIndex)) },
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        PageLoadState.Idle, PageLoadState.Loading -> {
                            LoadingStateView(
                                message = stringResource(R.string.reader_loading_page, pageIndex + 1)
                            )
                        }
                    }
                }

                ReaderOverlayHud(
                    isVisible = uiState.isHudVisible,
                    title = uiState.title,
                    currentPage = pagerState.currentPage,
                    pageCount = uiState.pageCount,
                    onPageSelected = { targetPage ->
                        coroutineScope.launch {
                            pagerState.scrollToPage(targetPage)
                        }
                    },
                    onNavigateBack = onNavigateBack,
                    readingDirection = uiState.readingDirection,
                    scaleMode = uiState.scaleMode,
                    onToggleReadingDirection = { onEvent(ReaderUiEvent.ToggleReadingDirection) },
                    onToggleScaleMode = { onEvent(ReaderUiEvent.ToggleScaleMode) }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReaderScreenLoadingPreview() {
    ComixTheme {
        ReaderScreenContent(
            uiState = ReaderUiState(isArchiveLoading = true),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReaderScreenErrorPreview() {
    ComixTheme {
        ReaderScreenContent(
            uiState = ReaderUiState(
                isArchiveLoading = false,
                fatalError = ComicError.CorruptArchive("issue1.cbz", "Bad Central Directory signature")
            ),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}
