package org.cortex.terminal.runtime

import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/** One long Debian operation at a time for the whole process, so rotation cannot start a second one. */
internal object DebianOperationRunner {
    sealed class State {
        object Idle : State()
        object Running : State()
        class Finished(val success: Boolean, val message: String?, val closeSettings: Boolean) : State()
    }

    private val lock = Any()
    private var state: State = State.Idle
    private val listeners = CopyOnWriteArrayList<(State) -> Unit>()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Miauterm-Debian-Operation").apply { isDaemon = true }
    }

    val current: State
        get() = synchronized(lock) { state }

    fun addListener(listener: (State) -> Unit) {
        listeners += listener
        listener(current)
    }

    fun removeListener(listener: (State) -> Unit) {
        listeners -= listener
    }

    /** Starts [operation] unless one is running; true when it was started. */
    fun start(
        context: Context,
        successMessage: String,
        closeSettings: Boolean,
        operation: (Context) -> Unit
    ): Boolean {
        val app = context.applicationContext
        synchronized(lock) {
            if (state == State.Running) return false
            state = State.Running
        }
        deliver(State.Running)
        try {
            executor.execute {
                val result = runCatching { operation(app) }
                val finished = result.fold(
                    onSuccess = { State.Finished(true, successMessage, closeSettings) },
                    onFailure = { error ->
                        State.Finished(
                            false,
                            error.message ?: error.javaClass.simpleName,
                            closeSettings
                        )
                    }
                )
                synchronized(lock) { state = finished }
                deliver(finished)
                synchronized(lock) { state = State.Idle }
            }
        } catch (_: RejectedExecutionException) {
            synchronized(lock) { state = State.Idle }
            return false
        }
        return true
    }

    private fun deliver(next: State) {
        for (listener in listeners) {
            try {
                listener(next)
            } catch (_: Exception) {
                // A screen that went away must not stop the operation or the other listeners.
            }
        }
    }
}
