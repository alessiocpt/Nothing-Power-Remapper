package io.github.alessiocpt.powerremapper.service

import android.os.Handler
import io.github.alessiocpt.powerremapper.core.Cancellable
import io.github.alessiocpt.powerremapper.core.GestureScheduler

/**
 * Production [GestureScheduler] that posts delayed work to an Android [Handler] (the service's main
 * looper). Mirrors the deterministic test scheduler's contract: cancelling before the delay elapses
 * removes the pending callback; cancelling afterwards is a harmless no-op.
 */
class HandlerGestureScheduler(
    private val handler: Handler
) : GestureScheduler {

    override fun schedule(delayMs: Long, action: () -> Unit): Cancellable {
        val runnable = Runnable { action() }
        handler.postDelayed(runnable, delayMs)
        return object : Cancellable {
            override fun cancel() {
                handler.removeCallbacks(runnable)
            }
        }
    }
}
