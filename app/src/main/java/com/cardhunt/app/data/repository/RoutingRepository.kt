package com.cardhunt.app.data.repository

import com.cardhunt.app.core.NetworkResult
import com.cardhunt.app.core.safeApiCall
import com.cardhunt.app.data.remote.OsrmApi
import org.osmdroid.util.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutingRepository @Inject constructor(private val osrmApi: OsrmApi) {

    suspend fun getRoute(start: GeoPoint, end: GeoPoint): NetworkResult<List<GeoPoint>> {
        val coords = "${start.longitude},${start.latitude};${end.longitude},${end.latitude}"
        return safeApiCall { osrmApi.getRoute(coords) }.let { res ->
            when (res) {
                is NetworkResult.Success -> {
                    val points = res.data.routes.firstOrNull()?.geometry?.coordinates?.map {
                        GeoPoint(it[1], it[0]) // OSRM returns [lon, lat]; osmdroid needs (lat, lon)
                    } ?: emptyList()
                    NetworkResult.Success(points)
                }
                is NetworkResult.Error -> res
            }
        }
    }
}