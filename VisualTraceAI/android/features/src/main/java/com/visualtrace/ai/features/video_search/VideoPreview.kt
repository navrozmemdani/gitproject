package com.visualtrace.ai.features.video_search

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.visualtrace.ai.domain.model.MediaSelection

/** Local playback preview (Media3). The video is not uploaded for previewing. */
@OptIn(UnstableApi::class)
@Composable
fun VideoPreview(selection: MediaSelection, contentDescription: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(selection.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(selection.uri)))
            playWhenReady = false
            volume = 0f
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    val ratio = if ((selection.width ?: 0) > 0 && (selection.height ?: 0) > 0) {
        (selection.width!!.toFloat() / selection.height!!).coerceIn(0.5f, 2f)
    } else 16f / 9f
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true; setShowNextButton(false); setShowPreviousButton(false) } },
        modifier = modifier.fillMaxWidth().aspectRatio(ratio).clip(MaterialTheme.shapes.large).background(Color.Black)
            .semantics { this.contentDescription = contentDescription },
    )
}
