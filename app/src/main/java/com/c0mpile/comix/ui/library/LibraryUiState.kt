package com.c0mpile.comix.ui.library

import com.c0mpile.comix.data.archive.NaturalOrderComparator
import com.c0mpile.comix.domain.model.BulkComicMetadataUpdate
import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.ComicLibrary
import com.c0mpile.comix.domain.model.ComicSeries
import com.c0mpile.comix.domain.model.DriveFolder
import com.c0mpile.comix.domain.model.LibraryFilterTab
import com.c0mpile.comix.domain.model.LibraryFolder
import com.c0mpile.comix.domain.model.LibraryLayoutMode
import com.c0mpile.comix.domain.model.LibrarySortOrder
import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.domain.model.SingleComicMetadataUpdate
import java.util.Locale

/**
 * Immutable UI state for the Library screen.
 */
data class LibraryUiState(
    val comics: List<Comic> = emptyList(),
    val seriesList: List<ComicSeries> = emptyList(),
    val selectedSeriesKey: String? = null,
    val folders: List<LibraryFolder> = emptyList(),
    val driveFolders: List<DriveFolder> = emptyList(),
    val libraries: List<ComicLibrary> = emptyList(),
    val activeLibraryId: String = ComicLibrary.DEFAULT_COMICS_ID,
    val selectedFilterTab: LibraryFilterTab = LibraryFilterTab.ALL,
    val layoutMode: LibraryLayoutMode = LibraryLayoutMode.GRID,
    val isInitialLoading: Boolean = true,
    val isScanning: Boolean = false,
    val revokedFolders: List<LibraryFolder> = emptyList(),
    val activeError: ComicError? = null,
    val scanFailureCount: Int = 0,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val sortOrder: LibrarySortOrder = LibrarySortOrder.TITLE_ASC,
    val totalComicCount: Int = comics.size,
    val selectedComicForDetails: Comic? = null,
    val selectedComicKeys: Set<String> = emptySet(),
    val editingMetadataComics: List<Comic>? = null
) {
    val activeLibrary: ComicLibrary?
        get() = libraries.find { it.libraryId == activeLibraryId } ?: libraries.firstOrNull()

    val selectedSeries: ComicSeries?
        get() = selectedSeriesKey?.let { key -> seriesList.find { it.seriesKey == key } }

    val isSelectionMode: Boolean
        get() = selectedComicKeys.isNotEmpty()

    val showEmptyNoFoldersState: Boolean
        get() = !isInitialLoading && !isScanning && folders.isEmpty() && driveFolders.isEmpty() && comics.isEmpty() && totalComicCount == 0

    val showEmptyNoComicsState: Boolean
        get() = !isInitialLoading && !isScanning && (folders.isNotEmpty() || driveFolders.isNotEmpty()) && comics.isEmpty() && totalComicCount == 0 && revokedFolders.isEmpty()

    val showEmptySearchResultsState: Boolean
        get() = !isInitialLoading && !isScanning && totalComicCount > 0 && comics.isEmpty() && searchQuery.isNotBlank()

    val showEmptyFilterTabState: Boolean
        get() = !isInitialLoading && !isScanning && totalComicCount > 0 && comics.isEmpty() && searchQuery.isBlank() && selectedFilterTab != LibraryFilterTab.ALL
}

/**
 * User intents emitted from the Library screen.
 */
sealed interface LibraryUiEvent {
    data class FolderPicked(val treeUri: String) : LibraryUiEvent
    data object RescanRequested : LibraryUiEvent
    data class RemoveFolderRequested(val folderUri: String) : LibraryUiEvent
    data object DismissError : LibraryUiEvent
    data class SearchQueryChanged(val query: String) : LibraryUiEvent
    data class SearchActiveChanged(val active: Boolean) : LibraryUiEvent
    data class SortOrderSelected(val sortOrder: LibrarySortOrder) : LibraryUiEvent
    data class LibrarySelected(val libraryId: String) : LibraryUiEvent
    data class CreateLibrarySubmitted(val name: String, val defaultReadingDirection: ReadingDirection) : LibraryUiEvent
    data class UpdateLibrarySubmitted(val libraryId: String, val name: String, val defaultReadingDirection: ReadingDirection) : LibraryUiEvent
    data class DeleteLibraryConfirmed(val libraryId: String) : LibraryUiEvent
    data class FilterTabSelected(val tab: LibraryFilterTab) : LibraryUiEvent
    data object ToggleLayoutMode : LibraryUiEvent
    data class ShowComicDetails(val comic: Comic) : LibraryUiEvent
    data object DismissComicDetails : LibraryUiEvent
    data class SeriesSelected(val seriesKey: String) : LibraryUiEvent
    data object ClearSelectedSeries : LibraryUiEvent
    data class ToggleComicSelected(val comicKey: String) : LibraryUiEvent
    data class EnterSelectionMode(val comicKey: String) : LibraryUiEvent
    data object SelectAllComics : LibraryUiEvent
    data object ClearSelection : LibraryUiEvent
    data object OpenMetadataEditorForSelection : LibraryUiEvent
    data class OpenMetadataEditorForComic(val comic: Comic) : LibraryUiEvent
    data object DismissMetadataEditor : LibraryUiEvent
    data class SaveSingleMetadata(val update: SingleComicMetadataUpdate) : LibraryUiEvent
    data class SaveBulkMetadata(val update: BulkComicMetadataUpdate) : LibraryUiEvent
}

