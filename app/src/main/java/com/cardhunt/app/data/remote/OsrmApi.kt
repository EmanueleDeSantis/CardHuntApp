package com.cardhunt.app.data.remote

import com.cardhunt.app.data.remote.dto.OsrmResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OsrmApi {
    @GET("route/v1/foot/{coordinates}")
    suspend fun getRoute(
        @Path("coordinates", encoded = true) coordinates: String, // "lon1,lat1;lon2,lat2"
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "geojson"
    ): Response<OsrmResponse>
}