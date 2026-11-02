package com.visualtrace.ai.features.results

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.visualtrace.ai.core.designsystem.LocalVtColors
import com.visualtrace.ai.core.ui.LoadingState
import com.visualtrace.ai.core.ui.MessageState
import com.visualtrace.ai.core.ui.StatusPill
import com.visualtrace.ai.core.util.Format
import com.visualtrace.ai.core.util.base64Image
import com.visualtrace.ai.domain.model.ProviderReport
import com.visualtrace.ai.domain.model.ProviderRunStatus
import com.visualtrace.ai.domain.model.ResultFilter
import com.visualtrace.ai.domain.model.ResultSort
import com.visualtrace.ai.domain.model.SearchResultItem
import com.visualtrace.ai.domain.model.SearchSession
import com.visualtrace.ai.domain.model.SearchStatus
import com.visualtrace.ai.domain.repository.SubmissionState
import com.visualtrace.ai.features.R
import com.visualtrace.ai.features.common.toText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(viewModel: ResultsViewModel, onBack: () -> Unit, onOpenTicket: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var sortMenu by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<SearchResultItem?>(null) }
    var saving by remember { mutableStateOf<SearchResultItem?>(null) }
    var reporting by remember { mutableStateOf<SearchResultItem?>(null) }

    val messages = mapOf(
        MessageKey.SAVED to stringResource(R.string.saved),
        MessageKey.REMOVED to stringResource(R.string.remove),
        MessageKey.REPORTED to stringResource(R.string.report_sent),
        MessageKey.COPIED to stringResource(R.string.url_copied),
        MessageKey.ERROR to stringResource(R.string.err_server),
    )
    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { e ->
            when (e) {
                is ResultsEvent.Message -> snackbar.showSnackbar(messages.getValue(e.text))
                is ResultsEvent.OpenTicket -> onOpenTicket(e.ticket)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.results_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.sort)) }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            ResultSort.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(sortLabel(s)) },
                                    leadingIcon = { RadioButton(selected = state.sort == s, onClick = null) },
                                    onClick = { viewModel.setSort(s); sortMenu = false },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when (val phase = state.phase) {
                is ResultsPhase.Submitting -> LoadingState(
                    when (phase.state) {
                        SubmissionState.Waiting -> stringResource(R.string.waiting_network)
                        else -> stringResource(R.string.preparing_upload)
                    },
                )
                is ResultsPhase.SubmitFailed -> {
                    val t = phase.error.toText()
                    MessageState(t.title, t.message, icon = t.icon, actionLabel = stringResource(R.string.back), onAction = onBack)
                }
                ResultsPhase.Tracking -> ResultsContent(
                    state = state,
                    savedIds = savedIds.keys,
                    onRetry = viewModel::retry,
                    onFilter = viewModel::setFilter,
                    onCharacteristics = viewModel::searchByCharacteristics,
                    onView = { viewing = it },
                    onSave = { item -> if (item.id in savedIds) { viewModel.toggleSave(item, null, null) } else { saving = item } },
                    onReport = { reporting = it },
                    onCopied = viewModel::copied,
                )
            }
        }
    }

    viewing?.let { ImageViewerDialog(it, onDismiss = { viewing = null }) }
    saving?.let { item ->
        SaveResultSheet(collections = collections, onCreateCollection = viewModel::createCollection,
            onDismiss = { saving = null }, onSave = { col, note -> viewModel.toggleSave(item, col, note); saving = null })
    }
    reporting?.let { item ->
        ReportDialog(onDismiss = { reporting = null }, onSend = { reason, comment -> viewModel.report(item, reason, comment); reporting = null })
    }
}

