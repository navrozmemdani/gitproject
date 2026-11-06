package com.example.tempnumber

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tempnumber.ui.MainViewModel
import com.example.tempnumber.ui.UiState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    // Force the settings screen until configured.
    LaunchedEffect(state.configured) {
        if (!state.configured) showSettings = true
    }

    when {
        showSettings || !state.configured ->
            SettingsScreen(
                state = state,
                onSave = { url, key ->
                    vm.saveSettings(url, key)
                    showSettings = false
                },
                onBack = if (state.configured) ({ showSettings = false }) else null,
            )

        state.selectedNumber != null ->
            MessagesScreen(
                state = state,
                onBack = vm::clearSelection,
                onRefresh = vm::refreshMessages,
                onDismissError = vm::dismissError,
            )

        else ->
            NumbersScreen(
                state = state,
                onOpenSettings = { showSettings = true },
                onRefresh = vm::refreshNumbers,
                onSearch = vm::searchAvailable,
                onRent = vm::rent,
                onRelease = vm::release,
                onOpen = vm::openMessages,
                onDismissError = vm::dismissError,
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: UiState,
    onSave: (String, String) -> Unit,
    onBack: (() -> Unit)?,
) {
    var url by remember(state.settings.baseUrl) { mutableStateOf(state.settings.baseUrl) }
    var key by remember(state.settings.apiKey) { mutableStateOf(state.settings.apiKey) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backend settings") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Point the app at your backend. The API key is the APP_API_KEY " +
                    "you configured on the server.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Backend URL (https://...)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("API key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSave(url, key) },
                enabled = url.isNotBlank() && key.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumbersScreen(
    state: UiState,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onSearch: (String?) -> Unit,
    onRent: (String) -> Unit,
    onRelease: (String) -> Unit,
    onOpen: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    ErrorSnackbar(state.error, snackbar, onDismissError)
    var areaCode by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your numbers") },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = areaCode,
                    onValueChange = { areaCode = it },
                    label = { Text("Area code (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
                Button(onClick = { onSearch(areaCode) }) { Text("Find") }
            }

            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.available.isNotEmpty()) {
                    item {
                        Text("Available to rent", style = MaterialTheme.typography.titleMedium)
                    }
                    items(state.available) { n ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.fillMaxWidth(0.7f)) {
                                    Text(n.phoneNumber, style = MaterialTheme.typography.bodyLarge)
                                    val loc = listOfNotNull(n.locality, n.region)
                                        .joinToString(", ")
                                    if (loc.isNotBlank()) {
                                        Text(loc, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Button(onClick = { onRent(n.phoneNumber) }) { Text("Rent") }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                item { Text("Rented", style = MaterialTheme.typography.titleMedium) }
                if (state.numbers.isEmpty()) {
                    item {
                        Text(
                            "No numbers yet. Search by area code and rent one.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                items(state.numbers) { n ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                n.phoneNumber,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth(0.5f),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { onOpen(n.phoneNumber) }) { Text("Messages") }
                                OutlinedButton(onClick = { onRelease(n.phoneNumber) }) {
                                    Text("Release")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessagesScreen(
    state: UiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    ErrorSnackbar(state.error, snackbar, onDismissError)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.selectedNumber ?: "Messages") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.messages.isEmpty()) {
                Text(
                    "No messages yet. SMS sent to this number will appear here.",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LazyColumn(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages) { m ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "From ${m.from}",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(m.body, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(m.receivedAt, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorSnackbar(
    error: String?,
    host: SnackbarHostState,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(error) {
        if (error != null) {
            host.showSnackbar(error)
            onDismiss()
        }
    }
}
