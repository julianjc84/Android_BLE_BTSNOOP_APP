package com.jc84.blesnooplogger.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import com.jc84.blesnooplogger.data.capture.CaptureState
import com.jc84.blesnooplogger.ui.theme.PausedColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureTopBar(
    captureState: CaptureState,
    packetCount: Long,
    maxPackets: Int,
    selectedAddress: String,
    onOpenCsvFolder: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val isPaused = captureState is CaptureState.Paused
    val isStopped = captureState is CaptureState.Stopped

    TopAppBar(
        title = {
            Column {
                Text("BLE Snoop")
                Text(
                    // "captured" and "held" are unrelated quantities — a slash between them
                    // reads as progress toward a limit, which it isn't. The ring buffer only
                    // bounds the list and the Save button, never the live CSV.
                    text = buildString {
                        append("$packetCount captured")
                        if (packetCount > maxPackets) append(" · newest $maxPackets held")
                        if (selectedAddress.isNotBlank()) append(" | filtered")
                        if (isPaused) append(" | PAUSED")
                        if (isStopped) append(" | STOPPED")
                        if (captureState is CaptureState.Error) append(" | ${captureState.message}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPaused) PausedColor
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        },
        actions = {
            IconButton(onClick = onOpenCsvFolder) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Open CSV folder")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}
