package com.c0mpile.comix.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.c0mpile.comix.R
import com.c0mpile.comix.domain.model.AppThemeMode
import com.c0mpile.comix.domain.model.DriveAccount
import com.c0mpile.comix.domain.model.DriveAuthState
import com.c0mpile.comix.domain.model.DriveFolder
import com.c0mpile.comix.domain.model.LibraryFolder
import com.c0mpile.comix.domain.model.ReaderScaleMode
import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.domain.model.UserSettings
import com.c0mpile.comix.ui.theme.ComixDimens
import com.c0mpile.comix.ui.theme.ComixTheme

/**
 * Stateful host for the Application Settings screen.
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToDriveFolderPicker: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onEvent(SettingsUiEvent.SnackbarDismissed)
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val activity = context.findActivity()
        if (activity != null) {
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.onEvent(SettingsUiEvent.DriveConsentResult(activity, result.data))
            } else {
                viewModel.onEvent(SettingsUiEvent.DriveConsentResult(activity, null))
            }
        }
    }

    LaunchedEffect(uiState.pendingConsentIntent) {
        val pendingIntent = uiState.pendingConsentIntent
        if (pendingIntent != null) {
            val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            consentLauncher.launch(request)
        }
    }

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
            viewModel.onEvent(SettingsUiEvent.FolderPicked(uri.toString()))
        }
    }

    SettingsContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onAddFolderClick = { folderPickerLauncher.launch(null) },
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        onNavigateToDriveFolderPicker = onNavigateToDriveFolderPicker,
        snackbarHostState = snackbarHostState
    )
}

/**
 * Stateless content composable for the Application Settings screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    onAddFolderClick: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToDriveFolderPicker: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Appearance section
            item(key = "section_appearance") {
                SettingsSectionHeader(title = stringResource(R.string.settings_section_appearance))
                ThemeModeSelector(
                    selectedMode = uiState.userSettings.themeMode,
                    onModeSelected = { onEvent(SettingsUiEvent.ThemeModeSelected(it)) },
                    modifier = Modifier.padding(
                        horizontal = ComixDimens.spacingMedium,
                        vertical = ComixDimens.spacingSmall
                    )
                )
                Spacer(modifier = Modifier.height(ComixDimens.spacingMedium))
                HorizontalDivider(modifier = Modifier.padding(horizontal = ComixDimens.spacingMedium))
            }

            // Reader section
            item(key = "section_reader") {
                Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))
                SettingsSectionHeader(title = stringResource(R.string.settings_section_reader))

                ReadingDirectionSelector(
                    selectedDirection = uiState.userSettings.readingDirection,
                    onDirectionSelected = { onEvent(SettingsUiEvent.ReadingDirectionSelected(it)) },
                    modifier = Modifier.padding(
                        horizontal = ComixDimens.spacingMedium,
                        vertical = ComixDimens.spacingSmall
                    )
                )

                Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))

                ReaderScaleModeSelector(
                    selectedScaleMode = uiState.userSettings.readerScaleMode,
                    onScaleModeSelected = { onEvent(SettingsUiEvent.ReaderScaleModeSelected(it)) },
                    modifier = Modifier.padding(
                        horizontal = ComixDimens.spacingMedium,
                        vertical = ComixDimens.spacingSmall
                    )
                )

                Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))

                KeepScreenOnRow(
                    enabled = uiState.userSettings.keepScreenOn,
                    onToggle = { onEvent(SettingsUiEvent.KeepScreenOnToggled(it)) },
                    modifier = Modifier.padding(
                        horizontal = ComixDimens.spacingMedium,
                        vertical = ComixDimens.spacingSmall
                    )
                )

                Spacer(modifier = Modifier.height(ComixDimens.spacingMedium))
                HorizontalDivider(modifier = Modifier.padding(horizontal = ComixDimens.spacingMedium))
            }

            // Library Folders section
            item(key = "section_folders") {
                Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))
                SettingsSectionHeader(title = stringResource(R.string.settings_section_folders))

                if (uiState.folders.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_folders_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = ComixDimens.spacingMedium,
                            vertical = ComixDimens.spacingSmall
                        )
                    )
                }
            }

            items(
                items = uiState.folders,
                key = { it.folderUri }
            ) { folder ->
                FolderItem(
                    folder = folder,
                    onRemove = { onEvent(SettingsUiEvent.RemoveFolderRequested(folder.folderUri)) },
                    modifier = Modifier.padding(
                        horizontal = ComixDimens.spacingMedium,
                        vertical = ComixDimens.spacingXSmall
                    )
                )
            }

            item(key = "add_folder_action") {
                Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))
                Button(
                    onClick = onAddFolderClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = ComixDimens.spacingMedium,
                            vertical = ComixDimens.spacingSmall
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(ComixDimens.spacingSmall))
                    Text(text = stringResource(R.string.settings_add_folder))
                }
                Spacer(modifier = Modifier.height(ComixDimens.spacingMedium))
                HorizontalDivider(modifier = Modifier.padding(horizontal = ComixDimens.spacingMedium))
            }

            // Google Drive section
            item(key = "section_drive") {
                Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))
                SettingsSectionHeader(title = stringResource(R.string.settings_section_drive))
            }

            when (val authState = uiState.driveAuthState) {
                is DriveAuthState.SignedOut -> {
                    item(key = "drive_signed_out") {
                        val context = LocalContext.current
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = ComixDimens.spacingMedium,
                                    vertical = ComixDimens.spacingSmall
                                )
                        ) {
                            Button(
                                onClick = {
                                    context.findActivity()?.let { activity ->
                                        onEvent(SettingsUiEvent.SignInToDriveClicked(activity))
                                    }
                                },
                                enabled = !uiState.isDriveBusy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (uiState.isDriveBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(ComixDimens.iconSmall),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(ComixDimens.spacingSmall))
                                }
                                Text(text = stringResource(R.string.settings_drive_sign_in))
                            }
                        }
                    }
                }

                is DriveAuthState.ReauthRequired -> {
                    item(key = "drive_reauth_required") {
                        val context = LocalContext.current
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = ComixDimens.spacingMedium,
                                    vertical = ComixDimens.spacingSmall
                                )
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(ComixDimens.cardCornerRadius)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(ComixDimens.spacingMedium),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.width(ComixDimens.spacingSmall))
                                    Text(
                                        text = stringResource(R.string.settings_drive_reauth_required),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))
                            Button(
                                onClick = {
                                    context.findActivity()?.let { activity ->
                                        onEvent(SettingsUiEvent.SignInToDriveClicked(activity))
                                    }
                                },
                                enabled = !uiState.isDriveBusy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (uiState.isDriveBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(ComixDimens.iconSmall),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(ComixDimens.spacingSmall))
                                }
                                Text(text = stringResource(R.string.settings_drive_sign_in_again))
                            }
                        }
                    }
                }

                is DriveAuthState.SignedIn -> {
                    item(key = "drive_signed_in_header") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = ComixDimens.spacingMedium,
                                    vertical = ComixDimens.spacingSmall
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(ComixDimens.cardCornerRadius)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(ComixDimens.spacingMedium),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(
                                            R.string.settings_drive_signed_in_as,
                                            authState.account.email
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(ComixDimens.spacingSmall))
                                OutlinedButton(
                                    onClick = { onEvent(SettingsUiEvent.SignOutOfDriveClicked) }
                                ) {
                                    Text(text = stringResource(R.string.settings_drive_sign_out))
                                }
                            }
                        }

                        if (uiState.driveFolders.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_drive_folders_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    horizontal = ComixDimens.spacingMedium,
                                    vertical = ComixDimens.spacingSmall
                                )
                            )
                        }
                    }

                    items(
                        items = uiState.driveFolders,
                        key = { it.folderId }
                    ) { folder ->
                        DriveFolderItem(
                            folder = folder,
                            onRemove = {
                                onEvent(SettingsUiEvent.RemoveDriveFolderRequested(folder.folderId))
                            },
                            modifier = Modifier.padding(
                                horizontal = ComixDimens.spacingMedium,
                                vertical = ComixDimens.spacingXSmall
                            )
                        )
                    }

                    item(key = "add_drive_folder_action") {
                        Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))
                        Button(
                            onClick = onNavigateToDriveFolderPicker,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = ComixDimens.spacingMedium,
                                    vertical = ComixDimens.spacingSmall
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(ComixDimens.spacingSmall))
                            Text(text = stringResource(R.string.settings_drive_add_folder))
                        }
                        Spacer(modifier = Modifier.height(ComixDimens.spacingLarge))
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(
            horizontal = ComixDimens.spacingMedium,
            vertical = ComixDimens.spacingSmall
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(
    selectedMode: AppThemeMode,
    onModeSelected: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_theme_title),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val modes = listOf(
                AppThemeMode.SYSTEM to R.string.settings_theme_system,
                AppThemeMode.LIGHT to R.string.settings_theme_light,
                AppThemeMode.DARK to R.string.settings_theme_dark
            )
            modes.forEachIndexed { index, (mode, labelRes) ->
                SegmentedButton(
                    selected = selectedMode == mode,
                    onClick = { onModeSelected(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
                ) {
                    Text(text = stringResource(labelRes))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingDirectionSelector(
    selectedDirection: ReadingDirection,
    onDirectionSelected: (ReadingDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_reading_direction_title),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val directions = listOf(
                ReadingDirection.LEFT_TO_RIGHT to R.string.settings_reading_direction_ltr,
                ReadingDirection.RIGHT_TO_LEFT to R.string.settings_reading_direction_rtl
            )
            directions.forEachIndexed { index, (direction, labelRes) ->
                SegmentedButton(
                    selected = selectedDirection == direction,
                    onClick = { onDirectionSelected(direction) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = directions.size)
                ) {
                    Text(text = stringResource(labelRes))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScaleModeSelector(
    selectedScaleMode: ReaderScaleMode,
    onScaleModeSelected: (ReaderScaleMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_scale_mode_title),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val scaleModes = listOf(
                ReaderScaleMode.FIT_SCREEN to R.string.settings_scale_mode_fit_screen,
                ReaderScaleMode.FIT_WIDTH to R.string.settings_scale_mode_fit_width
            )
            scaleModes.forEachIndexed { index, (scaleMode, labelRes) ->
                SegmentedButton(
                    selected = selectedScaleMode == scaleMode,
                    onClick = { onScaleModeSelected(scaleMode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = scaleModes.size)
                ) {
                    Text(text = stringResource(labelRes))
                }
            }
        }
    }
}

@Composable
private fun KeepScreenOnRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggle(!enabled) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_keep_screen_on_title),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(ComixDimens.spacingXSmall))
            Text(
                text = stringResource(R.string.settings_keep_screen_on_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(ComixDimens.spacingMedium))
        Switch(
            checked = enabled,
            onCheckedChange = onToggle
        )
    }
}

@Composable
private fun FolderItem(
    folder: LibraryFolder,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ComixDimens.cardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ComixDimens.spacingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(ComixDimens.spacingXSmall))
                Text(
                    text = folder.folderUri,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!folder.hasPersistedPermission) {
                    Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ComixDimens.spacingXSmall)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(R.string.settings_folder_permission_revoked),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(ComixDimens.spacingSmall))

            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(
                        R.string.settings_folder_remove_cd,
                        folder.displayName
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DriveFolderItem(
    folder: DriveFolder,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ComixDimens.cardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ComixDimens.spacingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(ComixDimens.spacingSmall))

            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(
                        R.string.settings_drive_remove_folder_cd,
                        folder.displayName
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentPreview() {
    ComixTheme {
        SettingsContent(
            uiState = SettingsUiState(
                userSettings = UserSettings(),
                folders = listOf(
                    LibraryFolder(
                        folderUri = "content://com.android.externalstorage.documents/tree/primary%3AComics",
                        displayName = "Comics",
                        addedAtEpochMillis = 1_000_000L,
                        lastScannedAtEpochMillis = 2_000_000L,
                        hasPersistedPermission = true
                    ),
                    LibraryFolder(
                        folderUri = "content://com.android.externalstorage.documents/tree/primary%3AManga",
                        displayName = "Manga",
                        addedAtEpochMillis = 1_000_000L,
                        lastScannedAtEpochMillis = null,
                        hasPersistedPermission = false
                    )
                ),
                driveAuthState = DriveAuthState.SignedIn(
                    DriveAccount(email = "reader@example.com", signedInAtEpochMillis = 1_000_000L)
                ),
                driveFolders = listOf(
                    DriveFolder(
                        folderId = "folder_1",
                        displayName = "Comics Drive",
                        addedAtEpochMillis = 1_000_000L,
                        lastScannedAtEpochMillis = null
                    )
                )
            ),
            onEvent = {},
            onAddFolderClick = {},
            onNavigateBack = {}
        )
    }
}
