package com.jc84.blesnooplogger.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jc84.blesnooplogger.data.capture.CaptureState
import com.jc84.blesnooplogger.data.capture.RootSnoopCapture
import com.jc84.blesnooplogger.data.csv.CsvWriter
import com.jc84.blesnooplogger.data.model.BlePacket
import com.jc84.blesnooplogger.data.model.BlePacket.Direction
import com.jc84.blesnooplogger.data.util.RootShell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(FlowPreview::class)
class CaptureViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MAX_PACKETS = 5000

        /** ~30fps cap on UI snapshots — fast enough to feel live, slow enough to coalesce bursts. */
        private const val UI_REFRESH_INTERVAL_MS = 33L
    }

    private val capture = RootSnoopCapture()
    private var captureJob: Job? = null

    // --- Observable state ---

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _packetCount = MutableStateFlow(0L)
    val packetCount: StateFlow<Long> = _packetCount.asStateFlow()

    private val _selectedAddress = MutableStateFlow("")
    val selectedAddress: StateFlow<String> = _selectedAddress.asStateFlow()

    private val _selectedDirection = MutableStateFlow<Direction?>(null)
    val selectedDirection: StateFlow<Direction?> = _selectedDirection.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<DeviceInfo>> = _discoveredDevices.asStateFlow()

    private val _csvEnabled = MutableStateFlow(false)
    val csvEnabled: StateFlow<Boolean> = _csvEnabled.asStateFlow()

    private val _showAdvertising = MutableStateFlow(false)
    val showAdvertising: StateFlow<Boolean> = _showAdvertising.asStateFlow()

    private val _csvFile = MutableStateFlow<String?>(null)
    val csvFile: StateFlow<String?> = _csvFile.asStateFlow()

    /** Rows written to the live CSV since the writer opened. Unbounded — not tied to MAX_PACKETS. */
    private val _csvRowCount = MutableStateFlow(0L)
    val csvRowCount: StateFlow<Long> = _csvRowCount.asStateFlow()

    private val _lastExportFile = MutableStateFlow<File?>(null)
    val lastExportFile: StateFlow<File?> = _lastExportFile.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    private val _snoopFileSize = MutableStateFlow<String?>(null)
    val snoopFileSize: StateFlow<String?> = _snoopFileSize.asStateFlow()

    /** When true, displayPackets returns the buffer snapshot from when pause was entered. */
    private val _isPaused = MutableStateFlow(false)

    /** Packets that have arrived while paused — surfaced as a FAB so the user knows how much catches up on resume. */
    private val _pauseBufferedCount = MutableStateFlow(0L)
    val pauseBufferedCount: StateFlow<Long> = _pauseBufferedCount.asStateFlow()

    // --- Internal state ---

    /** Source of truth. Mutated only on the viewModelScope (Main) dispatcher. */
    private val ringBuffer = ArrayDeque<BlePacket>(MAX_PACKETS)
    private val deviceSet = mutableSetOf<String>()
    private var csvWriter: CsvWriter? = null

    /** Signal a UI snapshot is needed. Sampled at UI_REFRESH_INTERVAL_MS downstream. */
    private val refreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Buffer snapshot captured the moment pause was entered, so pause is stable across filter toggles. */
    private var pauseSnapshot: List<BlePacket> = emptyList()

    /**
     * Packets shown in the UI — newest first (index 0 is newest).
     *
     * Re-derived at most every UI_REFRESH_INTERVAL_MS from a snapshot of the ring buffer.
     * Filter changes re-derive immediately. While paused, returns the frozen snapshot.
     */
    val displayPackets: StateFlow<List<BlePacket>> = combine(
        refreshTrigger.sample(UI_REFRESH_INTERVAL_MS).onStart { emit(Unit) },
        _selectedAddress,
        _selectedDirection,
        _isPaused,
    ) { _, addr, dir, paused -> Snap(addr, dir, paused) }
        .map { snap -> snapshot(snap) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private data class Snap(val addr: String, val dir: Direction?, val paused: Boolean)

    private fun snapshot(snap: Snap): List<BlePacket> {
        val source = if (snap.paused) pauseSnapshot else ringBuffer.toList()
        val filtered = if (snap.addr.isBlank() && snap.dir == null) {
            source
        } else {
            source.filter { p ->
                (snap.addr.isBlank() || p.deviceAddress == snap.addr) &&
                    (snap.dir == null || p.direction == snap.dir)
            }
        }
        // Newest first so LazyColumn with reverseLayout=true grows off-screen.
        return filtered.asReversed()
    }

    // --- Capture lifecycle ---

    fun startOrStopCapture() {
        when (_captureState.value) {
            is CaptureState.Idle, is CaptureState.Error, is CaptureState.Stopped -> startCapture()
            is CaptureState.Capturing, is CaptureState.Paused -> stopCapture()
        }
    }

    fun pauseCapture() {
        if (_captureState.value is CaptureState.Capturing) {
            pauseSnapshot = ringBuffer.toList()
            _pauseBufferedCount.value = 0L
            _isPaused.value = true
            _captureState.value = CaptureState.Paused
            _snackbar.tryEmit("Display paused — still reading packets in background")
        }
    }

    fun resumeCapture() {
        if (_captureState.value is CaptureState.Paused) {
            _isPaused.value = false
            pauseSnapshot = emptyList()
            _pauseBufferedCount.value = 0L
            _captureState.value = CaptureState.Capturing
            _snackbar.tryEmit("Display resumed")
            refreshTrigger.tryEmit(Unit)
        }
    }

    private fun startCapture() {
        Timber.i("System: starting capture")
        _snackbar.tryEmit("Starting capture...")
        _captureState.value = CaptureState.Capturing
        ringBuffer.clear()
        deviceSet.clear()
        _packetCount.value = 0
        _discoveredDevices.value = emptyList()
        _isPaused.value = false
        pauseSnapshot = emptyList()
        _pauseBufferedCount.value = 0L
        refreshTrigger.tryEmit(Unit)

        capture.onDeviceDiscovered = { address -> addDiscoveredDevice(address) }
        capture.includeAdvertising = _showAdvertising.value

        if (_csvEnabled.value) openCsvWriter()

        captureJob = viewModelScope.launch {
            try {
                capture.startCapture().collect { packet ->
                    // Advertising packets come from all nearby devices — don't clutter the
                    // device picker with them; it lists connected/communicating devices only.
                    if (packet.packetType != "ADV" && packet.deviceAddress.contains(":")) {
                        addDiscoveredDevice(packet.deviceAddress)
                    }
                    addPacket(packet)
                }
                // Flow ended on its own (tail -f exited normally — file gone,
                // process killed externally, etc). Only transition state here if
                // stopCapture() hasn't already moved us, otherwise we'd clobber
                // the Stopped state and the Save FAB would never appear.
                if (_captureState.value is CaptureState.Capturing) {
                    _captureState.value = if (ringBuffer.isNotEmpty()) {
                        CaptureState.Stopped
                    } else {
                        CaptureState.Idle
                    }
                }
            } catch (e: CancellationException) {
                Timber.i("Bluetooth: capture cancelled")
                throw e
            } catch (e: Exception) {
                // Don't overwrite the Stopped/Idle state set by stopCapture() if the
                // exception is just a side-effect of our own teardown (e.g. the tail
                // process being destroyed).
                if (_captureState.value is CaptureState.Capturing) {
                    Timber.e("Bluetooth: capture error: %s", e.message)
                    _captureState.value = CaptureState.Error(e.message ?: "Unknown error")
                } else {
                    Timber.i("Bluetooth: capture ended during stop (%s)", e.message)
                }
            } finally {
                closeCsvWriter()
            }
        }
    }

    private fun stopCapture() {
        Timber.i("System: stopping capture")
        _snackbar.tryEmit("Capture stopped")
        capture.stop()
        captureJob?.cancel()
        captureJob = null
        closeCsvWriter()
        _isPaused.value = false
        pauseSnapshot = emptyList()
        _pauseBufferedCount.value = 0L
        _captureState.value = if (ringBuffer.isNotEmpty()) CaptureState.Stopped else CaptureState.Idle
        refreshTrigger.tryEmit(Unit)
    }

    private fun addPacket(packet: BlePacket) {
        if (ringBuffer.size >= MAX_PACKETS) ringBuffer.removeFirst()
        ringBuffer.addLast(packet)
        _packetCount.value++

        // Live CSV honours the device filter so a locked-on MAC records clean, single-device logs.
        // The direction filter is a view-only aid and never gates recording (we always want TX + RX).
        val recFilter = _selectedAddress.value
        if (recFilter.isBlank() || packet.deviceAddress == recFilter) {
            csvWriter?.let {
                it.writePacket(packet)
                _csvRowCount.value++
            }
        }

        if (_isPaused.value) _pauseBufferedCount.value++

        // Refresh protocol labels every 20 packets — capture identifies protocols after first data byte.
        if (_packetCount.value % 20 == 0L) refreshDeviceLabels()

        refreshTrigger.tryEmit(Unit)
    }

    // --- Filters ---

    fun selectAddress(address: String) {
        _selectedAddress.value = address
    }

    fun clearAddressFilter() {
        _selectedAddress.value = ""
    }

    fun selectDirection(direction: Direction?) {
        _selectedDirection.value = direction
    }

    // --- Devices ---

    private fun addDiscoveredDevice(address: String) {
        if (address.contains(":") && deviceSet.add(address)) {
            val label = capture.getProtocolLabel(address)
            val name = capture.getDeviceName(address)
            val devices = _discoveredDevices.value.toMutableList()
            devices.add(DeviceInfo(address = address, protocolLabel = label, name = name))
            _discoveredDevices.value = devices
            Timber.i("Bluetooth: discovered device %s%s", address, if (label != null) " ($label)" else "")
        }
    }

    private fun refreshDeviceLabels() {
        val updated = _discoveredDevices.value.map { device ->
            val label = capture.getProtocolLabel(device.address)
            val name = capture.getDeviceName(device.address)
            var d = device
            if (label != null && label != d.protocolLabel) d = d.copy(protocolLabel = label)
            if (name != null && name != d.name) d = d.copy(name = name)
            d
        }
        if (updated != _discoveredDevices.value) _discoveredDevices.value = updated
    }

    // --- Clear / save ---

    fun clearPackets() {
        ringBuffer.clear()
        _packetCount.value = 0
        pauseSnapshot = emptyList()
        // Clearing a finished capture is effectively a new session — drop the Stopped
        // state (and its Save FAB) back to Idle. Active captures keep running.
        if (_captureState.value is CaptureState.Stopped) _captureState.value = CaptureState.Idle
        refreshTrigger.tryEmit(Unit)
        _snackbar.tryEmit("Packets cleared")
    }

    /**
     * Save the current buffer to a CSV. Stays in the Stopped state so the capture can be
     * saved again — e.g. one clean CSV per device. Start a new capture when finished.
     */
    fun saveCapture(filename: String? = null, deviceAddress: String = "") {
        exportCsv(filename, deviceAddress)
    }

    /** Buffered packet count for a device address ("" = all devices). Used by the Save dialog. */
    fun countForAddress(address: String): Int =
        if (address.isBlank()) ringBuffer.size
        else ringBuffer.count { it.deviceAddress == address }

    fun suggestedExportFilename(): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "ble_capture_$ts.csv"
    }

    private fun sanitizeFilename(input: String): String {
        val cleaned = input.trim().replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_")
        val nonEmpty = if (cleaned.isBlank()) suggestedExportFilename() else cleaned
        return if (nonEmpty.endsWith(".csv", ignoreCase = true)) nonEmpty else "$nonEmpty.csv"
    }

    fun exportCsv(filename: String? = null, deviceAddress: String = "") {
        val capturesDir = File(android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS), "BTSnoopLog/CSV")
        val writer = CsvWriter(capturesDir)
        val resolvedName = filename?.let { sanitizeFilename(it) }
        val file = writer.open(filename = resolvedName)
        // Filter the export to a single device MAC so each saved CSV is clean per-device.
        val source = ringBuffer.toList().let { all ->
            if (deviceAddress.isBlank()) all else all.filter { it.deviceAddress == deviceAddress }
        }
        for (packet in source) writer.writePacket(packet)
        writer.close()
        _lastExportFile.value = file
        val scope = if (deviceAddress.isBlank()) "" else " ($deviceAddress)"
        Timber.i("FileSystem: exported %d packets%s to %s", source.size, scope, file.name)
        _snackbar.tryEmit("Exported ${source.size} packets$scope to ${file.name}")
    }

    // --- CSV live recording ---

    fun toggleCsv() {
        _csvEnabled.value = !_csvEnabled.value
        if (_csvEnabled.value && _captureState.value is CaptureState.Capturing) {
            openCsvWriter()
            _snackbar.tryEmit("Live CSV recording started")
        } else if (_csvEnabled.value) {
            _snackbar.tryEmit("Live CSV enabled — will record on next capture")
        } else {
            closeCsvWriter()
            _snackbar.tryEmit("Live CSV recording stopped")
        }
    }

    /** Toggle whether LE advertising reports are shown as ADV packets. Applies to the next capture. */
    fun toggleShowAdvertising() {
        _showAdvertising.value = !_showAdvertising.value
        capture.includeAdvertising = _showAdvertising.value
        _snackbar.tryEmit(
            if (_showAdvertising.value) "Advertising packets enabled"
            else "Advertising packets hidden",
        )
    }

    private fun openCsvWriter() {
        closeCsvWriter()
        val capturesDir = File(android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS), "BTSnoopLog/CSV")
        val writer = CsvWriter(capturesDir)
        val file = writer.open(prefix = "live_ble_capture")
        csvWriter = writer
        _csvFile.value = file.name
        _csvRowCount.value = 0L
    }

    private fun closeCsvWriter() {
        csvWriter?.close()
        csvWriter = null
        // Clear the filename too — it drives the REC banner, which must not outlive the
        // open writer. Settings falls back to "will record on next capture" while enabled.
        _csvFile.value = null
    }

    // --- Snoop log maintenance ---

    fun resetSnoopLog() {
        _snackbar.tryEmit("Clearing snoop log (5-step reset)...")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Timber.i("Bluetooth: clearing snoop log via 5-step reset")
                if (_captureState.value is CaptureState.Capturing) stopCapture()
                clearPackets()

                _snackbar.tryEmit("Step 1/5: Ensuring BT on")
                RootShell.exec("svc bluetooth enable"); kotlinx.coroutines.delay(1000)

                _snackbar.tryEmit("Step 2/5: HCI logging off")
                RootShell.exec("setprop persist.bluetooth.btsnooplogmode disabled"); kotlinx.coroutines.delay(1000)

                _snackbar.tryEmit("Step 3/5: BT off → on")
                RootShell.exec("svc bluetooth disable"); kotlinx.coroutines.delay(2000)
                RootShell.exec("svc bluetooth enable"); kotlinx.coroutines.delay(2000)

                _snackbar.tryEmit("Step 4/5: HCI logging on")
                RootShell.exec("setprop persist.bluetooth.btsnooplogmode full"); kotlinx.coroutines.delay(1000)

                _snackbar.tryEmit("Step 5/5: BT off → on")
                RootShell.exec("svc bluetooth disable"); kotlinx.coroutines.delay(2000)
                RootShell.exec("svc bluetooth enable"); kotlinx.coroutines.delay(3000)

                Timber.i("Bluetooth: 5-step snoop log reset complete")
                refreshSnoopFileSize()
                _snackbar.tryEmit("Snoop log cleared")
            } catch (e: Exception) {
                Timber.e("Bluetooth: reset snoop log failed: %s", e.message)
                _snackbar.tryEmit("Reset failed: ${e.message}")
            }
        }
    }

    fun copyRawSnoopFile() {
        _snackbar.tryEmit("Copying raw btsnoop file...")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val downloadsDir = File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS), "BTSnoopLog")
                downloadsDir.mkdirs()
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val destFile = File(downloadsDir, "btsnoop_hci_$timestamp.log")
                RootShell.exec("cp /data/misc/bluetooth/logs/btsnoop_hci.log ${destFile.absolutePath} && chmod 644 ${destFile.absolutePath}")
                if (destFile.exists() && destFile.length() > 0) {
                    _lastExportFile.value = destFile
                    Timber.i("FileSystem: raw btsnoop copied to %s (%d bytes)", destFile.absolutePath, destFile.length())
                    _snackbar.tryEmit("Saved to ${destFile.absolutePath}")
                } else {
                    Timber.e("FileSystem: btsnoop copy failed — file empty or missing")
                    _snackbar.tryEmit("Copy failed — file empty or missing")
                }
            } catch (e: Exception) {
                Timber.e("FileSystem: btsnoop copy error: %s", e.message)
                _snackbar.tryEmit("Copy failed: ${e.message}")
            }
        }
    }

    fun openExportFolder() = openDocumentsFolder("primary:Download/BTSnoopLog")

    fun openCsvFolder() = openDocumentsFolder("primary:Download/BTSnoopLog/CSV")

    private fun openDocumentsFolder(docId: String) {
        val uri = android.provider.DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents", docId
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "vnd.android.document/directory")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            getApplication<Application>().startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            _snackbar.tryEmit("No file manager found")
        }
    }

    fun refreshSnoopFileSize() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = RootShell.exec("stat -c %s /data/misc/bluetooth/logs/btsnoop_hci.log")
                    .trim().toLongOrNull()
                _snoopFileSize.value = bytes?.let { formatFileSize(it) }
            } catch (e: Exception) {
                Timber.e("FileSystem: failed to read snoop file size: %s", e.message)
                _snoopFileSize.value = null
            }
        }
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.US, "%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }

    override fun onCleared() {
        super.onCleared()
        stopCapture()
        RootShell.close()
    }
}

data class DeviceInfo(
    val address: String,
    val protocolLabel: String? = null,
    val name: String? = null,
) {
    /**
     * Human-friendly label: the advertised name up front (when known), with the MAC
     * and any detected protocol in parentheses. Falls back to MAC (+protocol).
     */
    val displayName: String
        get() = when {
            name != null -> {
                val extra = listOfNotNull(address, protocolLabel).joinToString(" · ")
                "$name ($extra)"
            }
            protocolLabel != null -> "$address ($protocolLabel)"
            else -> address
        }
}
