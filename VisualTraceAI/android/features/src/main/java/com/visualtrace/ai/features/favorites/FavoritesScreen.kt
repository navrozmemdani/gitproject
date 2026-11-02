package com.visualtrace.ai.features.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FolderDelete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.visualtrace.ai.core.ui.LoadingState
import com.visualtrace.ai.core.ui.MessageState
import com.visualtrace.ai.core.util.Format
import com.visualtrace.ai.core.util.imageModel
import com.visualtrace.ai.domain.model.ResultCollection
import com.visualtrace.ai.domain.model.SavedResult
import com.visualtrace.ai.features.R
import com.visualtrace.ai.features.common.Intents
import com.visualtrace.ai.features.common.toText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(viewModel: FavoritesViewModel, onOpenSearch: (String) -> Unit) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val selected by viewModel.selectedCollection.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SavedResult?>(null) }
    val context = LocalContext.current

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.saved_title)) }, actions = {
            IconButton(onClick = { creating = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.new_collection)) }
            if (selected != null) IconButton(onClick = { viewModel.deleteCollection(selected!!) }) {
                Icon(Icons.Outlined.FolderDelete, stringResource(R.string.delete_collection))
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = selected == null, onClick = { viewModel.select(null) }, label = { Text(stringResource(R.string.filter_all)) }) }
                items(collections, key = { it.id }) { c ->
                    FilterChip(selected = selected == c.id, onClick = { viewModel.select(c.id) }, label = { Text("${c.name} (${c.itemCount})") })
                }
            }
            val list = items
            when {
                list == null -> LoadingState(stringResource(R.string.searching))
                list.isEmpty() -> MessageState(stringResource(R.string.nothing_yet), stringResource(R.string.saved_empty), icon = Icons.Outlined.BookmarkBorder)
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { s ->
                        SavedRow(s, collections, onOpen = { onOpenSearch(s.result.searchId) },
                            onOpenSource = { Intents.openUrl(context, s.result.sourceUrl) }, onEdit = { editing = s },
                            onMove = { viewModel.move(s.id, it) }, onRemove = { viewModel.remove(s.id) })
                    }
                }
            }
        }
    }

    if (creating) {
        NameDialog(onDismiss = { creating = false }, onCreate = { viewModel.createCollection(it); creating = false })
    }
    editing?.let { s ->
        NoteDialog(initial = s.note.orEmpty(), onDismiss = { editing = null }, onSave = { viewModel.updateNote(s.id, it); editing = null })
    }
    error?.let { e ->
        val t = e.toText()
        AlertDialog(onDismissRequest = viewModel::clearError, title = { Text(t.title) }, text = { Text(t.message) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } })
    }
}

@Composable
private fun SavedRow(
    s: SavedResult,
    collections: List<ResultCollection>,
    onOpen: () -> Unit,
    onOpenSource: () -> Unit,
    onEdit: () -> Unit,
    onMove: (String?) -> Unit,
    onRemove: () -> Unit,
) {
    var moveMenu by remember { mutableStateOf(false) }
    Card(onClick = onOpen, shape = MaterialTheme.shapes.large, modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(12.dp)) {
            Box(Modifier.size(80.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant)) {
                AsyncImage(imageModel(s.result.thumbnailUrl ?: s.result.imageUrl), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.result.platform, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(s.result.pageTitle ?: Format.host(s.result.sourceUrl), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(Format.date(s.savedAtEpochMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.result.similarityScore?.let { Text(stringResource(R.string.similarity_label, Format.percent(it)), style = MaterialTheme.typography.bodySmall) }
                s.note?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("“$it”", style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            IconButton(onClick = onOpenSource) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.open_source)) }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.EditNote, stringResource(R.string.edit_note)) }
            Box {
                IconButton(onClick = { moveMenu = true }) { Icon(Icons.Outlined.DriveFileMove, stringResource(R.string.move_to)) }
                DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.no_collection)) }, onClick = { onMove(null); moveMenu = false })
                    collections.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { onMove(c.id); moveMenu = false }) }
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.remove)) }
        }
    }
}

@Composable
private fun NameDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.new_collection)) },
        text = { OutlinedTextField(name, { name = it.take(120) }, label = { Text(stringResource(R.string.collection_name)) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun NoteDialog(initial: String, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var note by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.edit_note)) },
        text = { OutlinedTextField(note, { note = it.take(2000) }, label = { Text(stringResource(R.string.note_optional)) }, minLines = 3) },
        confirmButton = { TextButton(onClick = { onSave(note.ifBlank { null }) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
