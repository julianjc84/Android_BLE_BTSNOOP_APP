package com.jc84.blesnooplogger.data.capture

sealed class CaptureState {
    data object Idle : CaptureState()
    data object Capturing : CaptureState()
    data object Paused : CaptureState()
    data object Stopped : CaptureState()
    data class Error(val message: String) : CaptureState()
}
