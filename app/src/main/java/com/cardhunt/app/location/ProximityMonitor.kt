package com.cardhunt.app.location

import android.location.Location
import com.cardhunt.app.core.NetworkResult
import com.cardhunt.app.data.model.Card
import com.cardhunt.app.data.repository.CardRepository
import com.cardhunt.app.data.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class ProximityMonitor @Inject constructor(
    private val cardRepo: CardRepository,
    private val notificationRepo: NotificationRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // In-memory cooldown: cardId -> when we last attempted to notify
    private val notifiedAt = mutableMapOf<Int, Long>()

    private val cooldownMillis = 30 * 60 * 1000L   // don't re-notify same card within 30 min
    private val minNotifyRadiusMeters = 100f       // notify within at least this distance

    private val recheckDistanceMeters = 100.0      // re-query if moved more than this...
    private val recheckIntervalMillis = 30_000L    // ...or every 30s regardless

    private var lastCheckCenter: Location? = null
    private var lastCheckTime = 0L

    fun check(loc: Location) {
        val now = System.currentTimeMillis()
        var shouldRecheck = lastCheckCenter == null || (now - lastCheckTime > recheckIntervalMillis)
        if (!shouldRecheck) {
            val last = lastCheckCenter
            if (last != null) {
                val out = FloatArray(1)
                Location.distanceBetween(last.latitude, last.longitude, loc.latitude, loc.longitude, out)
                if (out[0] > recheckDistanceMeters) shouldRecheck = true
            }
        }
        if (!shouldRecheck) return

        lastCheckCenter = loc
        lastCheckTime = now

        val delta = 0.01  // ~1.1 km box around the user
        scope.launch {
            val result = cardRepo.getCards(
                loc.latitude - delta, loc.latitude + delta,
                loc.longitude - delta, loc.longitude + delta
            )
            if (result is NetworkResult.Success) {
                processCards(loc, cardRepo.mergeLocalState(result.data))
            }
        }
    }

    private fun processCards(loc: Location, cards: List<Card>) {
        val now = System.currentTimeMillis()

        // Fetch existing notifications once (backend also deduplicates, but this avoids unnecessary calls)
        val existingNotifications = runCatching {
            val result = kotlinx.coroutines.runBlocking { notificationRepo.fetch() }
            if (result is NetworkResult.Success) {
                result.data
                    .filter { it.type == "CARD_NEARBY" && !it.isRead }
                    .mapNotNull { it.payload?.get("card_id")?.toString()?.toIntOrNull() }
                    .toSet()
            } else {
                emptySet<Int>()
            }
        }.getOrDefault(emptySet())

        for (card in cards) {
            if (card.collected) continue

            // Skip if we already have an unread notification for this card
            if (card.id in existingNotifications) continue

            val out = FloatArray(1)
            Location.distanceBetween(loc.latitude, loc.longitude, card.lat, card.lng, out)
            val radius = max(card.radiusMeters.toFloat(), minNotifyRadiusMeters)
            if (out[0] <= radius) {
                val last = notifiedAt[card.id]
                if (last == null || now - last > cooldownMillis) {
                    notifiedAt[card.id] = now
                    scope.launch { notificationRepo.reportCardNearby(card.id) }
                }
            }
        }
    }
}