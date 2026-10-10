package com.jc84.blesnooplogger.ui.screens

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jc84.blesnooplogger.viewmodel.CaptureViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(viewModel: CaptureViewModel, onOpenSettings: () -> Unit) {
    val captureState by viewModel.captureState.collectAsState()
    val packets by viewModel.displayPackets.collectAsState()
    val packetCount by viewModel.packetCount.collectAsState()
    val selectedAddress by viewModel.selectedAddress.collectAsState()
    val selectedDirection by viewModel.selectedDirection.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val csvEnabled by viewModel.csvEnabled.collectAsState()
    val csvFile by viewModel.csvFile.collectAsState()
    val csvRowCount by viewModel.csvRowCount.collectAsState()
    val pauseBufferedCount by viewModel.pauseBufferedCount.collectAsState()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showStopConfirmation by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveFilename by remember { mutableStateOf("") }
    var saveDeviceAddress by remember { mutableStateOf("") }

    // --- Scroll / follow-tail state machine ---
    //
    // followTail   : true while the viewport should track the live stream.
    //                Flipped to false when the user drags the list away from the bottom,
    //                flipped to true when they drag back to the bottom or tap the FAB.
    // userDragging : true while a finger is on the list. Auto-follow is paused for the
    //                whole gesture so we never fight the user's input.
    // lastSeenIndex: the newest packet.index the user has "seen" — used to count packets
    //                that have arrived while they're browsing history.
    var followTail by remember { mutableStateOf(true) }
    var userDragging by remember { mutableStateOf(false) }
    var lastSeenIndex by remember { mutableLongStateOf(-1L) }

    // Track finger-on-list interaction (drag), not animation-driven scrolling.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> userDragging = true
                is DragInteraction.Stop, is DragInteraction.Cancel -> userDragging = false
            }
        }
    }

    // When the user finishes touching (drag + any fling settles), decide whether to follow.
    LaunchedEffect(userDragging) {
        if (!userDragging) {
            snapshotFlow { listState.isScrollInProgress }.first { !it }
            followTail = listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0
        }
    }

    val newestPacketIndex = packets.firstOrNull()?.index

    // Auto-follow: smooth scroll to newest on new packet, but only when following AND
    // not currently dragging — so the user's gesture is never interrupted.
    LaunchedEffect(newestPacketIndex) {
        if (followTail && !userDragging && newestPacketIndex != null) {
            listState.animateScrollToItem(0)
        }
    }

    // While following, the "newest seen" pointer tracks the head of the buffer.
    // Once the user disengages, this freezes — and newPacketsBelow grows.
    LaunchedEffect(newestPacketIndex, followTail) {
        if (followTail) lastSeenIndex = newestPacketIndex ?: -1L
    }

    // Reset state when the buffer is cleared (e.g. a new capture starts).
    LaunchedEffect(packets.isEmpty()) {
        if (packets.isEmpty()) {
            followTail = true
            lastSeenIndex = -1L
        }
    }

    val newPacketsBelow by remember {
        derivedStateOf {
            if (followTail) 0L
            else {
                val newest = packets.firstOrNull()?.index ?: return@derivedStateOf 0L
                (newest - lastSeenIndex).coerceAtLeast(0L)
            }
        }
    }

    val showScrollToBottom by remember {
        derivedStateOf { newPacketsBelow > 0 }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { snackbarHostState.showSnackbar(it) }
    }

    if (showStopConfirmation) {
        StopConfirmationDialog(
            onConfirm = {
                showStopConfirmation = false
                viewModel.startOrStopCapture()
            },
            onDismiss = { showStopConfirmation = false },
        )
    }

    if (showClearConfirmation) {
        ClearConfirmationDialog(
            packetCount = packetCount,
            onConfirm = {
                showClearConfirmation = false
                viewModel.clearPackets()
            },
            onDismiss = { showClearConfirmation = false },
        )
    }

    if (showSaveDialog) {
        SaveDialog(
            devices = discoveredDevices,
            selectedDeviceAddress = saveDeviceAddress,
            onSelectDevice = { saveDeviceAddress = it },
            countForAddress = { viewModel.countForAddress(it) },
            filename = saveFilename,
            onFilenameChange = { saveFilename = it },
            onConfirm = {
                val name = saveFilename
                val device = saveDeviceAddress
                showSaveDialog = false
                viewModel.saveCapture(name, device)
            },
            onDismiss = { showSaveDialog = false },
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 80.dp),
            )
        },
        topBar = {
            CaptureTopBar(
                captureState = captureState,
                packetCount = packetCount,
                maxPackets = CaptureViewModel.MAX_PACKETS,
                selectedAddress = selectedAddress,
                onOpenCsvFolder = { viewModel.openCsvFolder() },
                onOpenSettings = onOpenSettings,
            )
        },
        floatingActionButton = {
            CaptureFabStack(
                captureState = captureState,
                showScrollToBottom = showScrollToBottom,
                newPacketsBelow = newPacketsBelow,
                pauseBufferedCount = pauseBufferedCount,
                onClear = { showClearConfirmation = true },
                onScrollToBottom = {
                    followTail = true
                    lastSeenIndex = newestPacketIndex ?: -1L
                    scope.launch { listState.animateScrollToItem(0) }
                },
                onPauseResume = {
                    if (captureState is com.jc84.blesnooplogger.data.capture.CaptureState.Paused) {
                        viewModel.resumeCapture()
                    } else {
                        viewModel.pauseCapture()
                    }
                },
                onStop = { showStopConfirmation = true },
                onSave = {
                    saveFilename = viewModel.suggestedExportFilename()
                    saveDeviceAddress = selectedAddress
                    showSaveDialog = true
                },
                onStart = { viewModel.startOrStopCapture() },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Only while the writer is actually open — csvFile is cleared on close, so this
            // can't linger after Stop the way a bare csvEnabled check would.
            val recFile = csvFile
            if (csvEnabled && recFile != null) {
                RecordingBanner(
                    csvFile = recFile,
                    rowCount = csvRowCount,
                    deviceScope = selectedAddress.takeIf { it.isNotBlank() }?.let { addr ->
                        discoveredDevices.firstOrNull { it.address == addr }?.displayName ?: addr
                    },
                )
            }
            FilterBar(
                devices = discoveredDevices,
                selectedAddress = selectedAddress,
                onSelectAddress = { viewModel.selectAddress(it) },
                onClearFilter = { viewModel.clearAddressFilter() },
                selectedDirection = selectedDirection,
                onSelectDirection = { viewModel.selectDirection(it) },
            )
            PacketList(
                packets = packets,
                listState = listState,
                captureState = captureState,
                maxPackets = CaptureViewModel.MAX_PACKETS,
            )
        }
    }
}
