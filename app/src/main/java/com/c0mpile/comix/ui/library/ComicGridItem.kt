package com.c0mpile.comix.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
 * Grid item displaying a comic book cover, title, and page count or progress badge.
 */
@Composable
fun ComicGridItem(
    comic: Comic,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ComixDimens.cardCornerRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = ComixDimens.cardElevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(ComixDimens.spacingSmall)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ComixDimens.coverAspectRatio)
                    .clip(RoundedCornerShape(ComixDimens.cardCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (comic.availability == ComicAvailability.REMOTE && comic.localUri == null && comic.coverCachePath == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(ComixDimens.spacingMedium),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cloud),
                            contentDescription = null,
                            modifier = Modifier.size(ComixDimens.iconLarge),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))
                        Text(
                            text = comic.format.name,
                            style = MaterialTheme.typography.labelMedium,
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
                        R.string.cover_content_description,
                        comic.title
                    ),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (comic.sourceType == ComicSourceType.GOOGLE_DRIVE) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(ComixDimens.spacingSmall)
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
                        .align(Alignment.BottomEnd)
                        .padding(ComixDimens.spacingSmall)
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

            Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))

            Text(
                text = comic.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ComicGridItemPreview() {
    ComixTheme {
        ComicGridItem(
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
