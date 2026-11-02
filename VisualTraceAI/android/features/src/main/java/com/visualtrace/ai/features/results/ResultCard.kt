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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.visualtrace.ai.core.designsystem.LocalVtColors
import com.visualtrace.ai.core.ui.SimilarityMeter
import com.visualtrace.ai.core.ui.StatusPill
import com.visualtrace.ai.core.util.Format
import com.visualtrace.ai.core.util.imageModel
import com.visualtrace.ai.domain.model.MatchType
import com.visualtrace.ai.domain.model.Provenance
import com.visualtrace.ai.domain.model.SearchResultItem
import com.visualtrace.ai.features.R
import com.visualtrace.ai.features.common.Intents

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultCard(
    item: SearchResultItem,
    saved: Boolean,
    onView: () -> Unit,
    onSave: () -> Unit,
    onReport: () -> Unit,
    onCopied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val ext = LocalVtColors.current
    val similarityText = item.similarityScore?.let { stringResource(R.string.similarity_label, Format.percent(it)) }
        ?: stringResource(R.string.similarity_unavailable)

    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(12.dp)) {
            Box(
                Modifier.size(104.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant)
                    .semantics { contentDescription = item.pageTitle ?: item.platform },
            ) {
                AsyncImage(imageModel(item.thumbnailUrl ?: item.imageUrl), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (item.frameTimestampMs != null) {
                    Row(Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Videocam, null, Modifier.size(12.dp), tint = Color.White)
                        Spacer(Modifier.width(3.dp))
                        Text(Format.duration(item.frameTimestampMs!!), style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val (matchLabel, matchColor) = when (item.matchType) {
                        MatchType.EXACT -> stringResource(R.string.match_exact) to ext.success
                        MatchType.CROPPED -> stringResource(R.string.match_cropped) to MaterialTheme.colorScheme.primary
                        MatchType.SIMILAR -> stringResource(R.string.match_similar) to MaterialTheme.colorScheme.secondary
                        MatchType.RELATED -> stringResource(R.string.match_related) to MaterialTheme.colorScheme.outline
                    }
                    StatusPill(matchLabel, matchColor)
                    if (item.isMock) StatusPill(stringResource(R.string.mock_badge), ext.warning)
                }
                Spacer(Modifier.height(4.dp))
                Text(item.platform, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                Text(item.pageTitle ?: Format.host(item.sourceUrl), style = MaterialTheme.typography.titleSmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text(Format.host(item.sourceUrl), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                item.similarityScore?.let { SimilarityMeter(it) }
                Text(similarityText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val meta = buildList {
            item.publishedAtEpochMs?.let { add(stringResource(R.string.published, Format.date(it))) }
            item.frameTimestampMs?.let { add(stringResource(R.string.frame_match, Format.duration(it))) }
            item.via?.let { add(stringResource(R.string.via_source, it)) }
            if (item.alsoFoundBy.size > 1) add(stringResource(R.string.also_found_by, item.alsoFoundBy.filter { it != item.providerKey }.joinToString()))
        }
        if (meta.isNotEmpty()) {
            Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp))
        }
        AnimatedVisibility(expanded) { PublicInfo(item) }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Intents.openUrl(context, item.sourceUrl) }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.open_website))
            }
            IconButton(onClick = onView, enabled = item.imageUrl != null || item.thumbnailUrl != null) {
                Icon(Icons.Outlined.Image, stringResource(R.string.view_image))
            }
            if (item.profileUrl != null) {
                IconButton(onClick = { Intents.openUrl(context, item.profileUrl!!) }) { Icon(Icons.Outlined.Person, stringResource(R.string.visit_profile)) }
            }
            IconButton(onClick = onSave) {
                Icon(if (saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    stringResource(if (saved) R.string.saved else R.string.save),
                    tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, stringResource(R.string.public_info))
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null) }
                val shareSubject = stringResource(R.string.share_subject)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.share)) }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = {
                        menu = false
                        Intents.share(context, shareSubject, listOfNotNull(item.pageTitle, item.sourceUrl).joinToString("\n"))
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.copy_url)) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = {
                        menu = false
                        Intents.copy(context, "url", item.sourceUrl)
                        onCopied()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.report)) }, leadingIcon = { Icon(Icons.Outlined.Flag, null) }, onClick = {
                        menu = false
                        onReport()
                    })
                }
            }
        }
    }
}

/** Public information with explicit provenance (from source / inferred / not available). */
@Composable
private fun PublicInfo(item: SearchResultItem) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.public_info), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        InfoField(stringResource(R.string.page_title), item.pageTitle, item.provenanceOf("page_title"))
        InfoField(stringResource(R.string.description), item.description, item.provenanceOf("description"))
        InfoField(stringResource(R.string.display_name), item.authorDisplayName, item.provenanceOf("author_display_name"))
        InfoField(stringResource(R.string.username), item.authorUsername?.let { "@$it" }, item.provenanceOf("author_username"))
        InfoField(stringResource(R.string.profile_url), item.profileUrl, item.provenanceOf("profile_url")) { item.profileUrl?.let { Intents.openUrl(context, it) } }
        InfoField(stringResource(R.string.bio), item.profileBio, item.provenanceOf("profile_bio"))
        InfoField(stringResource(R.string.website), item.websiteUrl, item.provenanceOf("website_url")) { item.websiteUrl?.let { Intents.openUrl(context, it) } }
        InfoField(stringResource(R.string.caption), item.caption, item.provenanceOf("caption"))
        InfoField(stringResource(R.string.published, ""), item.publishedAtEpochMs?.let { Format.dateTime(it) }, item.provenanceOf("published_at"))
        item.similarityMethod?.let {
            Text("${stringResource(R.string.measured)}: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoField(label: String, value: String?, provenance: Provenance, onClick: (() -> Unit)? = null) {
    val ext = LocalVtColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(label.trim(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (value != null) {
                if (onClick != null) {
                    AssistChip(onClick = onClick, label = { Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                } else {
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(stringResource(R.string.not_available), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        if (value != null && provenance != Provenance.UNAVAILABLE) {
            val (text, color) = when (provenance) {
                Provenance.PROVIDER -> stringResource(R.string.verified_source) to ext.success
                Provenance.MEASURED -> stringResource(R.string.measured) to MaterialTheme.colorScheme.primary
                else -> stringResource(R.string.inferred) to ext.warning
            }
            StatusPill(text, color)
        }
    }
}
