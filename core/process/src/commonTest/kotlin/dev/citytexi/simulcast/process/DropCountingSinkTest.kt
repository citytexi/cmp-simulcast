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
}
