package com.cardhunt.app.data.model

import com.cardhunt.app.data.remote.dto.CardDto

data class Card(
    val id: Int, val name: String, val description: String?,
    val lat: Double, val lng: Double, val radiusMeters: Int,
    val rarity: String, val points: Int, val referenceImageUrl: String?,
    val collected: Boolean, val collectedByPhoto: Boolean,
    val photoUrl: String?, val pendingSync: Boolean = false,
)

fun CardDto.toDomain() = Card(
    id, name, description, latitude, longitude, radiusMeters, rarity, points,
    referenceImageUrl, collected, collectedByPhoto, photoUrl)