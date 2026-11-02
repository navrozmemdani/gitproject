package com.visualtrace.ai.features.image_search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.visualtrace.ai.domain.model.MediaSelection

@Composable
fun ImagePreview(selection: MediaSelection, contentDescription: String, modifier: Modifier = Modifier) {
    val ratio = if ((selection.width ?: 0) > 0 && (selection.height ?: 0) > 0) {
        (selection.width!!.toFloat() / selection.height!!).coerceIn(0.6f, 2f)
    } else 4f / 3f
    Box(modifier.fillMaxWidth().heightIn(max = 360.dp).clip(MaterialTheme.shapes.large)
        .background(MaterialTheme.colorScheme.surfaceVariant)) {
        AsyncImage(
            model = selection.uri,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
        )
    }
}
