package com.cardhunt.app.data.remote

import com.cardhunt.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface CardHuntApi {

    // Auth
    @POST("auth/register") suspend fun register(@Body b: RegisterRequest): Response<AuthResponse>
    @POST("auth/login")    suspend fun login(@Body b: LoginRequest): Response<AuthResponse>

    // Profile & Inventory
    @GET("me")          suspend fun me(): Response<ProfileDto>
    @GET("me/cards")    suspend fun myCards(): Response<List<InventoryCardDto>>
    @DELETE("me/cards/{cardId}")
    suspend fun deleteMyCard(@Path("cardId") cardId: Int): Response<OkDto>
    @PATCH("me/avatar") suspend fun updateAvatar(@Body b: AvatarUpdateRequest): Response<OkDto>

    // Cards & Collection
    @GET("cards")
    suspend fun cards(@Query("min_lat") minLat: Double, @Query("max_lat") maxLat: Double,
                      @Query("min_lng") minLng: Double, @Query("max_lng") maxLng: Double
    ): Response<List<CardDto>>

    @GET("cards/{id}")  suspend fun card(@Path("id") id: Int): Response<CardDto>
    @GET("cards/{cardId}/available-friends")
    suspend fun getAvailableFriends(@Path("cardId") cardId: Int): Response<List<AvailableFriendDto>>
    @POST("cards")      suspend fun createCard(@Body b: CreateCardRequest): Response<CreateCardResponse>
    @POST("collect")    suspend fun collect(@Body b: CollectRequest): Response<CollectResponse>

    // Shares
    @POST("shares")                    suspend fun createShare(@Body b: ShareRequest): Response<ShareResponse>
    @POST("shares/{token}/redeem")     suspend fun redeemShare(@Path("token") token: String): Response<RedeemResponse>

    // Friends & Players
    @GET("friends")                                suspend fun friends(): Response<FriendsResponse>
    @GET("users")                                  suspend fun getPlayers(): Response<List<PlayerDto>>
    @GET("users/search")                           suspend fun searchUsers(@Query("q") q: String): Response<List<UserSearchDto>>
    @DELETE("friends/{userId}")
    suspend fun removeFriend(@Path("userId") userId: Int): Response<OkDto>
    @POST("friends/{userId}/request")              suspend fun friendRequest(@Path("userId") userId: Int): Response<RequestResponse>
    @PATCH("friends/requests/{requestId}/accept")  suspend fun acceptRequest(@Path("requestId") requestId: Int): Response<AcceptResponse>
    @PATCH("friends/requests/{requestId}/decline") suspend fun declineRequest(@Path("requestId") requestId: Int): Response<OkDto>
    @DELETE("friends/requests/{requestId}/cancel")
    suspend fun cancelFriendRequest(@Path("requestId") requestId: Int): Response<OkDto>

    // Notifications
    @GET("notifications")            suspend fun notifications(): Response<List<NotificationDto>>
    @PATCH("notifications/read")     suspend fun markNotificationsRead(): Response<OkDto>
    @POST("notifications/card-nearby") suspend fun reportCardNearby(@Body b: CardNearbyRequest): Response<OkDto>
    @DELETE("notifications/{id}")    suspend fun deleteNotification(@Path("id") id: Int): Response<OkDto>
    @DELETE("notifications")         suspend fun clearAllNotifications(): Response<OkDto>

    // Staff Dashboard (paths updated to match staff_bp)
    @GET("staff/users")                              suspend fun getAllUsers(): Response<List<AdminUserDto>>
    @PATCH("staff/users/{id}/status")                suspend fun updateUserStatus(@Path("id") id: Int, @Body b: UpdateUserRequest): Response<OkDto>
    @PATCH("staff/users/{id}/role")                  suspend fun updateUserRole(@Path("id") id: Int, @Body b: UpdateUserRequest): Response<OkDto>
    @GET("staff/cards")                              suspend fun getAllCards(): Response<List<AdminCardDto>>
    @DELETE("staff/cards/{id}")                      suspend fun deleteCard(@Path("id") id: Int): Response<OkDto>

    @POST("fcm/register")
    suspend fun registerFcmToken(@Body body: FcmTokenRequest): Response<OkDto>
}