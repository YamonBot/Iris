package party.qwer.iris

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import party.qwer.iris.features.reply.application.ReplyQueueFullException

class LegacyReplyQueueTest {
    @Test fun overloadRejectsSynchronouslyAndDoesNotLeakWorkOrPendingCount() = runBlocking {
        val queue = LegacyReplyQueue<String>(2)
        queue.enqueue("first")
        queue.enqueue("second")
        repeat(1000) {
            try {
                queue.enqueue("rejected")
                fail("Full queue admitted work")
            } catch (_: ReplyQueueFullException) { }
        }
        assertEquals(2L, queue.pendingCount)
        assertEquals("first", queue.channel.receive())
        queue.complete() // The consumer must complete failed sends too.
        queue.enqueue("third")
        assertEquals("second", queue.channel.receive())
        queue.complete()
        assertEquals("third", queue.channel.receive())
        queue.complete()
        assertEquals(0L, queue.pendingCount)
        assertTrue(queue.channel.tryReceive().isFailure)
    }
}
