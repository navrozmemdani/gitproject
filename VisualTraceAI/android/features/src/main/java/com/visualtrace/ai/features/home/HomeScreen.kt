package com.visualtrace.ai.features.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.visualtrace.ai.core.designsystem.LocalVtColors
import com.visualtrace.ai.core.ui.GradientButton
import com.visualtrace.ai.core.ui.SectionHeader
import com.visualtrace.ai.core.ui.StatusPill
import com.visualtrace.ai.core.ui.VisualTraceLogo
import com.visualtrace.ai.core.util.Format
import com.visualtrace.ai.core.util.imageModel
import com.visualtrace.ai.domain.model.HistoryEntry
import com.visualtrace.ai.domain.model.MediaKind
import com.visualtrace.ai.domain.model.MediaRules
import com.visualtrace.ai.domain.model.ProviderInfo
import com.visualtrace.ai.domain.model.ProviderMode
import com.visualtrace.ai.domain.model.SavedResult
import com.visualtrace.ai.domain.model.SearchType
import com.visualtrace.ai.features.R
import com.visualtrace.ai.features.common.toText
import com.visualtrace.ai.features.image_search.ImagePreview
import com.visualtrace.ai.features.video_search.VideoPreview
import kotlinx.coroutines.launch
import java.io.File

private val VIDEO_MIMES = arrayOf("video/mp4", "video/quicktime", "video/webm")
private val ALL_MIMES = arrayOf("image/jpeg", "image/png", "image/webp", *VIDEO_MIMES)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenResults: (ticket: String) -> Unit,
    onOpenSearch: (searchId: String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenProviders: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val enabledKeys by viewModel.enabledProviderKeys.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showUrlDialog by rememberSaveable { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { if (it is HomeEvent.OpenResults) onOpenResults(it.ticket) }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onMediaPicked(uri?.toString())
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onMediaPicked(uri?.toString())
    }
    val browse = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onMediaPicked(uri?.toString())
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        viewModel.onCameraResult(pendingCameraUri, ok)
    }

    val errorText = state.error?.toText()
    LaunchedEffect(state.error) {
        if (errorText != null) {
            snackbar.showSnackbar("${errorText.title}: ${errorText.message}")
            viewModel.dismissError()
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Hero() }
            item {
                Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionTile(Icons.Outlined.Image, stringResource(R.string.upload_image), Modifier.weight(1f)) {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        ActionTile(Icons.Outlined.Videocam, stringResource(R.string.upload_video), Modifier.weight(1f)) {
                            pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionTile(Icons.Outlined.PhotoCamera, stringResource(R.string.open_camera), Modifier.weight(1f)) {
                            scope.launch {
                                val uri = viewModel.cameraUri()
                                pendingCameraUri = uri
                                camera.launch(Uri.parse(uri))
                            }
                        }
                        ActionTile(Icons.Outlined.Link, stringResource(R.string.paste_url), Modifier.weight(1f)) { showUrlDialog = true }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.supported_formats), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = { browse.launch(ALL_MIMES) }) {
                            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.browse_files))
                        }
                    }
                }
            }
            item {
                AnimatedVisibility(state.selection != null || state.inspecting, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    SelectionCard(state, settings.frameIntervalSeconds, settings.maxVideoDurationSeconds, settings.maxFramesPerVideo,
                        onRemove = viewModel::clearSelection, onFullVideoToggle = viewModel::onFullVideoToggle)
                }
            }
            item {
                Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.queryText,
                        onValueChange = viewModel::onQueryChange,
                        label = { Text(stringResource(R.string.describe_hint)) },
                        placeholder = { Text(stringResource(R.string.describe_example)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.onSearch() }),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    GradientButton(
                        text = stringResource(R.string.search),
                        onClick = viewModel::onSearch,
                        enabled = state.canSearch,
                        loading = state.submitting || state.inspecting,
                        icon = Icons.Outlined.Search,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                ProvidersSection(state.catalog?.providers.orEmpty(), enabledKeys, state.catalog?.mockMode == true,
                    state.catalogError != null, onOpenProviders, viewModel::refreshProviders)
            }
            item {
                Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp)) {
                    SectionHeader(stringResource(R.string.recent_searches)) {
                        if (recent.isNotEmpty()) TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.see_all)) }
                    }
                }
            }
            item {
                if (recent.isEmpty()) EmptyRow()
                else LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recent, key = { it.searchId }) { RecentCard(it) { onOpenSearch(it.searchId) } }
                }
            }
            item {
                Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp)) {
                    SectionHeader(stringResource(R.string.saved_results)) {
                        if (saved.isNotEmpty()) TextButton(onClick = onOpenSaved) { Text(stringResource(R.string.see_all)) }
                    }
                }
            }
            item {
                if (saved.isEmpty()) EmptyRow()
                else LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(saved, key = { it.id }) { SavedCard(it) { onOpenSearch(it.result.searchId) } }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    if (showUrlDialog) {
        UrlDialog(onDismiss = { showUrlDialog = false }, onSubmit = { if (viewModel.onImageUrl(it)) showUrlDialog = false })
    }
    if (state.showConsentDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConsent,
            title = { Text(stringResource(R.string.consent_title)) },
            text = { Text(stringResource(R.string.consent_body)) },
            confirmButton = { TextButton(onClick = { viewModel.onConsentAnswer(true) }) { Text(stringResource(R.string.consent_allow)) } },
            dismissButton = { TextButton(onClick = { viewModel.onConsentAnswer(false) }) { Text(stringResource(R.string.consent_deny)) } },
        )
    }
    if (state.showFullVideoDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onFullVideoConsent(false) },
            title = { Text(stringResource(R.string.full_video_consent_title)) },
            text = { Text(stringResource(R.string.full_video_consent_body)) },
            confirmButton = { TextButton(onClick = { viewModel.onFullVideoConsent(true) }) { Text(stringResource(R.string.consent_allow)) } },
            dismissButton = { TextButton(onClick = { viewModel.onFullVideoConsent(false) }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Hero() {
    val gradient = LocalVtColors.current.heroGradient
    Box(Modifier.fillMaxWidth().background(gradient)) {
        Column(Modifier.align(Alignment.Center).widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VisualTraceLogo(36.dp)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.home_heading), style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.home_subheading), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.heightIn(min = 96.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(LocalVtColors.current.accentGradient), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = androidx.compose.ui.graphics.Color.White)
            }
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 2)
        }
    }
}

