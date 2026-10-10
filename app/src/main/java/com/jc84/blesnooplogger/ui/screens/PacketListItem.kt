package com.jc84.blesnooplogger.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jc84.blesnooplogger.data.model.BlePacket
import com.jc84.blesnooplogger.ui.theme.EventColor
import com.jc84.blesnooplogger.ui.theme.HexDataColor
import com.jc84.blesnooplogger.ui.theme.RxColor
import com.jc84.blesnooplogger.ui.theme.RxColorDim
import com.jc84.blesnooplogger.ui.theme.TxColor
import com.jc84.blesnooplogger.ui.theme.TxColorDim

private val EventColorDim = Color(0xFF2A2000)

@Composable
fun PacketListItem(
    packet: BlePacket,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(packet.index) { mutableStateOf(false) }

    val containerColor = when (packet.direction) {
        BlePacket.Direction.TX -> TxColorDim
        BlePacket.Direction.RX -> RxColorDim
        BlePacket.Direction.EVENT -> EventColorDim
    }
    val directionColor = when (packet.direction) {
        BlePacket.Direction.TX -> TxColor
        BlePacket.Direction.RX -> RxColor
        BlePacket.Direction.EVENT -> EventColor
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = packet.direction.label,
                    color = directionColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = packet.formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = packet.opcode,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = packet.packetType,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }

            // Resolved GATT characteristic/descriptor name, when known.
            if (packet.handleName != null) {
                Text(
                    text = packet.handleName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }

            if (packet.dataHex.isNotEmpty()) {
                Text(
                    text = packet.dataHex,
                    style = MaterialTheme.typography.labelSmall,
                    color = HexDataColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            if (expanded) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = "Address: ${packet.deviceAddress}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (packet.handleHex.isNotEmpty()) {
                        Text(
                            text = "Handle: ${packet.handleHex}" +
                                (packet.handleName?.let { "  →  $it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (packet.dataAscii.isNotEmpty()) {
                        Text(
                            text = "ASCII: ${packet.dataAscii}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}
