package com.c0mpile.comix.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.c0mpile.comix.R
import com.c0mpile.comix.domain.model.ReaderScaleMode
import com.c0mpile.comix.domain.model.ReadingDirection
import com.c0mpile.comix.ui.theme.ComixDimens
import com.c0mpile.comix.ui.theme.ComixTheme
import kotlin.math.roundToInt

/**
 * Overlay HUD displaying the reader top app bar, bottom page slider scrubber,
 * and persistent translucent page pill indicator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderOverlayHud(
    isVisible: Boolean,
    title: String,
    currentPage: Int,
    pageCount: Int,
    onPageSelected: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    readingDirection: ReadingDirection = ReadingDirection.LEFT_TO_RIGHT,
    scaleMode: ReaderScaleMode = ReaderScaleMode.FIT_SCREEN,
    onToggleReadingDirection: () -> Unit = {},
    onToggleScaleMode: () -> Unit = {}
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = ComixDimens.hudElevation,
                modifier = Modifier.fillMaxWidth()
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    actions = {
                        val directionLabel = if (readingDirection == ReadingDirection.LEFT_TO_RIGHT) {
                            stringResource(R.string.reader_reading_direction_ltr)
                        } else {
                            stringResource(R.string.reader_reading_direction_rtl)
                        }
                        val directionCd = stringResource(
                            R.string.reader_reading_direction_toggle_cd,
                            directionLabel
                        )
                        TextButton(
                            onClick = onToggleReadingDirection,
                            modifier = Modifier.semantics { contentDescription = directionCd }
                        ) {
                            Text(
                                text = directionLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        val scaleLabel = if (scaleMode == ReaderScaleMode.FIT_SCREEN) {
                            stringResource(R.string.reader_scale_mode_fit_screen)
                        } else {
                            stringResource(R.string.reader_scale_mode_fit_width)
                        }
                        val scaleCd = stringResource(
                            R.string.reader_scale_mode_toggle_cd,
                            scaleLabel
                        )
                        TextButton(
                            onClick = onToggleScaleMode,
                            modifier = Modifier.semantics { contentDescription = scaleCd }
                        ) {
                            Text(
                                text = scaleLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier.statusBarsPadding()
                )
            }
        }

        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = ComixDimens.hudElevation,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = ComixDimens.hudScrubberPadding, vertical = ComixDimens.spacingSmall)
                ) {
                    val sliderCd = stringResource(R.string.reader_slider_cd)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (pageCount > 1) {
                            Slider(
                                value = currentPage.toFloat(),
                                onValueChange = { onPageSelected(it.roundToInt()) },
                                valueRange = 0f..(pageCount - 1).toFloat(),
                                steps = (pageCount - 2).coerceAtLeast(0),
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = sliderCd }
                            )
                            Spacer(modifier = Modifier.width(ComixDimens.spacingMedium))
                        }

                        Text(
                            text = stringResource(
                                R.string.reader_page_indicator,
                                currentPage + 1,
                                pageCount
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !isVisible && pageCount > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(ComixDimens.pageIndicatorPadding)
        ) {
            val pillCd = stringResource(R.string.reader_page_indicator_cd, currentPage + 1, pageCount)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(ComixDimens.pillCornerRadius))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                    .padding(
                        horizontal = ComixDimens.spacingMedium,
                        vertical = ComixDimens.spacingSmall
                    )
                    .semantics { contentDescription = pillCd }
            ) {
                Text(
                    text = stringResource(
                        R.string.reader_page_indicator,
                        currentPage + 1,
                        pageCount
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReaderOverlayHudVisiblePreview() {
    ComixTheme {
        ReaderOverlayHud(
            isVisible = true,
            title = "Action Comics #1",
            currentPage = 3,
            pageCount = 64,
            onPageSelected = {},
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReaderOverlayHudHiddenPreview() {
    ComixTheme {
        ReaderOverlayHud(
            isVisible = false,
            title = "Action Comics #1",
            currentPage = 3,
            pageCount = 64,
            onPageSelected = {},
            onNavigateBack = {}
        )
    }
}
