package com.c0mpile.comix.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.c0mpile.comix.R
import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ComicAvailability
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicMetadata
import com.c0mpile.comix.domain.model.ComicSourceType
import com.c0mpile.comix.ui.theme.ComixDimens
import com.c0mpile.comix.ui.theme.ComixTheme

/**
 * Dialog displaying embedded comic metadata and archive details.
 */
@Composable
fun ComicDetailsDialog(
    comic: Comic,
    onDismiss: () -> Unit,
    onReadComic: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null
) {
    val metadata = comic.metadata

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = metadata?.title ?: comic.title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (metadata != null) {
                    if (!metadata.series.isNullOrBlank()) {
                        MetadataFieldRow(
                            label = stringResource(R.string.comic_details_series),
                            value = metadata.series
                        )
                    }

                    if (metadata.volume != null && metadata.volume > 0) {
                        MetadataFieldRow(
                            label = stringResource(R.string.comic_details_volume),
                            value = metadata.volume.toString()
                        )
                    }

                    if (!metadata.number.isNullOrBlank()) {
                        MetadataFieldRow(
                            label = stringResource(R.string.comic_details_issue),
                            value = metadata.number
                        )
                    }

                    if (metadata.releaseMonth != null || metadata.releaseDay != null) {
                        val releaseDate = metadata.formattedReleaseDate
                        if (!releaseDate.isNullOrBlank()) {
                            MetadataFieldRow(
                                label = stringResource(R.string.comic_details_release_date),
                                value = releaseDate
                            )
                        }
                    } else if (metadata.releaseYear != null && metadata.releaseYear > 0) {
                        MetadataFieldRow(
                            label = stringResource(R.string.comic_details_year),
                            value = metadata.releaseYear.toString()
                        )
                    }

                    if (!metadata.writer.isNullOrBlank()) {
                        MetadataFieldRow(
                            label = stringResource(R.string.comic_details_writer),
                            value = metadata.writer
                        )
                    }

                    if (!metadata.penciller.isNullOrBlank()) {
                        MetadataFieldRow(
                            label = stringResource(R.string.comic_details_penciller),
                            value = metadata.penciller
                        )
                    }

                    if (!metadata.publisher.isNullOrBlank()) {
                        MetadataFieldRow(
                            label = stringResource(R.string.comic_details_publisher),
                            value = metadata.publisher
                        )
                    }

                    if (!metadata.genre.isNullOrBlank()) {
                        MetadataFieldRow(
                            label = stringResource(R.string.comic_details_genre),
                            value = metadata.genre
                        )
                    }

                    if (!metadata.summary.isNullOrBlank()) {
                        MetadataFieldRow(
                            label = stringResource(R.string.comic_details_summary),
                            value = metadata.summary
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.comic_details_no_embedded_metadata),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = ComixDimens.spacingSmall)
                    )
                }

                Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))

                MetadataFieldRow(
                    label = stringResource(R.string.comic_details_file_name),
                    value = comic.fileName
                )

                Text(
                    text = stringResource(
                        R.string.comic_details_format_pages,
                        comic.format.name,
                        comic.pageCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onReadComic) {
                Text(text = stringResource(R.string.action_read_comic))
            }
        },
        dismissButton = {
            Row {
                if (onEdit != null) {
                    TextButton(onClick = onEdit) {
                        Text(text = stringResource(R.string.action_edit))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.action_close))
                }
            }
        }
    )
}

@Composable
private fun MetadataFieldRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ComicDetailsDialogPreview() {
    ComixTheme {
        ComicDetailsDialog(
            comic = Comic(
                comicKey = "sample_key",
                title = "Amazing Fantasy #15",
                fileName = "Amazing Fantasy #15.cbz",
                format = ComicFormat.CBZ,
                sourceType = ComicSourceType.LOCAL_SAF,
                availability = ComicAvailability.LOCAL,
                sizeBytes = 25_000_000L,
                lastModifiedEpochMillis = System.currentTimeMillis(),
                pageCount = 36,
                localUri = null,
                folderUri = null,
                metadata = ComicMetadata(
                    title = "Spider-Man!",
                    series = "Amazing Fantasy",
                    number = "15",
                    volume = 1,
                    summary = "Introducing Peter Parker, the Amazing Spider-Man!",
                    writer = "Stan Lee",
                    penciller = "Steve Ditko",
                    publisher = "Marvel",
                    genre = "Superhero",
                    releaseYear = 1962,
                    releaseMonth = 8,
                    releaseDay = 10
                )
            ),
            onDismiss = {},
            onReadComic = {}
        )
    }
}
