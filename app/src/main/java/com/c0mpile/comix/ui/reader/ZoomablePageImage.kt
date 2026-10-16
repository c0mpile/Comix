package com.c0mpile.comix.ui.reader

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import com.c0mpile.comix.domain.model.ReaderScaleMode
import com.c0mpile.comix.domain.model.ReadingDirection
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Renders a single comic page bitmap with pinch-to-zoom, pan clamping, double-tap zoom,
 * and tap zone detection coordinated with the reader pager.
 */
@Composable
fun ZoomablePageImage(
    bitmap: Bitmap,
    contentDescription: String?,
    isCurrentPage: Boolean,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onToggleHud: () -> Unit,
    modifier: Modifier = Modifier,
    scaleMode: ReaderScaleMode = ReaderScaleMode.FIT_SCREEN,
    readingDirection: ReadingDirection = ReadingDirection.LEFT_TO_RIGHT
) {
    val coroutineScope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scaleAnim.snapTo(1f)
            offsetXAnim.snapTo(0f)
            offsetYAnim.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(readingDirection) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        coroutineScope.launch {
                            if (scaleAnim.value > 1.01f) {
                                launch { scaleAnim.animateTo(1f) }
                                launch { offsetXAnim.animateTo(0f) }
                                launch { offsetYAnim.animateTo(0f) }
                            } else {
                                val targetScale = 2.5f
                                val maxOffsetX = maxOf(0f, (size.width * (targetScale - 1f)) / 2f)
                                val maxOffsetY = maxOf(0f, (size.height * (targetScale - 1f)) / 2f)

                                val contentCenterX = size.width / 2f
                                val contentCenterY = size.height / 2f
                                val targetOffsetX =
                                    ((contentCenterX - tapOffset.x) * (targetScale - 1f))
                                        .coerceIn(-maxOffsetX, maxOffsetX)
                                val targetOffsetY =
                                    ((contentCenterY - tapOffset.y) * (targetScale - 1f))
                                        .coerceIn(-maxOffsetY, maxOffsetY)

                                launch { scaleAnim.animateTo(targetScale) }
                                launch { offsetXAnim.animateTo(targetOffsetX) }
                                launch { offsetYAnim.animateTo(targetOffsetY) }
                            }
                        }
                    },
                    onTap = { tapOffset ->
                        if (scaleAnim.value > 1.01f) {
                            onToggleHud()
                        } else {
                            val width = size.width
                            val isLeftTap = tapOffset.x < width * 0.25f
                            val isRightTap = tapOffset.x > width * 0.75f
                            when {
                                isLeftTap -> {
                                    if (readingDirection == ReadingDirection.RIGHT_TO_LEFT) {
                                        onNextPage()
                                    } else {
                                        onPreviousPage()
                                    }
                                }
                                isRightTap -> {
                                    if (readingDirection == ReadingDirection.RIGHT_TO_LEFT) {
                                        onPreviousPage()
                                    } else {
                                        onNextPage()
                                    }
                                }
                                else -> onToggleHud()
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var pastTouchSlop = false
                    val touchSlop = viewConfiguration.touchSlop
                    var panAccumulator = Offset.Zero

                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (canceled) break

                        val pointerCount = event.changes.count { it.pressed }
                        if (pointerCount >= 2) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            val currentScale = (scaleAnim.value * zoomChange).coerceIn(1f, 4f)
                            val maxOffsetX = maxOf(0f, (size.width * (currentScale - 1f)) / 2f)
                            val maxOffsetY = maxOf(0f, (size.height * (currentScale - 1f)) / 2f)

                            val currentOffsetX =
                                (offsetXAnim.value + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
                            val currentOffsetY =
                                (offsetYAnim.value + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)

                            coroutineScope.launch {
                                scaleAnim.snapTo(currentScale)
                                offsetXAnim.snapTo(currentOffsetX)
                                offsetYAnim.snapTo(currentOffsetY)
                            }

                            event.changes.forEach { it.consume() }
                        } else if (pointerCount == 1 && scaleAnim.value > 1.01f) {
                            val panChange = event.calculatePan()
                            if (!pastTouchSlop) {
                                panAccumulator += panChange
                                if (panAccumulator.getDistance() > touchSlop) {
                                    pastTouchSlop = true
                                }
                            }

                            if (pastTouchSlop) {
                                val currentScale = scaleAnim.value
                                val maxOffsetX = maxOf(0f, (size.width * (currentScale - 1f)) / 2f)
                                val maxOffsetY = maxOf(0f, (size.height * (currentScale - 1f)) / 2f)

                                val targetX = offsetXAnim.value + panChange.x
                                val targetY =
                                    (offsetYAnim.value + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)

                                val isAtHorizontalEdge =
                                    (targetX > maxOffsetX && panChange.x > 0) ||
                                            (targetX < -maxOffsetX && panChange.x < 0)
                                val isPrimarilyHorizontal = abs(panChange.x) > abs(panChange.y)

                                if (isAtHorizontalEdge && isPrimarilyHorizontal) {
                                    coroutineScope.launch {
                                        offsetYAnim.snapTo(targetY)
                                    }
                                } else {
                                    val clampedX = targetX.coerceIn(-maxOffsetX, maxOffsetX)
                                    coroutineScope.launch {
                                        offsetXAnim.snapTo(clampedX)
                                        offsetYAnim.snapTo(targetY)
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = if (scaleMode == ReaderScaleMode.FIT_WIDTH) ContentScale.FillWidth else ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                    translationX = offsetXAnim.value
                    translationY = offsetYAnim.value
                }
        )
    }
}