@Composable
private fun ResultsContent(
    state: ResultsUiState,
    savedIds: Set<String>,
    onRetry: () -> Unit,
    onFilter: (ResultFilter) -> Unit,
    onCharacteristics: (String) -> Unit,
    onView: (SearchResultItem) -> Unit,
    onSave: (SearchResultItem) -> Unit,
    onReport: (SearchResultItem) -> Unit,
    onCopied: () -> Unit,
) {
    val session = state.session
    if (session == null && !state.resultsLoaded) {
        val err = state.trackingError
        if (err != null) {
            val t = err.toText()
            MessageState(t.title, t.message, icon = t.icon, actionLabel = stringResource(R.string.retry), onAction = onRetry)
        } else {
            LoadingState(stringResource(R.string.searching))
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (session != null) {
            item { SearchHeader(session) }
            if (!session.status.isTerminal) item { ProgressBlock(session) }
            if (session.usedMockData) item { MockBanner() }
            item { Disclaimer() }
            session.analysis?.let { analysis -> item { AnalysisCard(analysis, onCharacteristics) } }
        }
        if (session == null || session.status.isTerminal) {
            item { FilterRow(state.filter, state.counts, onFilter) }
            when {
                state.loadingResults && state.items.isEmpty() -> item { LoadingState(stringResource(R.string.searching)) }
                state.resultsError != null -> item {
                    val t = state.resultsError.toText()
                    MessageState(t.title, t.message, icon = t.icon, actionLabel = stringResource(R.string.retry), onAction = onRetry)
                }
                session != null && session.status == SearchStatus.FAILED && state.items.isEmpty() -> item {
                    MessageState(stringResource(R.string.status_failed), session.errorMessage ?: stringResource(R.string.err_server),
                        icon = Icons.Outlined.ErrorOutline, actionLabel = stringResource(R.string.retry), onAction = onRetry)
                }
                state.items.isEmpty() && state.resultsLoaded -> item {
                    val noneSearched = session != null && session.providersSearched.isEmpty()
                    MessageState(stringResource(R.string.no_results_title),
                        stringResource(if (noneSearched) R.string.no_providers_searched else R.string.no_results_body),
                        icon = Icons.Outlined.SearchOff)
                }
                else -> items(state.items, key = { it.id }) { item ->
                    ResultCard(item = item, saved = item.id in savedIds, onView = { onView(item) }, onSave = { onSave(item) },
                        onReport = { onReport(item) }, onCopied = onCopied,
                        modifier = Modifier.widthIn(max = 720.dp).padding(horizontal = 16.dp, vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchHeader(session: SearchSession) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val ext = LocalVtColors.current
    Card(
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(16.dp).animateContentSize(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val thumb = remember(session.previewThumbnailBase64) { base64Image(session.previewThumbnailBase64) }
            Box(Modifier.size(84.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (thumb != null) AsyncImage(thumb, stringResource(R.string.selected_media), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                val (label, color) = when (session.status) {
                    SearchStatus.COMPLETED -> stringResource(R.string.status_completed) to ext.success
                    SearchStatus.PARTIAL -> stringResource(R.string.status_partial) to ext.warning
                    SearchStatus.FAILED -> stringResource(R.string.status_failed) to ext.danger
                    else -> stringResource(R.string.status_running) to MaterialTheme.colorScheme.primary
                }
                StatusPill(label, color)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.results_count, session.resultCount), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.searched_at, Format.dateTime(session.createdAtEpochMs)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                session.queryText?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall) }
                session.durationMs?.let {
                    Text(stringResource(R.string.video_duration, Format.duration(it)) + (session.framesProcessed?.let { f -> " · $f frames" } ?: ""),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Text(stringResource(R.string.providers_used, session.providersSearched.size), modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, stringResource(R.string.provider_details))
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                session.providerReports.forEach { ProviderRow(it) }
            }
        }
    }
}

@Composable
private fun ProviderRow(report: ProviderReport) {
    val ext = LocalVtColors.current
    val (label, color) = providerStatus(report.status)
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (report.searched) Icons.Outlined.CheckCircle else Icons.Outlined.Info, null, Modifier.size(18.dp),
                tint = if (report.searched) ext.success else MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(8.dp))
            Text(report.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            StatusPill(if (report.searched) "$label · ${report.resultCount}" else label, color)
        }
        report.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 26.dp, top = 2.dp))
        }
    }
}

@Composable
fun providerStatus(status: ProviderRunStatus): Pair<String, Color> {
    val ext = LocalVtColors.current
    return when (status) {
        ProviderRunStatus.OK -> stringResource(R.string.pstatus_ok) to ext.success
        ProviderRunStatus.NO_RESULTS -> stringResource(R.string.pstatus_no_results) to MaterialTheme.colorScheme.primary
        ProviderRunStatus.NOT_CONFIGURED -> stringResource(R.string.pstatus_not_configured) to MaterialTheme.colorScheme.outline
        ProviderRunStatus.UNSUPPORTED -> stringResource(R.string.pstatus_unsupported) to MaterialTheme.colorScheme.outline
        ProviderRunStatus.SKIPPED_NO_CONSENT -> stringResource(R.string.pstatus_skipped_no_consent) to MaterialTheme.colorScheme.outline
        ProviderRunStatus.SKIPPED_NO_QUERY -> stringResource(R.string.pstatus_skipped_no_query) to MaterialTheme.colorScheme.outline
        ProviderRunStatus.DISABLED -> stringResource(R.string.pstatus_disabled) to MaterialTheme.colorScheme.outline
        ProviderRunStatus.RATE_LIMITED -> stringResource(R.string.pstatus_rate_limited) to ext.warning
        ProviderRunStatus.TIMEOUT -> stringResource(R.string.pstatus_timeout) to ext.warning
        ProviderRunStatus.ERROR -> stringResource(R.string.pstatus_error) to ext.danger
        ProviderRunStatus.MOCK -> stringResource(R.string.pstatus_mock) to ext.warning
        ProviderRunStatus.UNKNOWN -> stringResource(R.string.pstatus_unknown) to MaterialTheme.colorScheme.outline
    }
}

@Composable
private fun ProgressBlock(session: SearchSession) {
    val raw = session.stage
    val stage = when {
        raw == null -> stringResource(R.string.searching)
        raw.startsWith("analy") -> stringResource(R.string.stage_analyzing)
        raw.startsWith("search") -> stringResource(R.string.stage_searching) + raw.removePrefix("searching")
        raw.startsWith("saving") -> stringResource(R.string.stage_saving)
        else -> stringResource(R.string.searching)
    }
    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(stage, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        if (session.progress > 0f) LinearProgressIndicator(progress = { session.progress }, modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MockBanner() {
    val ext = LocalVtColors.current
    Surface(color = ext.warning.copy(alpha = 0.15f), shape = MaterialTheme.shapes.medium,
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Warning, null, tint = ext.warning)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.mock_mode_warning), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Disclaimer() {
    Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FilterRow(selected: ResultFilter, counts: Map<ResultFilter, Int>, onSelect: (ResultFilter) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = 720.dp)) {
        items(ResultFilter.entries) { f ->
            val count = counts[f]
            FilterChip(selected = f == selected, onClick = { onSelect(f) },
                label = { Text(filterLabel(f) + (count?.let { " ($it)" } ?: "")) })
        }
    }
}

@Composable
fun filterLabel(f: ResultFilter): String = stringResource(
    when (f) {
        ResultFilter.ALL -> R.string.filter_all
        ResultFilter.EXACT -> R.string.filter_exact
        ResultFilter.SIMILAR -> R.string.filter_similar
        ResultFilter.SOCIAL -> R.string.filter_social
        ResultFilter.WEBSITES -> R.string.filter_websites
        ResultFilter.NEWS -> R.string.filter_news
    },
)

@Composable
fun sortLabel(s: ResultSort): String = stringResource(
    when (s) {
        ResultSort.SIMILARITY -> R.string.sort_similarity
        ResultSort.RECENT -> R.string.sort_recent
        ResultSort.RELEVANCE -> R.string.sort_relevance
    },
)
