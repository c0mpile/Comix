package com.c0mpile.comix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.c0mpile.comix.ui.theme.ComixDimens
import com.c0mpile.comix.ui.theme.ComixTheme

/**
 * Reusable error state view displaying an error icon, title, message, and action buttons.
 */
@Composable
fun ErrorStateView(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Warning,
    retryLabel: String? = null,
    onRetryClick: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ComixDimens.spacingLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(ComixDimens.iconXLarge),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(ComixDimens.spacingMedium))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(ComixDimens.spacingSmall))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if ((retryLabel != null && onRetryClick != null) || (secondaryLabel != null && onSecondaryClick != null)) {
            Spacer(modifier = Modifier.height(ComixDimens.spacingLarge))

            Row(horizontalArrangement = Arrangement.Center) {
                if (secondaryLabel != null && onSecondaryClick != null) {
                    OutlinedButton(onClick = onSecondaryClick) {
                        Text(text = secondaryLabel)
                    }
                    if (retryLabel != null && onRetryClick != null) {
                        Spacer(modifier = Modifier.width(ComixDimens.spacingMedium))
                    }
                }

                if (retryLabel != null && onRetryClick != null) {
                    Button(onClick = onRetryClick) {
                        Text(text = retryLabel)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorStateViewPreview() {
    ComixTheme {
        ErrorStateView(
            title = "Corrupt archive",
            message = "This archive cannot be opened because its file headers are damaged.",
            retryLabel = "Retry",
            onRetryClick = {},
            secondaryLabel = "Back",
            onSecondaryClick = {}
        )
    }
}
