package com.c0mpile.comix.ui.library

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.c0mpile.comix.R
import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ComicAvailability
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicSourceType
import com.c0mpile.comix.domain.model.LibraryFolder
import com.c0mpile.comix.domain.model.LibrarySortOrder
import com.c0mpile.comix.ui.components.EmptyStateView
import com.c0mpile.comix.ui.components.LoadingStateView
import com.c0mpile.comix.ui.theme.ComixDimens
import com.c0mpile.comix.ui.theme.ComixTheme

/**
 * Stateful host for the Library screen.
 */
@Composable
fun LibraryScreen(
    onOpenComic: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}
            viewModel.onEvent(LibraryUiEvent.FolderPicked(uri.toString()))
        }
    }

    LibraryScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onAddFolderClick = { folderPickerLauncher.launch(null) },
        onOpenSettings = onOpenSettings,
        onOpenComic = onOpenComic,
        modifier = modifier
    )
}

/**
 * Stateless content composable for the Library screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreenContent(
    uiState: LibraryUiState,
    onEvent: (LibraryUiEvent) -> Unit,
    onAddFolderClick: () -> Unit,
    onOpenComic: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val dismissLabel = stringResource(R.string.snackbar_dismiss)
    var isSortMenuExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    BackHandler(enabled = uiState.isSearchActive) {
        onEvent(LibraryUiEvent.SearchActiveChanged(false))
    }

    LaunchedEffect(uiState.isSearchActive) {
        if (uiState.isSearchActive) {
            searchFocusRequester.requestFocus()
        }
    }

    val skippedMessage = if (uiState.scanFailureCount > 0) {
        stringResource(R.string.snackbar_skipped_archives, uiState.scanFailureCount)
    } else null

    LaunchedEffect(uiState.scanFailureCount) {
        if (skippedMessage != null) {
            val result = snackbarHostState.showSnackbar(
                message = skippedMessage,
                actionLabel = dismissLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                onEvent(LibraryUiEvent.DismissError)
            }
        }
    }

    val activeErrorMessage = when (val err = uiState.activeError) {
        is ComicError.PermissionRevoked -> stringResource(R.string.banner_revoked_folders_title)
        is ComicError.UnknownIoError -> err.message ?: stringResource(R.string.error_unknown)
        is ComicError.CorruptArchive -> err.reason
        is ComicError.EmptyArchive -> stringResource(R.string.empty_no_comics_title)
        is ComicError.FileNotFound -> stringResource(R.string.error_unknown)
        is ComicError.OutOfMemory -> stringResource(R.string.error_unknown)
        is ComicError.AuthExpired -> stringResource(R.string.error_unknown)
        is ComicError.AuthRevoked -> stringResource(R.string.error_unknown)
        is ComicError.NetworkUnavailable -> stringResource(R.string.error_unknown)
        is ComicError.RateLimited -> stringResource(R.string.error_unknown)
        is ComicError.DriveApiError -> err.message ?: stringResource(R.string.error_unknown)
        null -> null
    }

    LaunchedEffect(uiState.activeError) {
        if (activeErrorMessage != null) {
            val result = snackbarHostState.showSnackbar(
                message = activeErrorMessage,
                actionLabel = dismissLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                onEvent(LibraryUiEvent.DismissError)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (uiState.isSearchActive) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { onEvent(LibraryUiEvent.SearchActiveChanged(false)) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_close_search)
                            )
                        }
                    },
                    title = {
                        TextField(
                            value = uiState.searchQuery,
                            onValueChange = { onEvent(LibraryUiEvent.SearchQueryChanged(it)) },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.search_hint),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester)
                        )
                    },
                    actions = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onEvent(LibraryUiEvent.SearchQueryChanged("")) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.action_clear_search)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else {
                TopAppBar(
                    title = { Text(text = stringResource(R.string.library_title)) },
                    actions = {
                        IconButton(onClick = { onEvent(LibraryUiEvent.SearchActiveChanged(true)) }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.action_search)
                            )
                        }
                        Box {
                            IconButton(onClick = { isSortMenuExpanded = true }) {
                                Icon(
                                    imageVector = SortIcon,
                                    contentDescription = stringResource(R.string.action_sort)
                                )
                            }
                            DropdownMenu(
                                expanded = isSortMenuExpanded,
                                onDismissRequest = { isSortMenuExpanded = false }
                            ) {
                                val sortOptions = listOf<Pair<LibrarySortOrder, Int>>(
                                    LibrarySortOrder.TITLE_ASC to R.string.sort_order_title_asc,
                                    LibrarySortOrder.TITLE_DESC to R.string.sort_order_title_desc,
                                    LibrarySortOrder.RECENTLY_READ to R.string.sort_order_recently_read,
                                    LibrarySortOrder.DATE_ADDED_DESC to R.string.sort_order_date_added_desc
                                )
                                for ((order, labelRes) in sortOptions) {
                                    val isSelected = uiState.sortOrder == order
                                    DropdownMenuItem(
                                        text = { Text(text = stringResource(labelRes)) },
                                        trailingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null
                                                )
                                            }
                                        } else null,
                                        onClick = {
                                            onEvent(LibraryUiEvent.SortOrderSelected(order))
                                            isSortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { onEvent(LibraryUiEvent.RescanRequested) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_rescan)
                            )
                        }
                        IconButton(onClick = onAddFolderClick) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.action_add_folder)
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.action_settings)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ComixDimens.progressIndicatorHeight),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (uiState.revokedFolders.isNotEmpty()) {
                RevokedFoldersBanner(
                    revokedFolders = uiState.revokedFolders,
                    onReauthorizeClick = onAddFolderClick,
                    onRemoveClick = { folderUri ->
                        onEvent(LibraryUiEvent.RemoveFolderRequested(folderUri))
                    },
                    modifier = Modifier.padding(ComixDimens.spacingMedium)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    uiState.isInitialLoading -> {
                        LoadingStateView(message = stringResource(R.string.loading_library))
                    }

                    uiState.showEmptyNoFoldersState -> {
                        EmptyStateView(
                            title = stringResource(R.string.empty_no_folders_title),
                            message = stringResource(R.string.empty_no_folders_message),
                            actionLabel = stringResource(R.string.empty_no_folders_action),
                            onActionClick = onAddFolderClick
                        )
                    }

                    uiState.showEmptySearchResultsState -> {
                        EmptyStateView(
                            title = stringResource(R.string.empty_search_title),
                            message = stringResource(R.string.empty_search_message, uiState.searchQuery),
                            actionLabel = stringResource(R.string.action_clear_search),
                            onActionClick = { onEvent(LibraryUiEvent.SearchQueryChanged("")) }
                        )
                    }

                    uiState.showEmptyNoComicsState -> {
                        EmptyStateView(
                            title = stringResource(R.string.empty_no_comics_title),
                            message = stringResource(R.string.empty_no_comics_message),
                            actionLabel = stringResource(R.string.empty_no_comics_action_add),
                            onActionClick = onAddFolderClick
                        )
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = ComixDimens.gridMinCellSize),
                            contentPadding = PaddingValues(ComixDimens.gridItemPadding),
                            horizontalArrangement = Arrangement.spacedBy(ComixDimens.gridItemPadding),
                            verticalArrangement = Arrangement.spacedBy(ComixDimens.gridItemPadding),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = uiState.comics,
                                key = { it.comicKey }
                            ) { comic ->
                                ComicGridItem(
                                    comic = comic,
                                    onClick = { onOpenComic(comic.comicKey) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RevokedFoldersBanner(
    revokedFolders: List<LibraryFolder>,
    onReauthorizeClick: () -> Unit,
    onRemoveClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ComixDimens.bannerCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(ComixDimens.spacingMedium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(ComixDimens.spacingSmall))
                Text(
                    text = stringResource(R.string.banner_revoked_folders_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            for (folder in revokedFolders) {
                Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))
                Text(
                    text = stringResource(
                        R.string.banner_revoked_folder_message,
                        folder.displayName
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { onRemoveClick(folder.folderUri) }) {
                        Text(text = stringResource(R.string.action_remove_folder))
                    }
                    Spacer(modifier = Modifier.width(ComixDimens.spacingSmall))
                    Button(onClick = onReauthorizeClick) {
                        Text(text = stringResource(R.string.action_reauthorize_folder))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenContentPreview() {
    ComixTheme {
        LibraryScreenContent(
            uiState = LibraryUiState(
                isInitialLoading = false,
                comics = listOf(
                    Comic(
                        comicKey = "comic_1",
                        title = "Amazing Fantasy #15",
                        fileName = "Amazing Fantasy #15.cbz",
                        format = ComicFormat.CBZ,
                        sourceType = ComicSourceType.LOCAL_SAF,
                        availability = ComicAvailability.LOCAL,
                        sizeBytes = 25_000_000L,
                        lastModifiedEpochMillis = System.currentTimeMillis(),
                        pageCount = 36,
                        localUri = null,
                        folderUri = null
                    )
                )
            ),
            onEvent = {},
            onAddFolderClick = {},
            onOpenComic = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenEmptyPreview() {
    ComixTheme {
        LibraryScreenContent(
            uiState = LibraryUiState(
                isInitialLoading = false,
                folders = emptyList(),
                comics = emptyList()
            ),
            onEvent = {},
            onAddFolderClick = {},
            onOpenComic = {}
        )
    }
}

private val SortIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Sort",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.Black)
    ) {
        moveTo(3f, 18f)
        horizontalLineTo(9f)
        verticalLineToRelative(-2f)
        horizontalLineTo(3f)
        verticalLineToRelative(2f)
        close()
        moveTo(3f, 6f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(18f)
        verticalLineTo(6f)
        horizontalLineTo(3f)
        close()
        moveTo(3f, 13f)
        horizontalLineToRelative(12f)
        verticalLineToRelative(-2f)
        horizontalLineTo(3f)
        verticalLineToRelative(2f)
        close()
    }.build()

