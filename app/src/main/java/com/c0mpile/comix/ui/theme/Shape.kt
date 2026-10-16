package com.c0mpile.comix.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * Standard dimension tokens for spacing, sizing, and elevation across Comix composables.
 */
object ComixDimens {
    val spacingNone = 0.dp
    val spacingXSmall = 4.dp
    val spacingSmall = 8.dp
    val spacingMedium = 16.dp
    val spacingLarge = 24.dp
    val spacingXLarge = 32.dp

    val iconSmall = 18.dp
    val iconMedium = 24.dp
    val iconLarge = 48.dp
    val iconXLarge = 64.dp

    val gridMinCellSize = 150.dp
    val gridItemPadding = 8.dp
    val coverAspectRatio = 2f / 3f

    val cardCornerRadius = 12.dp
    val badgeCornerRadius = 6.dp
    val bannerCornerRadius = 12.dp

    val cardElevation = 2.dp
    val progressIndicatorHeight = 4.dp
}
