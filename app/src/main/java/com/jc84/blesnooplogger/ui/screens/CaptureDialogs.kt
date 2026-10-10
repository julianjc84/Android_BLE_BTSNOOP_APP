package com.jc84.blesnooplogger.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.jc84.blesnooplogger.viewmodel.DeviceInfo

@Composable
fun StopConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stop tailing btsnoop log?") },
        text = {
            Text("This will stop tailing the btsnoop HCI log and end the session. Packets already captured will remain in the buffer.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Stop", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun ClearConfirmationDialog(
    packetCount: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear all packets?") },
        text = { Text("This will clear all $packetCount packets from the buffer. This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Clear", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveDialog(
    devices: List<DeviceInfo>,
    selectedDeviceAddress: String,
    onSelectDevice: (String) -> Unit,
    countForAddress: (String) -> Int,
    filename: String,
    onFilenameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val savingCount = countForAddress(selectedDeviceAddress)

    // Hoist the filename as a TextFieldValue so the cursor/selection survives the
    // recompositions triggered by the device picker — otherwise double-tap-to-select
    // gets reset. Seeded once per dialog open (SaveDialog leaves composition on dismiss).
    var filenameField by remember {
        // Cursor at the very start so a name/prefix can be typed in front of the suggested filename.
        mutableStateOf(TextFieldValue(filename, selection = TextRange(0)))
    }

    AlertDialog(
        // Only Save / Cancel close the dialog — ignore scrim taps and back press.
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
        onDismissRequest = onDismiss,
        title = { Text("Save capture") },
        text = {
            Column {
                Text("Save $savingCount packets to Download/BTSnoopLog/CSV/")

                // Device (MAC) filter — pick a single device for a clean per-MAC CSV.
                if (devices.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    var expanded by remember { mutableStateOf(false) }
                    val currentLabel = if (selectedDeviceAddress.isBlank()) {
                        "All devices (${countForAddress("")})"
                    } else {
                        devices.find { it.address == selectedDeviceAddress }
                            ?.let { "${it.displayName} (${countForAddress(it.address)})" }
                            ?: "$selectedDeviceAddress (${countForAddress(selectedDeviceAddress)})"
                    }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        OutlinedTextField(
                            value = currentLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Device filter") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("All devices (${countForAddress("")})") },
                                onClick = { onSelectDevice(""); expanded = false },
                            )
                            for (device in devices) {
                                DropdownMenuItem(
                                    text = { Text("${device.displayName} (${countForAddress(device.address)})") },
                                    onClick = { onSelectDevice(device.address); expanded = false },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = filenameField,
                    onValueChange = {
                        filenameField = it
                        onFilenameChange(it.text)
                    },
                    label = { Text("Filename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = filenameField.text.isNotBlank() && savingCount > 0,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
