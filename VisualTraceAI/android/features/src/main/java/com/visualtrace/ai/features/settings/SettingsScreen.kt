package com.visualtrace.ai.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.visualtrace.ai.core.ui.StatusPill
import com.visualtrace.ai.core.designsystem.LocalVtColors
import com.visualtrace.ai.domain.model.ProviderMode
import com.visualtrace.ai.domain.model.SearchQuality
import com.visualtrace.ai.domain.model.ThemeMode
import com.visualtrace.ai.features.R
import com.visualtrace.ai.features.common.AppInfo
import com.visualtrace.ai.features.common.Intents
import com.visualtrace.ai.features.common.toText
import kotlin.math.roundToInt

val LANGUAGES = listOf("" to null, "en" to "English", "es" to "Español", "fr" to "Français", "de" to "Deutsch", "hi" to "हिन्दी")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    appInfo: AppInfo,
    onOpenProviders: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenLegal: (String) -> Unit,
) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var interval by remember(s.frameIntervalSeconds) { mutableFloatStateOf(s.frameIntervalSeconds) }
    var duration by remember(s.maxVideoDurationSeconds) { mutableFloatStateOf(s.maxVideoDurationSeconds.toFloat()) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(16.dp)) {
                Group(stringResource(R.string.appearance)) {
                    Text(stringResource(R.string.theme), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark).forEach { (m, l) ->
                            FilterChip(selected = s.theme == m, onClick = { viewModel.setTheme(m) }, label = { Text(stringResource(l)) })
                        }
                    }
                    ToggleRow(stringResource(R.string.dynamic_color), null, s.dynamicColor, viewModel::setDynamic)
                    Text(stringResource(R.string.language), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LANGUAGES.forEach { (tag, label) ->
                            FilterChip(selected = s.languageTag == tag, onClick = { viewModel.setLanguage(tag) },
                                label = { Text(label ?: stringResource(R.string.language_system)) })
                        }
                    }
                }
                Group(stringResource(R.string.search_settings)) {
                    NavRow(Icons.Outlined.ManageSearch, stringResource(R.string.provider_preferences), onOpenProviders)
                    Text(stringResource(R.string.search_quality), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(SearchQuality.FAST to R.string.quality_fast, SearchQuality.BALANCED to R.string.quality_balanced,
                            SearchQuality.THOROUGH to R.string.quality_thorough).forEach { (q, l) ->
                            FilterChip(selected = s.searchQuality == q, onClick = { viewModel.setQuality(q) }, label = { Text(stringResource(l)) })
                        }
                    }
                    Text(stringResource(R.string.frame_interval, "%.1f".format(interval)), style = MaterialTheme.typography.labelLarge)
                    Slider(value = interval, onValueChange = { interval = (it * 2).roundToInt() / 2f }, valueRange = 0.5f..30f,
                        onValueChangeFinished = { viewModel.setFrameInterval(interval) })
                    Text(stringResource(R.string.max_video_duration, duration.roundToInt()), style = MaterialTheme.typography.labelLarge)
                    Slider(value = duration, onValueChange = { duration = ((it / 5).roundToInt() * 5).toFloat() }, valueRange = 5f..600f,
                        onValueChangeFinished = { viewModel.setMaxDuration(duration.roundToInt()) })
                }
                Group(stringResource(R.string.history_management)) {
                    ToggleRow(stringResource(R.string.save_history), null, s.historyEnabled, viewModel::setHistoryEnabled)
                    NavRow(Icons.Outlined.Delete, stringResource(R.string.clear_all)) { confirmClear = true }
                }
                Group(stringResource(R.string.privacy)) {
                    ToggleRow(stringResource(R.string.third_party_consent), stringResource(R.string.third_party_consent_body), s.thirdPartyConsent, viewModel::setConsent)
                    ToggleRow(stringResource(R.string.full_video_consent), null, s.fullVideoUploadConsent, viewModel::setFullVideoConsent)
                    NavRow(Icons.Outlined.PrivacyTip, stringResource(R.string.privacy_policy)) { onOpenLegal("privacy") }
                    NavRow(Icons.Outlined.Description, stringResource(R.string.terms)) { onOpenLegal("terms") }
                }
                Group(stringResource(R.string.account)) {
                    NavRow(Icons.Outlined.Person, stringResource(R.string.profile_title), onOpenProfile)
                }
                Group(stringResource(R.string.about)) {
                    NavRow(Icons.Outlined.Info, stringResource(R.string.about)) { onOpenLegal("about") }
                    NavRow(Icons.Outlined.Email, stringResource(R.string.contact_support)) {
                        Intents.email(context, appInfo.supportEmail, "VisualTrace AI ${appInfo.versionName} support")
                    }
                    Text(stringResource(R.string.version, appInfo.versionName), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text(stringResource(R.string.clear_history_title)) },
            text = { Text(stringResource(R.string.clear_history_body)) },
            confirmButton = { TextButton(onClick = { viewModel.clearHistory(); confirmClear = false }) { Text(stringResource(R.string.clear_all)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp).semantics { heading() })
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) { { Text(subtitle) } } else null,
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

@Composable
private fun NavRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

/** Provider enable/disable with an honest description of each integration. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProviderSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val error by viewModel.catalogError.collectAsStateWithLifecycle()
    val ext = LocalVtColors.current
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.provider_preferences)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val c = catalog
                if (c == null) {
                    val e = error
                    if (e != null) {
                        val t = e.toText()
                        com.visualtrace.ai.core.ui.MessageState(t.title, t.message, icon = t.icon, actionLabel = stringResource(R.string.retry),
                            onAction = { viewModel.loadProviders(true) })
                    } else {
                        com.visualtrace.ai.core.ui.LoadingState(stringResource(R.string.searching))
                    }
                    return@Column
                }
                if (c.mockMode) Text(stringResource(R.string.mock_mode_warning), color = ext.warning, style = MaterialTheme.typography.bodySmall)
                val enabled = s.enabledProviders ?: c.providers.filter { it.enabledByDefault }.map { it.key }.toSet()
                c.providers.forEach { p ->
                    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                                    Text(p.platform, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = p.key in enabled, onCheckedChange = { viewModel.setProviderEnabled(p.key, it) },
                                    enabled = !p.globallyDisabled)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                when (p.mode) {
                                    ProviderMode.LIVE -> StatusPill(stringResource(R.string.provider_mode_live), ext.success)
                                    ProviderMode.MOCK -> StatusPill(stringResource(R.string.provider_mode_mock), ext.warning)
                                    ProviderMode.UNAVAILABLE -> StatusPill(stringResource(R.string.provider_mode_unavailable), MaterialTheme.colorScheme.outline)
                                }
                                StatusPill(stringResource(if (p.supportsReverseImage) R.string.reverse_image else R.string.keyword_based), MaterialTheme.colorScheme.primary)
                                if (p.uploadsMedia) StatusPill(stringResource(R.string.uploads_media), ext.warning)
                                if (p.siteSearchFallback) StatusPill(stringResource(R.string.site_search), MaterialTheme.colorScheme.secondary)
                            }
                            if (p.limitations.isNotBlank()) {
                                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                Text("${stringResource(R.string.limitations)}: ${p.limitations}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (p.alternatives.isNotBlank()) {
                                Text("${stringResource(R.string.alternatives)}: ${p.alternatives}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
