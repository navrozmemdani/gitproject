package com.visualtrace.ai.features.legal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.visualtrace.ai.features.R

/** Renders the bundled privacy policy / terms / about documents (simple Markdown-like text). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(document: String, versionName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val (titleRes, rawRes) = when (document) {
        "privacy" -> R.string.privacy_policy to R.raw.privacy_policy
        "terms" -> R.string.terms to R.raw.terms
        else -> R.string.about to R.raw.about
    }
    val lines = remember(rawRes) {
        context.resources.openRawResource(rawRes).bufferedReader().use { it.readText() }.replace("{version}", versionName).lines()
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(titleRes)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(20.dp)) {
                lines.forEach { line ->
                    when {
                        line.startsWith("# ") -> Text(line.removePrefix("# "), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 8.dp))
                        line.startsWith("## ") -> Text(line.removePrefix("## "), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                        line.startsWith("- ") -> Text("•  " + line.removePrefix("- "), style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp, bottom = 2.dp))
                        line.isBlank() -> Text("", style = MaterialTheme.typography.bodySmall)
                        else -> Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
