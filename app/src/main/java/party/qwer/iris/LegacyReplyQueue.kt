package party.qwer.iris

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.Channel
import party.qwer.iris.features.reply.application.ReplyQueueFullException

/** Admission is synchronous: callers cannot accumulate suspended producer coroutines. */
internal class LegacyReplyQueue<T>(capacity: Int = 4) {
    val channel = Channel<T>(capacity)
    private val accepted = AtomicLong()
    private val completed = AtomicLong()
    val pendingCount: Long get() = (accepted.get() - completed.get()).coerceAtLeast(0)

    fun enqueue(request: T) {
        if (channel.trySend(request).isFailure) {
            throw ReplyQueueFullException("legacy reply queue is full")
        }
        accepted.incrementAndGet()
    }

    fun complete() { completed.incrementAndGet() }
}
