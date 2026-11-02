package com.visualtrace.ai.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.visualtrace.ai.core.designsystem.LocalVtColors
import com.visualtrace.ai.domain.model.SessionState
import com.visualtrace.ai.features.R
import com.visualtrace.ai.features.common.toText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.profile_title)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(16.dp)) {
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        val account = (session as? SessionState.SignedIn)?.account
                        Box(Modifier.size(64.dp).background(LocalVtColors.current.accentGradient, CircleShape), contentAlignment = Alignment.Center) {
                            if (account?.photoUrl != null) AsyncImage(account.photoUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Icon(Icons.Outlined.Person, null, Modifier.size(32.dp), tint = androidx.compose.ui.graphics.Color.White)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(account?.displayName ?: account?.email ?: stringResource(R.string.guest_user), style = MaterialTheme.typography.titleLarge)
                            if (account != null) Text(account.email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else Text(stringResource(R.string.guest_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (session is SessionState.SignedIn) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.cloud_sync)) },
                        supportingContent = { Text(stringResource(R.string.cloud_sync_body)) },
                        leadingContent = { Icon(Icons.Outlined.CloudSync, null) },
                        trailingContent = { Switch(settings.cloudSyncEnabled, viewModel::setCloudSync) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                    if (settings.cloudSyncEnabled) {
                        OutlinedButton(onClick = viewModel::syncNow, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sync_now)) }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.DeleteForever, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_server_data))
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sign_out))
                    }
                } else {
                    OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.delete_server_data))
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = viewModel::signIn, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Login, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sign_in))
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.delete_server_data)) },
            text = { Text(stringResource(R.string.delete_server_data_body)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteServerData(); confirmDelete = false }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } })
    }
    message?.let { m ->
        val text = when (m) {
            is ProfileMessage.Synced -> stringResource(R.string.synced_items, m.count)
            ProfileMessage.Deleted -> stringResource(R.string.data_deleted)
            is ProfileMessage.Error -> m.error.toText().message
        }
        AlertDialog(onDismissRequest = viewModel::clearMessage, text = { Text(text) },
            confirmButton = { TextButton(onClick = viewModel::clearMessage) { Text("OK") } })
    }
}
