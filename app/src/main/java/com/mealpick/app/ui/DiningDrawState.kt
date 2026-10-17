package com.mealpick.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

internal const val CARD_DINING_GROUP_ID = "dining-01"

internal enum class DrawPhase { Browse, Shuffling, Choosing, Flipping, Revealed }

/** Only a round's identity/order lives here. A draw never writes food records. */
internal class DiningDrawState {
    var phase by mutableStateOf(DrawPhase.Browse)
        private set
    var deck: List<String> by mutableStateOf(emptyList())
        private set
    var selectedId: String? by mutableStateOf(null)
        private set
    val active: Boolean get() = phase != DrawPhase.Browse

    fun start(ids: List<String>, random: Random = Random.Default) {
        if (phase != DrawPhase.Browse && phase != DrawPhase.Revealed) return
        if (ids.isEmpty()) return
        require(ids.size == ids.toSet().size)
        deck = ids.shuffled(random)
        selectedId = null
        phase = DrawPhase.Shuffling
    }

    fun deal() {
        if (phase == DrawPhase.Shuffling) phase = DrawPhase.Choosing
    }

    fun choose(index: Int): Boolean {
        if (phase != DrawPhase.Choosing || index !in deck.indices) return false
        selectedId = deck[index]
        phase = DrawPhase.Flipping
        return true
    }

    fun reveal() {
        if (phase == DrawPhase.Flipping) phase = DrawPhase.Revealed
    }

    fun reset() {
        phase = DrawPhase.Browse
        deck = emptyList()
        selectedId = null
    }

    fun reconcile(ids: List<String>) {
        if (active && ids.toSet() != deck.toSet()) reset()
    }
}
