package com.c0mpile.comix.ui.library

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.c0mpile.comix.R
import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ComicAvailability
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicLibrary
import com.c0mpile.comix.domain.model.ComicSourceType
import com.c0mpile.comix.domain.model.LibraryFilterTab
import com.c0mpile.comix.domain.model.LibraryFolder
import com.c0mpile.comix.domain.model.LibraryLayoutMode
import com.c0mpile.comix.domain.model.LibrarySortOrder
import com.c0mpile.comix.domain.model.ReadingDirection
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val dismissLabel = stringResource(R.string.snackbar_dismiss)
    var isSortMenuExpanded by remember { mutableStateOf(false) }
    var isCreatingLibrary by remember { mutableStateOf(false) }
    var libraryToEdit by remember { mutableStateOf<ComicLibrary?>(null) }
    val searchFocusRequester = remember { FocusRequester() }

    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }

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
        is ComicError.AuthExpired, is ComicError.AuthRevoked ->
            stringResource(R.string.error_drive_auth_expired)
        is ComicError.NetworkUnavailable ->
            stringResource(R.string.error_drive_network_unavailable)
        is ComicError.RateLimited ->
            stringResource(R.string.error_drive_rate_limited)
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

    if (isCreatingLibrary) {
        LibraryEditorDialog(
            initialName = "",
            initialDirection = ReadingDirection.LEFT_TO_RIGHT,
            title = stringResource(R.string.dialog_create_library_title),
            canDelete = false,
            onConfirm = { name, direction ->
                onEvent(LibraryUiEvent.CreateLibrarySubmitted(name, direction))
                isCreatingLibrary = false
            },
            onDismiss = { isCreatingLibrary = false }
        )
    }

    val currentEdit = libraryToEdit
    if (currentEdit != null) {
        LibraryEditorDialog(
            initialName = currentEdit.name,
            initialDirection = currentEdit.defaultReadingDirection,
            title = stringResource(R.string.dialog_edit_library_title),
            canDelete = uiState.libraries.size > 1,
            onConfirm = { name, direction ->
                onEvent(LibraryUiEvent.UpdateLibrarySubmitted(currentEdit.libraryId, name, direction))
                libraryToEdit = null
            },
            onDelete = {
                onEvent(LibraryUiEvent.DeleteLibraryConfirmed(currentEdit.libraryId))
                libraryToEdit = null
            },
            onDismiss = { libraryToEdit = null }
        )
    }

    val comicForDetails = uiState.selectedComicForDetails
    if (comicForDetails != null) {
        ComicDetailsDialog(
            comic = comicForDetails,
            onDismiss = { onEvent(LibraryUiEvent.DismissComicDetails) },
            onReadComic = {
                onEvent(LibraryUiEvent.DismissComicDetails)
                onOpenComic(comicForDetails.comicKey)
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = stringResource(R.string.library_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)
                )
                Text(
                    text = stringResource(R.string.drawer_libraries_header),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                )

                for (library in uiState.libraries) {
                    val isSelected = library.libraryId == uiState.activeLibraryId
                    NavigationDrawerItem(
                        selected = isSelected,
                        onClick = {
                            onEvent(LibraryUiEvent.LibrarySelected(library.libraryId))
                            coroutineScope.launch { drawerState.close() }
                        },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = library.name,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ) {
                                    Text(
                                        text = if (library.defaultReadingDirection == ReadingDirection.LEFT_TO_RIGHT) {
                                            stringResource(R.string.library_direction_ltr_short)
                                        } else {
                                            stringResource(R.string.library_direction_rtl_short)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        },
                        badge = {
                            IconButton(onClick = { libraryToEdit = library }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.action_edit_library, library.name)
                                )
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    label = { Text(stringResource(R.string.action_create_library)) },
                    selected = false,
                    onClick = {
                        isCreatingLibrary = true
                        coroutineScope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.action_settings)) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        onOpenSettings()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }
        }
    ) {
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
                        navigationIcon = {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = stringResource(R.string.action_open_drawer)
                                )
                            }
                        },
                        title = { Text(text = uiState.activeLibrary?.name ?: stringResource(R.string.library_title)) },
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
                            IconButton(onClick = { onEvent(LibraryUiEvent.ToggleLayoutMode) }) {
                                if (uiState.layoutMode == LibraryLayoutMode.GRID) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_list_view),
                                        contentDescription = stringResource(R.string.action_switch_to_list_view)
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_grid_view),
                                        contentDescription = stringResource(R.string.action_switch_to_grid_view)
                                    )
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

                if (!uiState.isInitialLoading && !uiState.showEmptyNoFoldersState) {
                    if (!uiState.isSearchActive) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { onEvent(LibraryUiEvent.SearchQueryChanged(it)) },
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onEvent(LibraryUiEvent.SearchQueryChanged("")) }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.action_clear_search)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    val tabs = listOf(
                        LibraryFilterTab.ALL to R.string.tab_all,
                        LibraryFilterTab.IN_PROGRESS to R.string.tab_in_progress,
                        LibraryFilterTab.FINISHED to R.string.tab_finished
                    )
                    PrimaryTabRow(
                        selectedTabIndex = tabs.indexOfFirst { it.first == uiState.selectedFilterTab }.coerceAtLeast(0),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tabs.forEach { (tab, labelRes) ->
                            Tab(
                                selected = uiState.selectedFilterTab == tab,
                                onClick = { onEvent(LibraryUiEvent.FilterTabSelected(tab)) },
                                text = { Text(stringResource(labelRes)) }
                            )
                        }
                    }
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

                        uiState.showEmptyFilterTabState -> {
                            val (titleRes, messageRes) = if (uiState.selectedFilterTab == LibraryFilterTab.IN_PROGRESS) {
                                R.string.empty_in_progress_title to R.string.empty_in_progress_message
                            } else {
                                R.string.empty_finished_title to R.string.empty_finished_message
                            }
                            EmptyStateView(
                                title = stringResource(titleRes),
                                message = stringResource(messageRes),
                                actionLabel = stringResource(R.string.action_show_all_comics),
                                onActionClick = { onEvent(LibraryUiEvent.FilterTabSelected(LibraryFilterTab.ALL)) }
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
                            if (uiState.layoutMode == LibraryLayoutMode.GRID) {
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
                                            onClick = {
                                                onOpenComic(comic.comicKey)
                                            },
                                            onInfoClick = {
                                                onEvent(LibraryUiEvent.ShowComicDetails(comic))
                                            }
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(ComixDimens.gridItemPadding),
                                    verticalArrangement = Arrangement.spacedBy(ComixDimens.spacingSmall),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(
                                        items = uiState.comics,
                                        key = { it.comicKey }
                                    ) { comic ->
                                        ComicListItem(
                                            comic = comic,
                                            onClick = {
                                                onOpenComic(comic.comicKey)
                                            },
                                            onInfoClick = {
                                                onEvent(LibraryUiEvent.ShowComicDetails(comic))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog for creating a new library or editing an existing library's name and reading direction.
 */
@Composable
fun LibraryEditorDialog(
    initialName: String,
    initialDirection: ReadingDirection,
    title: String,
    canDelete: Boolean,
    onConfirm: (String, ReadingDirection) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialName) }
    var direction by remember { mutableStateOf(initialDirection) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dialog_library_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.dialog_library_direction_label),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = direction == ReadingDirection.LEFT_TO_RIGHT,
                        onClick = { direction = ReadingDirection.LEFT_TO_RIGHT },
                        label = { Text(stringResource(R.string.settings_reading_direction_ltr)) }
                    )
                    FilterChip(
                        selected = direction == ReadingDirection.RIGHT_TO_LEFT,
                        onClick = { direction = ReadingDirection.RIGHT_TO_LEFT },
                        label = { Text(stringResource(R.string.settings_reading_direction_rtl)) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim(), direction) }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (canDelete && onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.action_delete_library))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
        modifier = modifier
    )
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
