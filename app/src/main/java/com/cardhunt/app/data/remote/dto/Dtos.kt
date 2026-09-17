package com.cardhunt.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ---------- auth ----------
@Serializable data class RegisterRequest(val username: String, val email: String, val password: String)
@Serializable data class LoginRequest(val username: String, val password: String)
@Serializable data class UserDto(val id: Int, val username: String, val role: String)
@Serializable data class AuthResponse(val token: String, val user: UserDto)
@Serializable data class PlayerDto(
    val id: Int, val username: String, val role: String, val xp: Int,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("cards_count") val cardsCount: Int = 0,
    @SerialName("request_id") val requestId: Int? = null,
    @SerialName("friend_state") val friendState: String = "NONE")
@Serializable data class AdminUserDto(
    val id: Int, val username: String, val email: String,
    val role: String, val status: String, val xp: Int,
    @SerialName("cards_count") val cardsCount: Int = 0)
@Serializable data class UpdateUserRequest(val status: String? = null, val role: String? = null)
@Serializable data class AdminCardDto(
    val id: Int, val name: String, val description: String? = null,
    val latitude: Double, val longitude: Double,
    @SerialName("radius_meters") val radiusMeters: Int,
    val rarity: String, val points: Int,
    @SerialName("created_at") val createdAt: String? = null,
    val collections: Int = 0,
    @SerialName("photo_collections") val photoCollections: Int = 0)
// OSRM Routing DTOs
@Serializable data class OsrmResponse(val routes: List<OsrmRoute>)
@Serializable data class OsrmRoute(val geometry: OsrmGeometry)
@Serializable data class OsrmGeometry(val coordinates: List<List<Double>>)
// ---------- cards ----------
@Serializable data class CardDto(
    val id: Int, val name: String, val description: String? = null,
    val latitude: Double, val longitude: Double,
    @SerialName("radius_meters") val radiusMeters: Int,
    val rarity: String, val points: Int,
    @SerialName("reference_image_url") val referenceImageUrl: String? = null,
    val collected: Boolean = false,
    @SerialName("collected_by_photo") val collectedByPhoto: Boolean = false,
    @SerialName("photo_url") val photoUrl: String? = null,
)

@Serializable data class CreateCardRequest(
    val name: String, val description: String? = null,
    val latitude: Double, val longitude: Double,
    @SerialName("radius_meters") val radiusMeters: Int = 50,
    val rarity: String = "COMMON", val points: Int = 10)

@Serializable data class CreateCardResponse(val id: Int)

@Serializable data class CollectRequest(
    @SerialName("card_id") val cardId: Int,
    @SerialName("photo_url") val photoUrl: String,
    @SerialName("public_id") val publicId: String,
    val lat: Double, val lng: Double)

@Serializable data class AchievementStub(val code: String, val name: String)

@Serializable data class CollectResponse(
    @SerialName("card_id") val cardId: Int,
    @SerialName("xp_earned") val xpEarned: Int,
    @SerialName("total_xp") val totalXp: Int,
    @SerialName("new_achievements") val newAchievements: List<AchievementStub> = emptyList())

@Serializable data class InventoryCardDto(
    val id: Int, val name: String, val rarity: String, val points: Int,
    val acquisition: String,
    @SerialName("has_original_shoot") val hasOriginalShoot: Boolean,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("collected_at") val collectedAt: String? = null)

// ---------- shares ----------
@Serializable data class ShareRequest(
    @SerialName("card_id") val cardId: Int,
    @SerialName("friend_id") val friendId: Int)
@Serializable data class ShareResponse(@SerialName("share_token") val shareToken: String)
@Serializable data class RedeemResponse(
    @SerialName("card_id") val cardId: Int,
    @SerialName("new_achievements") val newAchievements: List<AchievementStub> = emptyList())

// ---------- friends ----------
@Serializable data class FriendDto(
    val id: Int, val username: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val xp: Int = 0,
    @SerialName("cards_count") val cardsCount: Int = 0)
@Serializable data class FriendRequestDto(
    @SerialName("request_id") val requestId: Int, val from: FriendDto)
@Serializable data class FriendsResponse(
    val friends: List<FriendDto>, val incoming: List<FriendRequestDto>,
    val outgoing: List<Int>)
@Serializable data class AvailableFriendDto(
    val id: Int, val username: String,
    @SerialName("avatar_url") val avatarUrl: String? = null)
@Serializable data class UserSearchDto(
    val id: Int, val username: String,
    @SerialName("avatar_url") val avatarUrl: String? = null
)
@Serializable data class RequestResponse(@SerialName("request_id") val requestId: Int)
@Serializable data class AcceptResponse(
    @SerialName("new_achievements") val newAchievements: List<AchievementStub> = emptyList())

// ---------- profile ----------
@Serializable data class StatsDto(
    @SerialName("total_cards") val totalCards: Int,
    @SerialName("photo_cards") val photoCards: Int,
    @SerialName("shared_cards") val sharedCards: Int,
    @SerialName("friends_count") val friendsCount: Int)
@Serializable data class AchievementDto(
    val code: String, val name: String, val description: String,
    val icon: String? = null, val unlocked: Boolean = false)
@Serializable data class ProfileDto(
    val id: Int, val username: String, val email: String, val role: String,
    val xp: Int, @SerialName("avatar_url") val avatarUrl: String? = null,
    val stats: StatsDto, val achievements: List<AchievementDto>)
@Serializable data class AvatarUpdateRequest(@SerialName("avatar_url") val avatarUrl: String)
// ---------- notifications ----------
@Serializable data class NotificationDto(
    val id: Int, val type: String, val message: String,
    val payload: JsonObject? = null,
    @SerialName("is_read") val isRead: Boolean,
    @SerialName("created_at") val createdAt: String? = null)
@Serializable data class FcmTokenRequest(val token: String)
@Serializable data class CardNearbyRequest(@SerialName("card_id") val cardId: Int)
@Serializable data class OkDto(val ok: Boolean = true)