package dev.citytexi.simulcast.process

import kotlin.test.Test
import kotlin.test.assertEquals

class DropCountingSinkTest {

    @Test
    fun reports_dropped_count_once_the_channel_accepts_again() {
        val accepted = mutableListOf<CommandEvent>()
        var open = false
        val sink = DropCountingSink { event ->
            if (open) accepted += event
            open
        }

        sink.offer(CommandEvent.Stdout("a"))
        sink.offer(CommandEvent.Stdout("b"))
        open = true
        sink.offer(CommandEvent.Stdout("c"))

        assertEquals(
            listOf(CommandEvent.Dropped(2), CommandEvent.Stdout("c")),
            accepted,
        )
    }

    @Test
    fun stays_quiet_when_nothing_is_dropped() {
        val accepted = mutableListOf<CommandEvent>()
        val sink = DropCountingSink { accepted += it; true }

        sink.offer(CommandEvent.Stdout("a"))

        assertEquals(listOf<CommandEvent>(CommandEvent.Stdout("a")), accepted)
    }

    @Test
    fun keeps_the_count_when_the_dropped_report_itself_fails() {
        val accepted = mutableListOf<CommandEvent>()
        val results = ArrayDeque(listOf(false, false, false, true, true))
        val sink = DropCountingSink { event ->
            val ok = results.removeFirst()
            if (ok) accepted += event
            ok
        }

        sink.offer(CommandEvent.Stdout("a"))
        sink.offer(CommandEvent.Stdout("b"))
        sink.offer(CommandEvent.Stdout("c"))

        assertEquals(
            listOf(CommandEvent.Dropped(2), CommandEvent.Stdout("c")),
            accepted,
        )
    }

    @Test
    fun pending_count_claims_the_accumulated_drops_and_resets_to_zero() {
        val sink = DropCountingSink { false }

        sink.offer(CommandEvent.Stdout("a"))
        sink.offer(CommandEvent.Stdout("b"))

        assertEquals(2, sink.pendingCount())
        assertEquals(0, sink.pendingCount())
    }
}
