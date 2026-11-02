package com.visualtrace.ai.features.results

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.visualtrace.ai.core.util.imageModel
import com.visualtrace.ai.domain.model.ReportReason
import com.visualtrace.ai.domain.model.ResultCollection
import com.visualtrace.ai.domain.model.SearchResultItem
import com.visualtrace.ai.features.R

@Composable
fun ImageViewerDialog(item: SearchResultItem, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offsetX += pan.x
        offsetY += pan.y
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = imageModel(item.imageUrl ?: item.thumbnailUrl),
                contentDescription = item.pageTitle ?: item.platform,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().transformable(transform)
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Outlined.Close, stringResource(R.string.cancel), tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SaveResultSheet(
    collections: List<ResultCollection>,
    onCreateCollection: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (collectionId: String?, note: String?) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var newName by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.add_to_collection), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selected == null, onClick = { selected = null }, label = { Text(stringResource(R.string.no_collection)) })
                collections.forEach { c -> FilterChip(selected = selected == c.id, onClick = { selected = c.id }, label = { Text(c.name) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newName, { newName = it.take(120) }, label = { Text(stringResource(R.string.new_collection)) },
                    singleLine = true, modifier = Modifier.weight(1f))
                TextButton(onClick = { onCreateCollection(newName); newName = "" }, enabled = newName.isNotBlank()) { Text(stringResource(R.string.create)) }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(note, { note = it.take(2000) }, label = { Text(stringResource(R.string.note_optional)) },
                modifier = Modifier.fillMaxWidth(), minLines = 2)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onSave(selected, note) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.save))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun ReportDialog(onDismiss: () -> Unit, onSend: (ReportReason, String?) -> Unit) {
    var reason by rememberSaveable { mutableStateOf(ReportReason.NOT_SIMILAR) }
    var comment by rememberSaveable { mutableStateOf("") }
    val labels = mapOf(
        ReportReason.NOT_SIMILAR to stringResource(R.string.report_not_similar),
        ReportReason.WRONG_SOURCE to stringResource(R.string.report_wrong_source),
        ReportReason.WRONG_PROFILE to stringResource(R.string.report_wrong_profile),
        ReportReason.BROKEN_LINK to stringResource(R.string.report_broken_link),
        ReportReason.INAPPROPRIATE to stringResource(R.string.report_inappropriate),
        ReportReason.OTHER to stringResource(R.string.report_other),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ReportReason.entries.forEach { r ->
                    Row(Modifier.fillMaxWidth().selectable(selected = reason == r, onClick = { reason = r }, role = Role.RadioButton)
                        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reason == r, onClick = null)
                        Text(labels.getValue(r), Modifier.padding(start = 8.dp))
                    }
                }
                OutlinedTextField(comment, { comment = it.take(1000) }, label = { Text(stringResource(R.string.comment_optional)) },
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSend(reason, comment.ifBlank { null }) }) { Text(stringResource(R.string.send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
