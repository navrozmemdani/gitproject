package com.visualtrace.ai.features.results

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.visualtrace.ai.core.designsystem.LocalVtColors
import com.visualtrace.ai.core.ui.StatusPill
import com.visualtrace.ai.domain.model.AnalysisSection
import com.visualtrace.ai.domain.model.ImageAnalysis
import com.visualtrace.ai.features.R
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalysisCard(analysis: ImageAnalysis, onSearchCharacteristics: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).animateContentSize(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Icon(Icons.Outlined.AutoAwesome, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ai_analysis), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(stringResource(R.string.analysis_objects), analysis.objects)
                Field(stringResource(R.string.analysis_clothing), analysis.clothing)
                Column {
                    Header(stringResource(R.string.analysis_colors), analysis.colors.source)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        analysis.colors.items.take(6).forEach { c ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Box(Modifier.size(14.dp).background(parseHex(c.hex), CircleShape))
                                Spacer(Modifier.width(4.dp))
                                Text("${c.label} ${c.confidence?.let { "${(it * 100).roundToInt()}%" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Simple(stringResource(R.string.analysis_background), listOfNotNull(analysis.backgroundType, analysis.backgroundColor).joinToString(", ").ifBlank { null }, "measured")
                Field(stringResource(R.string.analysis_environment), analysis.environment)
                Simple(stringResource(R.string.analysis_text), analysis.ocrText, analysis.ocrSource, analysis.ocrReason)
                Simple(stringResource(R.string.analysis_orientation), listOfNotNull(analysis.orientation,
                    if (analysis.width != null && analysis.height != null) "${analysis.width}×${analysis.height}" else null).joinToString(" · ").ifBlank { null }, "measured")
                Simple(stringResource(R.string.analysis_quality), listOfNotNull(analysis.qualityLabel,
                    analysis.qualityIssues.takeIf { it.isNotEmpty() }?.joinToString()).joinToString(" — ").ifBlank { null }, "measured")
                Simple(stringResource(R.string.analysis_characteristics), analysis.characteristics.joinToString(", ").ifBlank { null }, "measured")
                analysis.embeddingModel?.let {
                    Text(stringResource(R.string.analysis_model, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val query = (analysis.keywords.ifEmpty { analysis.characteristics }).take(4).joinToString(" ")
                if (query.isNotBlank()) {
                    OutlinedButton(onClick = { onSearchCharacteristics(query) }, shape = MaterialTheme.shapes.medium) {
                        Icon(Icons.Outlined.Search, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.search_by_characteristics))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, source: String) {
    val ext = LocalVtColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        when {
            source == "unavailable" -> StatusPill(stringResource(R.string.not_available), MaterialTheme.colorScheme.outline)
            source.startsWith("inferred") -> StatusPill(stringResource(R.string.inferred), ext.warning)
            else -> StatusPill(stringResource(R.string.measured), MaterialTheme.colorScheme.primary)
        }
    }
    Spacer(Modifier.height(4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Field(title: String, section: AnalysisSection) {
    Column {
        Header(title, section.source)
        if (section.items.isEmpty()) {
            Text(section.reason ?: stringResource(R.string.not_available), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                section.items.forEach { v ->
                    SuggestionChip(onClick = {}, label = {
                        Text(v.label + (v.confidence?.let { " · ${(it * 100).roundToInt()}%" } ?: ""))
                    })
                }
            }
        }
    }
}

@Composable
private fun Simple(title: String, value: String?, source: String, reason: String? = null) {
    Column {
        Header(title, if (value == null) "unavailable" else source)
        Text(value ?: reason ?: stringResource(R.string.not_available), style = MaterialTheme.typography.bodyMedium,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
    }
}

private fun parseHex(hex: String?): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)
