package com.c0mpile.comix.ui.drive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.c0mpile.comix.R
import com.c0mpile.comix.domain.model.ComicError
import com.c0mpile.comix.domain.model.DriveBrowseItem
import com.c0mpile.comix.ui.components.EmptyStateView
import com.c0mpile.comix.ui.components.ErrorStateView
import com.c0mpile.comix.ui.components.LoadingStateView
import com.c0mpile.comix.ui.theme.ComixDimens
import com.c0mpile.comix.ui.theme.ComixTheme

/**
 * Stateful host for the Google Drive folder picker screen.
 */
@Composable
fun DriveFolderPickerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DriveFolderPickerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.folderAddedSuccess) {
        if (uiState.folderAddedSuccess) {
            onNavigateBack()
        }
    }

    DriveFolderPickerContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

/**
 * Stateless content composable for the Google Drive folder picker screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveFolderPickerContent(
    uiState: DriveFolderPickerUiState,
    onEvent: (DriveFolderPickerUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canPopStack = uiState.breadcrumbs.size > 1 && !uiState.isAddingFolder

    BackHandler(enabled = canPopStack) {
        onEvent(DriveFolderPickerUiEvent.NavigateUpInFolderStack)
    }

    val myDriveLabel = stringResource(R.string.drive_picker_my_drive)
    val currentTitle = if (uiState.currentFolder.folderId == DriveFolderPickerViewModel.ROOT_FOLDER_ID) {
        myDriveLabel
    } else {
        uiState.currentFolder.displayName
    }

    val breadcrumbPath = uiState.breadcrumbs.joinToString(" / ") {
        if (it.folderId == DriveFolderPickerViewModel.ROOT_FOLDER_ID) {
            myDriveLabel
        } else {
            it.displayName
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = breadcrumbPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (canPopStack) {
                                onEvent(DriveFolderPickerUiEvent.NavigateUpInFolderStack)
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(
                    topStart = ComixDimens.cardCornerRadius,
                    topEnd = ComixDimens.cardCornerRadius
                ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = ComixDimens.hudElevation)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(ComixDimens.spacingMedium)
                ) {
                    Button(
                        onClick = { onEvent(DriveFolderPickerUiEvent.ConfirmCurrentFolderClicked) },
                        enabled = !uiState.isLoading && !uiState.isAddingFolder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isAddingFolder) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(ComixDimens.iconSmall),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(ComixDimens.spacingSmall))
                            Text(text = stringResource(R.string.drive_picker_adding))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(ComixDimens.iconSmall)
                            )
                            Spacer(modifier = Modifier.width(ComixDimens.spacingSmall))
                            Text(text = stringResource(R.string.drive_picker_use_this_folder))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.error != null -> {
                    val errorMessage = when (val err = uiState.error) {
                        is ComicError.AuthExpired, is ComicError.AuthRevoked ->
                            stringResource(R.string.error_drive_auth_expired)
                        is ComicError.NetworkUnavailable ->
                            stringResource(R.string.error_drive_network_unavailable)
                        is ComicError.RateLimited ->
                            stringResource(R.string.error_drive_rate_limited)
                        is ComicError.DriveApiError ->
                            err.message ?: stringResource(R.string.error_unknown)
                        else ->
                            stringResource(R.string.error_unknown)
                    }
                    ErrorStateView(
                        title = stringResource(R.string.drive_picker_title),
                        message = errorMessage,
                        retryLabel = stringResource(R.string.action_retry),
                        onRetryClick = { onEvent(DriveFolderPickerUiEvent.RetryLoadClicked) }
                    )
                }

                uiState.isLoading -> {
                    LoadingStateView(
                        message = stringResource(R.string.loading_library)
                    )
                }

                uiState.subfolders.isEmpty() -> {
                    EmptyStateView(
                        title = stringResource(R.string.drive_picker_empty),
                        message = ""
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = uiState.subfolders,
                            key = { it.id }
                        ) { item ->
                            SubfolderRow(
                                item = item,
                                onClick = { onEvent(DriveFolderPickerUiEvent.SubfolderClicked(item)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubfolderRow(
    item: DriveBrowseItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = ComixDimens.spacingMedium,
                vertical = ComixDimens.spacingMedium
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_folder),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ComixDimens.iconMedium)
        )
        Spacer(modifier = Modifier.width(ComixDimens.spacingMedium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.isShortcut) {
                Spacer(modifier = Modifier.height(ComixDimens.spacingXSmall))
                SuggestionChip(
                    onClick = onClick,
                    label = {
                        Text(
                            text = stringResource(R.string.drive_picker_shortcut_label),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }
        Spacer(modifier = Modifier.width(ComixDimens.spacingSmall))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DriveFolderPickerContentPreview() {
    ComixTheme {
        DriveFolderPickerContent(
            uiState = DriveFolderPickerUiState(
                breadcrumbs = listOf(
                    DriveBreadcrumb("root", "My Drive"),
                    DriveBreadcrumb("f1", "Comics")
                ),
                subfolders = listOf(
                    DriveBrowseItem("f2", "Marvel", null, false),
                    DriveBrowseItem("f3", "DC Comics", null, true)
                ),
                isLoading = false,
                isAddingFolder = false
            ),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}
