package com.example.expression.runtime

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean

class ThreadLoop(name: String, threadFactory: ThreadFactory = ThreadFactory { runnable ->
    Thread(runnable, name).apply { isDaemon = true }
}) {
    private val running = AtomicBoolean(true)
    private val tasks = LinkedBlockingQueue<() -> Unit>()
    private val worker: Thread

    init {
        worker = threadFactory.newThread {
            while (running.get()) {
                val task = tasks.take()
                try {
                    task.invoke()
                } catch (ex: InterruptedException) {
                    Thread.currentThread().interrupt()
                    running.set(false)
                } catch (ex: Throwable) {
                    // swallow to keep loop alive
                }
            }
        }
        worker.start()
    }

    fun schedule(task: () -> Unit) {
        if (running.get()) {
            tasks.offer(task)
        }
    }

    fun shutdown() {
        running.set(false)
        worker.interrupt()
    }
}

class WorkerPool(size: Int = Runtime.getRuntime().availableProcessors()) {
    private val loops: List<ThreadLoop> = (0 until size).map { ThreadLoop("worker-$it") }
    private val index = java.util.concurrent.atomic.AtomicInteger(0)

    fun execute(task: () -> Unit) {
        val loop = loops[Math.abs(index.getAndIncrement()) % loops.size]
        loop.schedule(task)
    }

    fun shutdown() = loops.forEach { it.shutdown() }

    companion object {
        val default: WorkerPool by lazy { WorkerPool() }
    }
}
