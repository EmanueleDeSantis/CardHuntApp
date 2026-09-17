package com.cardhunt.app.data.local

import com.cardhunt.app.data.model.Card
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryCache @Inject constructor() {

    data class Override(val collected: Boolean, val byPhoto: Boolean, val pending: Boolean)

    private val overrides = mutableMapOf<Int, Override>()

    fun markCollected(cardId: Int, byPhoto: Boolean) = synchronized(overrides) {
        overrides[cardId] = Override(collected = true, byPhoto = byPhoto, pending = true)
    }

    fun confirm(cardId: Int) = synchronized(overrides) {
        overrides[cardId]?.let { overrides[cardId] = it.copy(pending = false) }
    }

    fun rollback(cardId: Int) = synchronized(overrides) { overrides.remove(cardId) }

    fun merge(cards: List<Card>): List<Card> = synchronized(overrides) {
        cards.map { c ->
            overrides[c.id]?.let { o ->
                c.copy(collected = o.collected, collectedByPhoto = o.byPhoto, pendingSync = o.pending)
            } ?: c
        }
    }
}