package com.rr.numio.files.data

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicBoolean

/** Thrown inside a job when the person taps Cancel. */
class CancelledJob : Exception("Cancelled")

/**
 * How far a long job (copy, move, extract, make ZIP) has got, for the progress
 * card. Updated from the worker thread; the screen redraws a few times a second.
 */
class JobProgress(val title: String) {
    var total by mutableLongStateOf(0L)
    var done by mutableLongStateOf(0L)
    var current by mutableStateOf("")

    private val cancelled = AtomicBoolean(false)
    private var doneRaw = 0L
    private var lastShown = 0L

    val isCancelled: Boolean get() = cancelled.get()
    fun cancel() = cancelled.set(true)

    /** Call between chunks; stops the job if Cancel was tapped. */
    fun check() {
        if (cancelled.get()) throw CancelledJob()
    }

    fun start(name: String) {
        check()
        current = name
    }

    fun add(bytes: Long) {
        doneRaw += bytes
        val now = SystemClock.uptimeMillis()
        if (now - lastShown > 100) {
            lastShown = now
            done = doneRaw
        }
    }

    fun finish() {
        done = doneRaw
    }
}
