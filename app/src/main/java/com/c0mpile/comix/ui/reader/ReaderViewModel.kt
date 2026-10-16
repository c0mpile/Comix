package com.c0mpile.comix.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.c0mpile.comix.data.archive.ComicArchiveReader
import com.c0mpile.comix.data.repository.ComicDomainException
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.repository.ReaderRepository
import com.c0mpile.comix.domain.usecase.GetComicUseCase
import com.c0mpile.comix.domain.usecase.SaveReadingProgressUseCase
import com.c0mpile.comix.ui.navigation.ReaderRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing the Reader screen, archive opening, bounded page caching,
 * decoding orchestration, reading progress persistence, and HUD state.
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getComicUseCase: GetComicUseCase,
    private val saveReadingProgressUseCase: SaveReadingProgressUseCase,
    private val readerRepository: ReaderRepository
) : ViewModel() {

    val comicKey: String = try {
        savedStateHandle.toRoute<ReaderRoute>().comicKey
    } catch (_: Exception) {
        savedStateHandle.get<String>("comicKey") ?: ""
    }

    private val _uiState = MutableStateFlow(
        ReaderUiState(comicKey = comicKey, isArchiveLoading = true)
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val pageCache = ReaderPageCache()
    private var currentReader: ComicArchiveReader? = null
    private val decodeJobs = mutableMapOf<Int, Job>()

    init {
        loadArchive()
    }

    fun onEvent(event: ReaderUiEvent) {
        when (event) {
            is ReaderUiEvent.ViewportSizeChanged -> {
                _uiState.update {
                    it.copy(viewportWidthPx = event.widthPx, viewportHeightPx = event.heightPx)
                }
                if (currentReader != null && _uiState.value.pageCount > 0) {
                    preloadPages(_uiState.value.currentPage, _uiState.value.pageCount)
                }
            }

            is ReaderUiEvent.PageChanged -> {
                val pageCount = _uiState.value.pageCount
                if (event.pageIndex in 0 until pageCount && event.pageIndex != _uiState.value.currentPage) {
                    _uiState.update { it.copy(currentPage = event.pageIndex) }
                    viewModelScope.launch {
                        saveReadingProgressUseCase(comicKey, event.pageIndex, pageCount)
                    }
                    preloadPages(event.pageIndex, pageCount)
                }
            }

            is ReaderUiEvent.RetryPage -> {
                pageCache.remove(event.pageIndex)
                val width = _uiState.value.viewportWidthPx
                val height = _uiState.value.viewportHeightPx
                if (width > 0 && height > 0) {
                    ensurePageLoaded(event.pageIndex, width, height)
                }
            }

            is ReaderUiEvent.ToggleHud -> {
                _uiState.update { it.copy(isHudVisible = !it.isHudVisible) }
            }

            is ReaderUiEvent.RetryOpenArchive -> {
                loadArchive()
            }
        }
    }

    private fun loadArchive() {
        for (job in decodeJobs.values) {
            job.cancel()
        }
        decodeJobs.clear()

        try {
            currentReader?.close()
        } catch (_: IOException) {}
        currentReader = null

        _uiState.update {
            it.copy(isArchiveLoading = true, fatalError = null)
        }

        viewModelScope.launch {
            val comic = getComicUseCase(comicKey)
            if (comic == null) {
                _uiState.update {
                    it.copy(
                        isArchiveLoading = false,
                        fatalError = ComicError.FileNotFound(comicKey),
                        isHudVisible = true
                    )
                }
                return@launch
            }

            val result = readerRepository.openComicReader(comic)
            result.onSuccess { reader ->
                currentReader = reader
                val pages = reader.getPages()
                if (pages.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isArchiveLoading = false,
                            fatalError = ComicError.EmptyArchive(comic.fileName),
                            isHudVisible = true
                        )
                    }
                    return@launch
                }

                val pageCount = pages.size
                val progress = readerRepository.getReadingProgress(comicKey) ?: comic.progress
                val initialPage = progress?.currentPage?.coerceIn(0, pageCount - 1) ?: 0

                _uiState.update {
                    it.copy(
                        title = comic.title,
                        pageCount = pageCount,
                        currentPage = initialPage,
                        isArchiveLoading = false,
                        fatalError = null
                    )
                }

                saveReadingProgressUseCase(comicKey, initialPage, pageCount)
                preloadPages(initialPage, pageCount)
            }.onFailure { throwable ->
                val error = (throwable as? ComicDomainException)?.comicError
                    ?: ComicError.UnknownIoError(throwable.message)
                _uiState.update {
                    it.copy(
                        isArchiveLoading = false,
                        fatalError = error,
                        isHudVisible = true
                    )
                }
            }
        }
    }

    private fun preloadPages(currentPage: Int, pageCount: Int) {
        val width = _uiState.value.viewportWidthPx
        val height = _uiState.value.viewportHeightPx
        if (width <= 0 || height <= 0) return

        val activeRange = (currentPage - 2)..(currentPage + 2)
        val staleEntries = decodeJobs.filterKeys { it !in activeRange }
        for ((pageIdx, job) in staleEntries) {
            job.cancel()
            decodeJobs.remove(pageIdx)
        }

        val prioritizedIndices = listOf(currentPage, currentPage + 1, currentPage - 1)
            .filter { it in 0 until pageCount }
            .distinct()

        for (pageIndex in prioritizedIndices) {
            ensurePageLoaded(pageIndex, width, height)
        }
    }

    private fun ensurePageLoaded(pageIndex: Int, widthPx: Int, heightPx: Int) {
        val cached = pageCache.get(pageIndex)
        if (cached != null) {
            _uiState.update {
                val updated = it.pageStates.toMutableMap()
                updated[pageIndex] = PageLoadState.Loaded(cached)
                it.copy(pageStates = updated)
            }
            return
        }

        val currentState = _uiState.value.pageStates[pageIndex]
        if (currentState is PageLoadState.Loading) {
            return
        }

        decodeJobs[pageIndex]?.cancel()
        decodeJobs[pageIndex] = viewModelScope.launch {
            _uiState.update {
                val updated = it.pageStates.toMutableMap()
                updated[pageIndex] = PageLoadState.Loading
                it.copy(pageStates = updated)
            }

            val reader = currentReader ?: return@launch
            val result = readerRepository.decodePageBitmap(reader, pageIndex, widthPx, heightPx)

            result.onSuccess { bitmap ->
                pageCache.put(pageIndex, bitmap)
                _uiState.update {
                    val updated = it.pageStates.toMutableMap()
                    updated[pageIndex] = PageLoadState.Loaded(bitmap)
                    it.copy(pageStates = updated)
                }
            }.onFailure { throwable ->
                val error = (throwable as? ComicDomainException)?.comicError
                    ?: ComicError.UnknownIoError(throwable.message)
                if (error is ComicError.OutOfMemory) {
                    pageCache.evictNonVisible(setOf(_uiState.value.currentPage))
                }
                _uiState.update {
                    val updated = it.pageStates.toMutableMap()
                    updated[pageIndex] = PageLoadState.Error(error)
                    it.copy(pageStates = updated)
                }
            }
        }
    }

    override fun onCleared() {
        for (job in decodeJobs.values) {
            job.cancel()
        }
        decodeJobs.clear()
        try {
            currentReader?.close()
        } catch (_: IOException) {}
        currentReader = null
        pageCache.clear()
    }
}
