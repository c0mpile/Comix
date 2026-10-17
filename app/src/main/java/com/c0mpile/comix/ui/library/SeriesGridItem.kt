package com.c0mpile.comix.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import com.c0mpile.comix.domain.model.ComicSeries
import com.c0mpile.comix.domain.model.ComicSourceType
import com.c0mpile.comix.ui.theme.ComixDimens
import com.c0mpile.comix.ui.theme.ComixTheme

/**
 * Grid item displaying a grouped comic series cover, title, and issue count.
 */
@Composable
fun SeriesGridItem(
    series: ComicSeries,
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
                AsyncImage(
                    model = ComicCoverRequest(
                        comicKey = series.coverComic.comicKey,
                        localUri = series.coverComic.localUri,
                        coverCachePath = series.coverComic.coverCachePath,
                        driveFileId = series.coverComic.driveFileId,
                        sizeBytes = series.coverComic.sizeBytes
                    ),
                    contentDescription = stringResource(
                        R.string.cover_content_description,
                        series.name
                    ),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(ComixDimens.spacingSmall)
                        .clip(RoundedCornerShape(ComixDimens.badgeCornerRadius))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .padding(
                            horizontal = ComixDimens.spacingSmall,
                            vertical = ComixDimens.spacingXSmall
                        )
                ) {
                    Text(
                        text = if (series.issueCount == 1) {
                            stringResource(R.string.series_issue_count_one)
                        } else {
                            stringResource(R.string.series_issue_count_many, series.issueCount)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))

            Text(
                text = series.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = if (series.issueCount == 1) {
                    stringResource(R.string.series_issue_count_one)
                } else {
                    stringResource(R.string.series_issue_count_many, series.issueCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SeriesGridItemPreview() {
    ComixTheme {
        val sampleComic = Comic(
            comicKey = "sample_1",
            title = "Batman #1",
            fileName = "Batman #1.cbz",
            format = ComicFormat.CBZ,
            sourceType = ComicSourceType.LOCAL_SAF,
            availability = ComicAvailability.LOCAL,
            sizeBytes = 10_000_000L,
            lastModifiedEpochMillis = System.currentTimeMillis(),
            pageCount = 32,
            localUri = null,
            folderUri = null
        )
        val sampleSeries = ComicSeries(
            seriesKey = "batman",
            name = "Batman",
            comics = listOf(sampleComic),
            coverComic = sampleComic
        )
        SeriesGridItem(
            series = sampleSeries,
            onClick = {}
        )
    }
}