@Composable
private fun SelectionCard(
    state: HomeUiState,
    frameInterval: Float,
    maxDuration: Int,
    maxFrames: Int,
    onRemove: () -> Unit,
    onFullVideoToggle: (Boolean) -> Unit,
) {
    val selection = state.selection
    Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.selected_media), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    FilledTonalIconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, stringResource(R.string.remove_selection)) }
                }
                if (selection == null) {
                    Text(stringResource(R.string.searching), style = MaterialTheme.typography.bodySmall)
                } else {
                    if (selection.kind == MediaKind.VIDEO) VideoPreview(selection, selection.displayName ?: stringResource(R.string.upload_video))
                    else ImagePreview(selection, selection.displayName ?: stringResource(R.string.upload_image))
                    Spacer(Modifier.height(8.dp))
                    val details = buildList {
                        add(if (selection.isRemoteUrl) stringResource(R.string.image_from_link) else selection.displayName.orEmpty())
                        if (selection.width != null && selection.height != null) add("${selection.width}×${selection.height}")
                        selection.sizeBytes?.let { add(Format.fileSize(it)) }
                        selection.durationMs?.let { add(stringResource(R.string.video_duration, Format.duration(it))) }
                    }.filter { it.isNotBlank() }
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (selection.kind == MediaKind.VIDEO) {
                        val frames = MediaRules.frameTimestamps(selection.durationMs ?: 0, frameInterval, maxDuration, maxFrames).size
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.video_frames_plan, frames, "%.1f".format(frameInterval)),
                            style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.upload_full_video), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(checked = state.uploadFullVideo, onCheckedChange = onFullVideoToggle)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProvidersSection(
    providers: List<ProviderInfo>,
    enabled: Set<String>,
    mockMode: Boolean,
    failed: Boolean,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
) {
    val ext = LocalVtColors.current
    Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        SectionHeader(stringResource(R.string.search_providers)) {
            if (providers.isNotEmpty()) TextButton(onClick = onOpen) {
                Text(stringResource(R.string.providers_active, providers.count { it.key in enabled && it.mode != ProviderMode.UNAVAILABLE }, providers.size))
            }
        }
        if (mockMode) {
            Surface(color = ext.warning.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Warning, null, tint = ext.warning)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.mock_mode_warning), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (failed && providers.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.providers_unreachable), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
    }
    if (providers.isNotEmpty()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(providers, key = { it.key }) { p ->
                val (label, color) = when (p.mode) {
                    ProviderMode.LIVE -> stringResource(R.string.provider_mode_live) to ext.success
                    ProviderMode.MOCK -> stringResource(R.string.provider_mode_mock) to ext.warning
                    ProviderMode.UNAVAILABLE -> stringResource(R.string.provider_mode_unavailable) to MaterialTheme.colorScheme.outline
                }
                Card(onClick = onOpen, shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = if (p.key in enabled) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(p.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        StatusPill(label, color)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRow() {
    Text(stringResource(R.string.nothing_yet), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp))
}

@Composable
private fun RecentCard(entry: HistoryEntry, onClick: () -> Unit) {
    Card(onClick = onClick, shape = MaterialTheme.shapes.medium, modifier = Modifier.width(150.dp)) {
        Box(Modifier.fillMaxWidth().height(100.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (entry.thumbnailPath != null) {
                AsyncImage(File(entry.thumbnailPath!!), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(if (entry.type == SearchType.TEXT) Icons.Outlined.Search else Icons.Outlined.Image, null,
                    Modifier.align(Alignment.Center))
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(entry.title ?: stringResource(if (entry.type == SearchType.VIDEO) R.string.video_search else R.string.image_search),
                style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.results_count, entry.resultCount), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SavedCard(item: SavedResult, onClick: () -> Unit) {
    Card(onClick = onClick, shape = MaterialTheme.shapes.medium, modifier = Modifier.width(150.dp)) {
        AsyncImage(imageModel(item.result.thumbnailUrl ?: item.result.imageUrl), item.result.pageTitle,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(100.dp).background(MaterialTheme.colorScheme.surfaceVariant))
        Column(Modifier.padding(10.dp)) {
            Text(item.result.pageTitle ?: Format.host(item.result.sourceUrl), style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.result.platform, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun UrlDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paste_url)) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(2048) }, singleLine = true,
                placeholder = { Text(stringResource(R.string.image_url_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit(text) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSubmit(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.use_url)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
