package com.jc84.blesnooplogger.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jc84.blesnooplogger.data.model.BlePacket
import com.jc84.blesnooplogger.ui.theme.EventColor
import com.jc84.blesnooplogger.ui.theme.RxColor
import com.jc84.blesnooplogger.ui.theme.TxColor
import com.jc84.blesnooplogger.viewmodel.DeviceInfo

@Composable
fun FilterBar(
    devices: List<DeviceInfo>,
    selectedAddress: String,
    onSelectAddress: (String) -> Unit,
    onClearFilter: () -> Unit,
    selectedDirection: BlePacket.Direction?,
    onSelectDirection: (BlePacket.Direction?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        // Direction filter row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = selectedDirection == null,
                onClick = { onSelectDirection(null) },
                label = { Text("All", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
            FilterChip(
                selected = selectedDirection == BlePacket.Direction.TX,
                onClick = { onSelectDirection(BlePacket.Direction.TX) },
                label = { Text("TX", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TxColor,
                    selectedLabelColor = MaterialTheme.colorScheme.surface,
                ),
            )
            FilterChip(
                selected = selectedDirection == BlePacket.Direction.RX,
                onClick = { onSelectDirection(BlePacket.Direction.RX) },
                label = { Text("RX", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = RxColor,
                    selectedLabelColor = MaterialTheme.colorScheme.surface,
                ),
            )
            FilterChip(
                selected = selectedDirection == BlePacket.Direction.EVENT,
                onClick = { onSelectDirection(BlePacket.Direction.EVENT) },
                label = { Text("EV", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = EventColor,
                    selectedLabelColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }

        // Device filter row
        if (devices.isNotEmpty()) {
            Text(
                text = "Devices:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // "All" chip
                FilterChip(
                    selected = selectedAddress.isBlank(),
                    onClick = { onClearFilter() },
                    label = { Text("All", style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )

                // One chip per discovered device
                for (device in devices) {
                    FilterChip(
                        selected = selectedAddress == device.address,
                        onClick = { onSelectAddress(device.address) },
                        label = {
                            Text(
                                text = device.displayName,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }
    }
}
