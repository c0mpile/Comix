package com.c0mpile.comix.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.c0mpile.comix.R
import com.c0mpile.comix.domain.model.BulkComicMetadataUpdate
import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.SingleComicMetadataUpdate

/**
 * Dialog for editing metadata of a single comic issue.
 */
@Composable
fun EditComicMetadataDialog(
    comic: Comic,
    onDismiss: () -> Unit,
    onSave: (SingleComicMetadataUpdate) -> Unit,
    modifier: Modifier = Modifier
) {
    var series by remember { mutableStateOf(comic.metadata?.series ?: "") }
    var title by remember { mutableStateOf(comic.metadata?.title ?: comic.title) }
    var number by remember { mutableStateOf(comic.metadata?.number ?: "") }
    var yearText by remember { mutableStateOf(comic.metadata?.releaseYear?.toString() ?: "") }
    var writer by remember { mutableStateOf(comic.metadata?.writer ?: "") }
    var penciller by remember { mutableStateOf(comic.metadata?.penciller ?: "") }
    var publisher by remember { mutableStateOf(comic.metadata?.publisher ?: "") }
    var description by remember { mutableStateOf(comic.metadata?.summary ?: "") }

    val isYearError = yearText.isNotBlank() && (yearText.toIntOrNull() == null || yearText.toInt() !in 1..9999)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = stringResource(R.string.metadata_edit_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = series,
                    onValueChange = { series = it },
                    label = { Text(stringResource(R.string.metadata_field_series)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("metadata_field_series")
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.metadata_field_title)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("metadata_field_title")
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text(stringResource(R.string.metadata_field_issue)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("metadata_field_issue")
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = yearText,
                    onValueChange = { yearText = it },
                    label = { Text(stringResource(R.string.metadata_field_year)) },
                    singleLine = true,
                    isError = isYearError,
                    supportingText = if (isYearError) {
                        { Text(stringResource(R.string.metadata_error_invalid_year)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("metadata_field_year")
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = writer,
                    onValueChange = { writer = it },
                    label = { Text(stringResource(R.string.metadata_field_writer)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("metadata_field_writer")
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = penciller,
                    onValueChange = { penciller = it },
                    label = { Text(stringResource(R.string.metadata_field_penciller)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("metadata_field_penciller")
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = publisher,
                    onValueChange = { publisher = it },
                    label = { Text(stringResource(R.string.metadata_field_publisher)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("metadata_field_publisher")
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.metadata_field_description)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("metadata_field_description")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedYear = yearText.trim().toIntOrNull()?.takeIf { it in 1..9999 }
                    val update = SingleComicMetadataUpdate(
                        comicKey = comic.comicKey,
                        series = series.trim().ifBlank { null },
                        title = title.trim().ifBlank { null },
                        number = number.trim().ifBlank { null },
                        releaseYear = parsedYear,
                        writer = writer.trim().ifBlank { null },
                        penciller = penciller.trim().ifBlank { null },
                        publisher = publisher.trim().ifBlank { null },
                        summary = description.trim().ifBlank { null }
                    )
                    onSave(update)
                },
                enabled = !isYearError
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * Dialog for bulk-editing metadata across multiple selected comic issues.
 */
@Composable
fun BulkEditComicMetadataDialog(
    comics: List<Comic>,
    onDismiss: () -> Unit,
    onSave: (BulkComicMetadataUpdate) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialSeries = remember(comics) {
        val seriesSet = comics.map { it.metadata?.series?.trim()?.ifBlank { null } }.toSet()
        if (seriesSet.size == 1 && seriesSet.first() != null) seriesSet.first()!! else ""
    }
    val initialYear = remember(comics) {
        val yearSet = comics.map { it.metadata?.releaseYear?.takeIf { y -> y > 0 } }.toSet()
        if (yearSet.size == 1 && yearSet.first() != null) yearSet.first()!!.toString() else ""
    }
    val initialWriter = remember(comics) {
        val writerSet = comics.map { it.metadata?.writer?.trim()?.ifBlank { null } }.toSet()
        if (writerSet.size == 1 && writerSet.first() != null) writerSet.first()!! else ""
    }
    val initialPenciller = remember(comics) {
        val pencillerSet = comics.map { it.metadata?.penciller?.trim()?.ifBlank { null } }.toSet()
        if (pencillerSet.size == 1 && pencillerSet.first() != null) pencillerSet.first()!! else ""
    }
    val initialPublisher = remember(comics) {
        val publisherSet = comics.map { it.metadata?.publisher?.trim()?.ifBlank { null } }.toSet()
        if (publisherSet.size == 1 && publisherSet.first() != null) publisherSet.first()!! else ""
    }

    var seriesText by remember { mutableStateOf(initialSeries) }
    var yearText by remember { mutableStateOf(initialYear) }
    var writerText by remember { mutableStateOf(initialWriter) }
    var pencillerText by remember { mutableStateOf(initialPenciller) }
    var publisherText by remember { mutableStateOf(initialPublisher) }

    val isYearError = yearText != initialYear && yearText.isNotBlank() &&
        (yearText.toIntOrNull() == null || yearText.toInt() !in 1..9999)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = stringResource(R.string.metadata_bulk_edit_title, comics.size),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = seriesText,
                    onValueChange = { seriesText = it },
                    label = { Text(stringResource(R.string.metadata_field_series)) },
                    placeholder = if (initialSeries.isEmpty()) {
                        { Text(stringResource(R.string.metadata_multiple_values)) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("metadata_field_series")
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = yearText,
                    onValueChange = { yearText = it },
                    label = { Text(stringResource(R.string.metadata_field_year)) },
                    placeholder = if (initialYear.isEmpty()) {
                        { Text(stringResource(R.string.metadata_multiple_values)) }
                    } else null,
                    singleLine = true,
                    isError = isYearError,
                    supportingText = if (isYearError) {
                        { Text(stringResource(R.string.metadata_error_invalid_year)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("metadata_field_year")
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = writerText,
                    onValueChange = { writerText = it },
                    label = { Text(stringResource(R.string.metadata_field_writer)) },
                    placeholder = if (initialWriter.isEmpty()) {
                        { Text(stringResource(R.string.metadata_multiple_values)) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("metadata_field_writer")
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pencillerText,
                    onValueChange = { pencillerText = it },
                    label = { Text(stringResource(R.string.metadata_field_penciller)) },
                    placeholder = if (initialPenciller.isEmpty()) {
                        { Text(stringResource(R.string.metadata_multiple_values)) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("metadata_field_penciller")
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = publisherText,
                    onValueChange = { publisherText = it },
                    label = { Text(stringResource(R.string.metadata_field_publisher)) },
                    placeholder = if (initialPublisher.isEmpty()) {
                        { Text(stringResource(R.string.metadata_multiple_values)) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("metadata_field_publisher")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val update = BulkComicMetadataUpdate(
                        comicKeys = comics.map { it.comicKey },
                        series = seriesText.trim().ifBlank { null },
                        updateSeries = (seriesText != initialSeries),
                        releaseYear = yearText.trim().toIntOrNull()?.takeIf { it in 1..9999 },
                        updateReleaseYear = (yearText != initialYear),
                        writer = writerText.trim().ifBlank { null },
                        updateWriter = (writerText != initialWriter),
                        penciller = pencillerText.trim().ifBlank { null },
                        updatePenciller = (pencillerText != initialPenciller),
                        publisher = publisherText.trim().ifBlank { null },
                        updatePublisher = (publisherText != initialPublisher)
                    )
                    onSave(update)
                },
                enabled = !isYearError
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