private val ISSUE_SUFFIX_REGEX = Regex(
    """(?:[\s_-]+(?:issue|vol\.?|volume|ch\.?|chapter|part)\s*#?\d+.*|[\s_-]+#\d+.*|[\s_-]+\d+)$""",
    RegexOption.IGNORE_CASE
)

/**
 * Groups a collection of [comics] into [ComicSeries] instances by metadata series name
 * or title-derived series name, sorting comics naturally within each series and sorting
 * the resulting series list according to [sortOrder].
 */
fun groupComicsIntoSeries(
    comics: List<Comic>,
    sortOrder: LibrarySortOrder = LibrarySortOrder.TITLE_ASC
): List<ComicSeries> {
    if (comics.isEmpty()) return emptyList()

    val groups = LinkedHashMap<String, MutableList<Comic>>()
    val seriesNames = LinkedHashMap<String, String>()

    for (comic in comics) {
        val metaSeries = comic.metadata?.series?.trim()?.takeIf { it.isNotEmpty() }
        val derivedName = if (metaSeries != null) {
            metaSeries
        } else {
            val titleTrimmed = comic.title.trim()
            val stripped = titleTrimmed.replace(ISSUE_SUFFIX_REGEX, "").trim()
            stripped.ifBlank { titleTrimmed }
        }
        val seriesKey = derivedName.lowercase(Locale.ROOT)

        groups.getOrPut(seriesKey) { mutableListOf() }.add(comic)
        if (!seriesNames.containsKey(seriesKey) || metaSeries != null) {
            seriesNames[seriesKey] = derivedName
        }
    }

    val seriesList = groups.map { (key, groupComics) ->
        val sortedComics = groupComics.sortedWith { a, b ->
            val aVol = a.metadata?.volume ?: Int.MAX_VALUE
            val bVol = b.metadata?.volume ?: Int.MAX_VALUE
            val volCmp = aVol.compareTo(bVol)
            if (volCmp != 0) {
                volCmp
            } else {
                val aNum = a.metadata?.number
                val bNum = b.metadata?.number
                if (aNum != null && bNum != null) {
                    val numCmp = NaturalOrderComparator.compare(aNum, bNum)
                    if (numCmp != 0) numCmp else NaturalOrderComparator.compare(a.title, b.title)
                } else if (aNum != null) {
                    -1
                } else if (bNum != null) {
                    1
                } else {
                    NaturalOrderComparator.compare(a.title, b.title)
                }
            }
        }
        ComicSeries(
            seriesKey = key,
            name = seriesNames[key] ?: key,
            comics = sortedComics,
            coverComic = sortedComics.first()
        )
    }

    return when (sortOrder) {
        LibrarySortOrder.TITLE_ASC -> seriesList.sortedWith { a, b ->
            NaturalOrderComparator.compare(a.name, b.name)
        }
        LibrarySortOrder.TITLE_DESC -> seriesList.sortedWith { a, b ->
            NaturalOrderComparator.compare(b.name, a.name)
        }
        LibrarySortOrder.RECENTLY_READ -> seriesList.sortedWith { a, b ->
            val aTime = a.comics.maxOfOrNull { it.progress?.updatedAtEpochMillis ?: 0L } ?: 0L
            val bTime = b.comics.maxOfOrNull { it.progress?.updatedAtEpochMillis ?: 0L } ?: 0L
            val timeCmp = bTime.compareTo(aTime)
            if (timeCmp != 0) timeCmp else NaturalOrderComparator.compare(a.name, b.name)
        }
        LibrarySortOrder.DATE_ADDED_DESC -> seriesList.sortedWith { a, b ->
            val aTime = a.comics.maxOfOrNull { it.lastModifiedEpochMillis } ?: 0L
            val bTime = b.comics.maxOfOrNull { it.lastModifiedEpochMillis } ?: 0L
            val timeCmp = bTime.compareTo(aTime)
            if (timeCmp != 0) timeCmp else NaturalOrderComparator.compare(a.name, b.name)
        }
    }
}
