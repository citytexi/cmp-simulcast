package dev.citytexi.simulcast.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OutcomeTest {

    @Test
    fun map_transforms_only_the_success_side() {
        val ok: Outcome<Int, String> = Outcome.Ok(2)
        val err: Outcome<Int, String> = Outcome.Err("boom")

        assertEquals(Outcome.Ok(4), ok.map { it * 2 })
        assertEquals(Outcome.Err("boom"), err.map { it * 2 })
    }

    @Test
    fun value_or_null_returns_null_on_failure() {
        val err: Outcome<Int, String> = Outcome.Err("boom")

        assertEquals(2, Outcome.Ok(2).valueOrNull())
        assertNull(err.valueOrNull())
    }
}
