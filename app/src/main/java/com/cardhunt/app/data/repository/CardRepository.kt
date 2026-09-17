package com.cardhunt.app.data.repository

import com.cardhunt.app.core.NetworkResult
import com.cardhunt.app.core.safeApiCall
import com.cardhunt.app.cloudinary.CloudinaryUploader
import com.cardhunt.app.data.local.InventoryCache
import com.cardhunt.app.data.model.Card
import com.cardhunt.app.data.model.toDomain
import com.cardhunt.app.data.remote.CardHuntApi
import com.cardhunt.app.data.remote.dto.CollectRequest
import com.cardhunt.app.data.remote.dto.CollectResponse
import com.cardhunt.app.data.remote.dto.CreateCardRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class CollectionEvent(val cardId: Int, val byPhoto: Boolean)

@Singleton
class CardRepository @Inject constructor(
    private val api: CardHuntApi,
    private val uploader: CloudinaryUploader,
    private val cache: InventoryCache,
) {
    private val _collected = MutableSharedFlow<CollectionEvent>(extraBufferCapacity = 8)
    val collectedEvents: SharedFlow<CollectionEvent> = _collected

    suspend fun getCards(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double) =
        safeApiCall { api.cards(minLat, maxLat, minLng, maxLng) }
            .let { r -> if (r is NetworkResult.Success)
                NetworkResult.Success(r.data.map { it.toDomain() }) else r as NetworkResult.Error }

    // Trust server data completely - don't merge with local cache for map data
    fun mergeLocalState(cards: List<Card>): List<Card> = cards

    suspend fun createCard(name: String, description: String?, lat: Double, lng: Double,
                           radius: Int, rarity: String, points: Int) =
        safeApiCall { api.createCard(CreateCardRequest(name, description, lat, lng, radius, rarity, points)) }

    fun emitCollectionEvent(cardId: Int, byPhoto: Boolean) {
        _collected.tryEmit(CollectionEvent(cardId, byPhoto))
    }

    suspend fun collectWithPhoto(cardId: Int, photo: File, lat: Double, lng: Double,
                                 rarity: String): NetworkResult<CollectResponse> {
        cache.markCollected(cardId, byPhoto = true)
        val upload = try { uploader.uploadAndStylize(photo, rarity) }
        catch (e: Exception) {
            cache.rollback(cardId)
            return NetworkResult.Error(null, "Photo upload failed: ${e.message}")
        }
        val result = safeApiCall {
            api.collect(CollectRequest(cardId, upload.secureUrl, upload.publicId, lat, lng))
        }
        when (result) {
            is NetworkResult.Success -> {
                cache.confirm(cardId)
                _collected.tryEmit(CollectionEvent(cardId = cardId, byPhoto = true))
            }
            is NetworkResult.Error -> cache.rollback(cardId)
        }
        return result
    }
}