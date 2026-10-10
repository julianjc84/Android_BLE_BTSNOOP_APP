package com.jc84.blesnooplogger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jc84.blesnooplogger.viewmodel.CaptureViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: CaptureViewModel,
    onBack: () -> Unit,
) {

    val csvEnabled by viewModel.csvEnabled.collectAsState()
    val showAdvertising by viewModel.showAdvertising.collectAsState()
    val csvFile by viewModel.csvFile.collectAsState()
    val snoopFileSize by viewModel.snoopFileSize.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearConfirmation by remember { mutableStateOf(false) }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear snoop log?") },
            text = {
                Text("This performs a 5-step reset: toggles HCI logging off/on and cycles Bluetooth to force a fresh log file. Takes ~10 seconds. Active connections will be dropped.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        viewModel.resetSnoopLog()
                    },
                ) {
                    Text("Clear", color = Color(0xFFEF5350))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshSnoopFileSize()
            kotlinx.coroutines.delay(2_000)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // --- BT Snoop Log section ---
            SectionHeader("BT Snoop Log")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (snoopFileSize != null) "btsnoop_hci.log \u2014 $snoopFileSize"
                        else "btsnoop_hci.log \u2014 unknown size",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Raw HCI log file stored at /data/misc/bluetooth/logs/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { showClearConfirmation = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF5350),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Clear snoop log")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Toggles HCI logging off/on and cycles Bluetooth to force a fresh log file. Takes ~10 seconds. Active connections will be dropped.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Recovery steps",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Android HCI snoop logging has known race conditions. " +
                            "If the log file becomes stale or stops updating, follow these steps:\n\n" +
                            "1) BT on\n" +
                            "2) HCI off\n" +
                            "3) BT off \u2192 on\n" +
                            "4) HCI on\n" +
                            "5) BT off \u2192 on\n\n" +
                            "Note: BLE scanning continues writing even with BT \"off\" " +
                            "due to background BLE scan traffic.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.copyRawSnoopFile() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Copy raw btsnoop file")
                    }
                    Text(
                        text = "Copies the raw HCI snoop log to Download/BTSnoopLog/ with a timestamped filename.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.openExportFolder() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open export folder")
                    }
                    Text(
                        text = "Browse saved exports in Download/BTSnoopLog/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }

            // --- Capture options section ---
            SectionHeader("Capture")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Show advertising packets",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = showAdvertising,
                            onCheckedChange = { viewModel.toggleShowAdvertising() },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Decodes LE advertising broadcasts (name, service UUIDs, " +
                            "manufacturer data, TX power) from all nearby devices and shows them " +
                            "as ADV packets. Off by default to keep the stream focused on " +
                            "connected devices. Applies to the next capture.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }

            // --- Live Recording CSV section ---
            SectionHeader("Live Recording CSV")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Live CSV recording",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = csvEnabled,
                            onCheckedChange = { viewModel.toggleCsv() },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (csvEnabled && csvFile != null) "Recording to $csvFile"
                        else if (csvEnabled) "Enabled \u2014 will start recording on next capture"
                        else "Disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (csvEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Writes each BLE packet to a CSV file in real time during capture, " +
                            "with no packet limit.\n\n" +
                            "Saves to: Download/BTSnoopLog/CSV/\n" +
                            "e.g. live_ble_capture_20260527_143052.csv",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.openCsvFolder() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open CSV folder")
                    }
                    Text(
                        text = "Browse saved CSV files in Download/BTSnoopLog/CSV/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}
