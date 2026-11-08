package io.ogwars.cloud.node.core.sdk

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Runs tasks one at a time and in order on [executor], so listeners see messages in the order they came. */
internal class SerialExecutor(
    private val executor: Executor,
) : Executor {
    private val tasks = ConcurrentLinkedQueue<Runnable>()
    private val draining = AtomicBoolean()

    override fun execute(task: Runnable) {
        tasks += task
        drainSoon()
    }

    private fun drainSoon() {
        if (draining.compareAndSet(false, true)) executor.execute(::drain)
    }

    private fun drain() {
        try {
            while (true) (tasks.poll() ?: break).run()
        } finally {
            draining.set(false)
            // A task queued while the last one ran would otherwise wait for the next execute.
            if (tasks.isNotEmpty()) drainSoon()
        }
    }
}
