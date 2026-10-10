package com.jc84.blesnooplogger.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jc84.blesnooplogger.data.capture.CaptureState
import com.jc84.blesnooplogger.data.model.BlePacket

@Composable
fun PacketList(
    packets: List<BlePacket>,
    listState: LazyListState,
    captureState: CaptureState,
    maxPackets: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (packets.isEmpty()) {
            EmptyStateHelp(
                isCapturing = captureState is CaptureState.Capturing,
                maxPackets = maxPackets,
                modifier = Modifier.padding(16.dp),
            )
        }

        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .fillMaxSize()
                .drawScrollbar(listState, packets.size),
        ) {
            items(
                items = packets,
                key = { it.index },
            ) { packet ->
                // animateItem() smoothly slides existing rows to their new position
                // when new packets prepend, so a batch insert looks like a slide
                // rather than a teleport.
                PacketListItem(packet = packet, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun EmptyStateHelp(
    isCapturing: Boolean,
    maxPackets: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (isCapturing) {
            Text(
                text = "Waiting for BLE packets...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        } else {
            Text(
                text = "Press play to start",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Play:\n" +
                    "1. Clears any previous packets and discovered devices\n" +
                    "2. Tails the HCI snoop log at /data/misc/bluetooth/logs/btsnoop_hci.log via root shell\n" +
                    "3. Parses incoming HCI data into BLE packets (buffered up to $maxPackets)\n" +
                    "4. If live CSV recording is enabled, packets are also written to disk\n\n" +
                    "Pause:\n" +
                    "1. Freezes the display — packets continue to be read and buffered in the background\n" +
                    "2. Resume unfreezes the display and shows all packets that arrived while paused\n\n" +
                    "Stop:\n" +
                    "1. Stops reading the snoop log and kills the root shell\n" +
                    "2. Closes the CSV writer if active\n" +
                    "3. Packets remain in the buffer — you can still scroll, filter, or export them\n\n" +
                    "Save (after stop):\n" +
                    "1. Prompts for a filename, then writes the buffer to Download/BTSnoopLog/CSV/\n" +
                    "2. Returns to idle — press play to start a new capture\n\n" +
                    "Clear (always visible):\n" +
                    "1. Clears all packets from the buffer and resets the packet count\n" +
                    "2. Asks for confirmation before clearing",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}

/** Thin scrollbar on the right edge. */
private fun Modifier.drawScrollbar(
    listState: LazyListState,
    totalItems: Int,
): Modifier = drawWithContent {
    drawContent()
    if (totalItems == 0) return@drawWithContent

    val firstVisible = listState.firstVisibleItemIndex
    val visibleCount = listState.layoutInfo.visibleItemsInfo.size
    if (visibleCount >= totalItems) return@drawWithContent

    val scrollbarHeight = (visibleCount.toFloat() / totalItems) * size.height
    // reverseLayout = true means index 0 is at the bottom; flip the scrollbar so it visually tracks the viewport.
    val scrollbarY = size.height - scrollbarHeight - (firstVisible.toFloat() / totalItems) * size.height
    val scrollbarWidth = 4.dp.toPx()

    drawRect(
        color = Color.White.copy(alpha = 0.3f),
        topLeft = Offset(size.width - scrollbarWidth, scrollbarY.coerceAtLeast(0f)),
        size = Size(scrollbarWidth, scrollbarHeight.coerceAtLeast(24.dp.toPx())),
    )
}
