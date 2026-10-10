package com.jc84.blesnooplogger.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jc84.blesnooplogger.data.capture.CaptureState

@Composable
fun CaptureFabStack(
    captureState: CaptureState,
    showScrollToBottom: Boolean,
    newPacketsBelow: Long,
    pauseBufferedCount: Long,
    onClear: () -> Unit,
    onScrollToBottom: () -> Unit,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    onSave: () -> Unit,
    onStart: () -> Unit,
) {
    val isCapturing = captureState is CaptureState.Capturing
    val isPaused = captureState is CaptureState.Paused
    val isStopped = captureState is CaptureState.Stopped
    val isActive = isCapturing || isPaused

    Column(horizontalAlignment = Alignment.End) {
        SmallFloatingActionButton(
            onClick = onClear,
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Clear packets",
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Spacer(Modifier.height(12.dp))

        if (showScrollToBottom) {
            ExtendedFloatingActionButton(
                onClick = onScrollToBottom,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                icon = {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                },
                text = {
                    Text(
                        text = if (newPacketsBelow == 1L) "1 new packet"
                        else "$newPacketsBelow new packets",
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
        }

        // While paused, surface how many packets are queued in the buffer behind the
        // frozen display — tapping resumes (same effect as the pause/resume FAB).
        if (isPaused && pauseBufferedCount > 0) {
            ExtendedFloatingActionButton(
                onClick = onPauseResume,
                containerColor = Color(0xFFFFA726),
                contentColor = Color.White,
                icon = {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                },
                text = {
                    Text(
                        text = if (pauseBufferedCount == 1L) "1 buffered"
                        else "$pauseBufferedCount buffered",
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
        }

        when {
            isActive -> Row {
                FloatingActionButton(
                    onClick = onPauseResume,
                    containerColor = if (isPaused) Color(0xFF4CAF50) else Color(0xFFFFA726),
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "Resume" else "Pause",
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.width(12.dp))
                FloatingActionButton(
                    onClick = onStop,
                    containerColor = MaterialTheme.colorScheme.error,
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                }
            }
            isStopped -> Row {
                FloatingActionButton(
                    onClick = onSave,
                    containerColor = Color(0xFF42A5F5),
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save to CSV", tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                FloatingActionButton(
                    onClick = onStart,
                    containerColor = Color(0xFF4CAF50),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start new capture", tint = Color.White)
                }
            }
            else -> FloatingActionButton(
                onClick = onStart,
                containerColor = Color(0xFF4CAF50),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = Color.White)
            }
        }
    }
}
