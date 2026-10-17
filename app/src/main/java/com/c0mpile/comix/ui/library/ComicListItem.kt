package com.c0mpile.comix.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.c0mpile.comix.R
import com.c0mpile.comix.data.archive.ComicCoverRequest
import com.c0mpile.comix.domain.model.Comic
import com.c0mpile.comix.domain.model.ComicAvailability
import com.c0mpile.comix.domain.model.ComicFormat
import com.c0mpile.comix.domain.model.ComicSourceType
import com.c0mpile.comix.domain.model.ReadingProgress
import com.c0mpile.comix.ui.theme.ComixDimens
import com.c0mpile.comix.ui.theme.ComixTheme

/**
 * List item displaying a comic book with a compact 2:3 thumbnail, title, filename, and progress badges.
 */
@Composable
fun ComicListItem(
    comic: Comic,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onInfoClick: (() -> Unit)? = null
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ComixDimens.cardCornerRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = ComixDimens.cardElevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ComixDimens.spacingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(84.dp)
                    .clip(RoundedCornerShape(ComixDimens.badgeCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (comic.availability == ComicAvailability.REMOTE && comic.localUri == null && comic.coverCachePath == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(ComixDimens.spacingXSmall),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cloud),
                            contentDescription = null,
                            modifier = Modifier.size(ComixDimens.iconMedium),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(ComixDimens.spacingXSmall))
                        Text(
                            text = comic.format.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AsyncImage(
                    model = ComicCoverRequest(
                        comicKey = comic.comicKey,
                        localUri = comic.localUri,
                        coverCachePath = comic.coverCachePath
                    ),
                    contentDescription = stringResource(
                        R.string.cd_comic_cover,
                        comic.title
                    ),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(ComixDimens.spacingMedium))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = comic.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val metadataSubtitle = comic.metadata?.let { meta ->
                    meta.formattedSeriesAndNumber ?: meta.writer ?: meta.publisher
                }
                if (!metadataSubtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = metadataSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(ComixDimens.spacingXSmall))

                Text(
                    text = comic.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ComixDimens.spacingSmall)
                ) {
                    if (comic.sourceType == ComicSourceType.GOOGLE_DRIVE) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(ComixDimens.badgeCornerRadius))
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f))
                                .padding(
                                    horizontal = ComixDimens.spacingSmall,
                                    vertical = ComixDimens.spacingXSmall
                                )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ComixDimens.spacingXSmall)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_cloud),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = stringResource(R.string.drive_badge_in_drive),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    val progress = comic.progress
                    val badgeText = when {
                        progress != null && progress.isFinished ->
                            stringResource(R.string.reading_progress_finished)
                        progress != null ->
                            stringResource(
                                R.string.reading_progress_page,
                                progress.currentPage + 1,
                                comic.pageCount
                            )
                        comic.availability == ComicAvailability.REMOTE && comic.pageCount == 0 ->
                            stringResource(R.string.drive_badge_in_drive)
                        comic.pageCount == 1 ->
                            stringResource(R.string.page_count_single)
                        else ->
                            stringResource(R.string.page_count_plural, comic.pageCount)
                    }

                    val badgeBgColor = when {
                        progress != null && progress.isFinished ->
                            MaterialTheme.colorScheme.tertiaryContainer
                        progress != null ->
                            MaterialTheme.colorScheme.secondaryContainer
                        else ->
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                    }

                    val badgeTextColor = when {
                        progress != null && progress.isFinished ->
                            MaterialTheme.colorScheme.onTertiaryContainer
                        progress != null ->
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else ->
                            MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(ComixDimens.badgeCornerRadius))
                            .background(badgeBgColor)
                            .padding(
                                horizontal = ComixDimens.spacingSmall,
                                vertical = ComixDimens.spacingXSmall
                            )
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeTextColor
                        )
                    }
                }

                val progress = comic.progress
                if (progress != null && !progress.isFinished && comic.pageCount > 0) {
                    Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))
                    val progressFraction = (progress.currentPage + 1).toFloat() / comic.pageCount.toFloat()
                    LinearProgressIndicator(
                        progress = { progressFraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ComixDimens.progressIndicatorHeight)
                            .clip(RoundedCornerShape(ComixDimens.badgeCornerRadius)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            if (onInfoClick != null) {
                Spacer(modifier = Modifier.width(ComixDimens.spacingSmall))
                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.cd_comic_info, comic.title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ComicListItemPreview() {
    ComixTheme {
        ComicListItem(
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
                progress = ReadingProgress(
                    comicKey = "sample_key",
                    currentPage = 14,
                    pageCount = 36,
                    isFinished = false,
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
            ),
            onClick = {},
            modifier = Modifier.padding(ComixDimens.spacingMedium)
        )
    }
}
