package com.visualtrace.ai.features.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.visualtrace.ai.core.designsystem.LocalVtColors
import com.visualtrace.ai.core.ui.LoadingState
import com.visualtrace.ai.core.ui.MessageState
import com.visualtrace.ai.core.ui.StatusPill
import com.visualtrace.ai.core.util.Format
import com.visualtrace.ai.domain.model.HistoryEntry
import com.visualtrace.ai.domain.model.SearchStatus
import com.visualtrace.ai.domain.model.SearchType
import com.visualtrace.ai.features.R
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onOpen: (String) -> Unit) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val enabled by viewModel.historyEnabled.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val tabs = listOf(null to R.string.history_all, SearchType.IMAGE to R.string.history_images,
        SearchType.VIDEO to R.string.history_videos, SearchType.TEXT to R.string.history_text)

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.history_title)) }, actions = {
            if (!entries.isNullOrEmpty()) IconButton(onClick = { confirmClear = true }) {
                Icon(Icons.Outlined.DeleteSweep, stringResource(R.string.clear_all))
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tabs.indexOfFirst { it.first == filter }.coerceAtLeast(0)) {
                tabs.forEach { (type, label) ->
                    Tab(selected = filter == type, onClick = { viewModel.setFilter(type) }, text = { Text(stringResource(label)) })
                }
            }
            val list = entries
            when {
                list == null -> LoadingState(stringResource(R.string.searching))
                list.isEmpty() -> MessageState(stringResource(R.string.nothing_yet),
                    stringResource(if (enabled) R.string.history_empty else R.string.history_disabled), icon = Icons.Outlined.History)
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()) {
                    items(list, key = { it.searchId }) { e ->
                        HistoryRow(e, onOpen = { onOpen(e.searchId) }, onDelete = { viewModel.delete(e.searchId) })
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_history_title)) },
            text = { Text(stringResource(R.string.clear_history_body)) },
            confirmButton = { TextButton(onClick = { viewModel.clearAll(); confirmClear = false }) { Text(stringResource(R.string.clear_all)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onOpen: () -> Unit, onDelete: () -> Unit) {
    val ext = LocalVtColors.current
    Card(onClick = onOpen, shape = MaterialTheme.shapes.large, modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                val path = entry.thumbnailPath
                if (path != null) AsyncImage(File(path), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(when (entry.type) { SearchType.VIDEO -> Icons.Outlined.Videocam; SearchType.TEXT -> Icons.Outlined.Search; else -> Icons.Outlined.Image }, null)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title ?: stringResource(when (entry.type) {
                    SearchType.VIDEO -> R.string.video_search; SearchType.TEXT -> R.string.text_search; else -> R.string.image_search }),
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Format.dateTime(entry.createdAtEpochMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.results_count, entry.resultCount), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    when (entry.status) {
                        SearchStatus.PARTIAL -> StatusPill(stringResource(R.string.status_partial), ext.warning)
                        SearchStatus.FAILED -> StatusPill(stringResource(R.string.status_failed), ext.danger)
                        else -> Unit
                    }
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete)) }
        }
    }
}
