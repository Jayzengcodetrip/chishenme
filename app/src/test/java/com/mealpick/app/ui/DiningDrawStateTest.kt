package com.mealpick.app.ui

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class DiningDrawStateTest {
    @Test fun eachRoundContainsEachIdentityExactlyOnceWithoutMutatingTheSource() {
        val ids = listOf("same-name-a", "same-name-b", "third", "fourth")
        val original = ids.toList()
        val seen = mutableSetOf<List<String>>()
        repeat(50) { seed ->
            val draw = DiningDrawState()
            draw.start(ids, Random(seed))
            assertEquals(ids.toSet(), draw.deck.toSet())
            assertEquals(ids.size, draw.deck.size)
            seen += draw.deck
        }
        assertTrue("The cards are not always dealt in a fixed order", seen.size > 1)
        assertEquals(original, ids)
    }

    @Test fun theFirstSelectionWinsAndAnimationEventsCannotRestartACancelledRound() {
        val draw = DiningDrawState()
        draw.start(listOf("a", "b"), Random(1))
        assertFalse(draw.choose(0))
        draw.deal()
        assertFalse(draw.choose(-1))
        assertTrue(draw.choose(0))
        val chosen = draw.selectedId
        assertFalse(draw.choose(1))
        draw.start(listOf("c"))
        assertEquals(chosen, draw.selectedId)
        draw.reset()
        draw.deal()
        draw.reveal()
        assertEquals(DrawPhase.Browse, draw.phase)
        assertNull(draw.selectedId)
        assertTrue(draw.deck.isEmpty())
    }

    @Test fun emptyAndSingleCollectionsAndRepeatedDrawsRemainUsable() {
        val draw = DiningDrawState()
        draw.start(emptyList())
        assertEquals(DrawPhase.Browse, draw.phase)
        repeat(3) {
            draw.start(listOf("one"))
            draw.deal()
            assertTrue(draw.choose(0))
            draw.reveal()
            assertEquals("one", draw.selectedId)
            assertEquals(DrawPhase.Revealed, draw.phase)
        }
    }

    @Test fun removingOrAddingAnIdentityInvalidatesTheRoundButReorderingDoesNot() {
        val draw = DiningDrawState()
        draw.start(listOf("a", "b"))
        draw.deal()
        draw.reconcile(listOf("b", "a"))
        assertEquals(DrawPhase.Choosing, draw.phase)
        draw.reconcile(listOf("a"))
        assertEquals(DrawPhase.Browse, draw.phase)
        draw.start(listOf("a"))
        draw.reconcile(listOf("a", "new"))
        assertEquals(DrawPhase.Browse, draw.phase)
    }
}
