package com.jc84.blesnooplogger.data.util

import android.util.Base64
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Persistent interactive root shell. One `su` process is kept alive and reused
 * for all root commands, so Magisk only prompts/toasts once per session.
 */
object RootShell {

    private val lock = ReentrantLock()
    private var process: Process? = null
    private var stdin: OutputStream? = null
    private var stdoutReader: java.io.BufferedReader? = null

    private fun ensureShell(): Triple<Process, OutputStream, java.io.BufferedReader> {
        val p = process
        val r = stdoutReader
        if (p != null && p.isAlive && r != null) return Triple(p, stdin!!, r)

        Timber.i("RootShell: opening persistent su shell")
        val newProc = Runtime.getRuntime().exec("su")
        process = newProc
        stdin = newProc.outputStream
        val reader = newProc.inputStream.bufferedReader()
        stdoutReader = reader
        return Triple(newProc, newProc.outputStream, reader)
    }

    private fun destroy() {
        process?.destroy()
        process = null
        stdin = null
        stdoutReader = null
    }

    /**
     * Run a command and return its stdout as a string.
     */
    fun exec(command: String): String = lock.withLock {
        var lastException: Exception? = null
        for (attempt in 0..1) {
            try {
                val (_, out, reader) = ensureShell()
                val marker = "ROOT_SHELL_EOF_${System.nanoTime()}"
                out.write("$command\necho $marker\n".toByteArray())
                out.flush()

                val result = StringBuilder()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line == marker) break
                    result.appendLine(line)
                }
                return result.toString().trimEnd('\n')
            } catch (e: Exception) {
                Timber.w("RootShell: exec failed (attempt ${attempt + 1}): %s", e.message)
                lastException = e
                destroy()
            }
        }
        throw lastException!!
    }

    /**
     * Run a command and return its stdout as raw bytes.
     * Pipes through base64 to safely transport binary data over the text shell.
     */
    fun execBytes(command: String): ByteArray {
        val encoded = exec("$command 2>/dev/null | base64")
        if (encoded.isBlank()) return ByteArray(0)
        return Base64.decode(encoded, Base64.DEFAULT)
    }

    /**
     * Start a long-running root process (e.g. tail -f). Gets its own su process
     * but reuses the existing Magisk grant (no extra prompt/toast).
     */
    fun execProcess(command: String): Process {
        lock.withLock { ensureShell() }
        return Runtime.getRuntime().exec(arrayOf("su", "-c", command))
    }

    fun close() = lock.withLock {
        Timber.i("RootShell: closing persistent shell")
        destroy()
    }
}
