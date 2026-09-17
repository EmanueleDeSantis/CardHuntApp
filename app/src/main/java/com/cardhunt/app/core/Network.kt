package com.cardhunt.app.core

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response
import java.io.IOException

sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val code: Int?, val message: String) : NetworkResult<Nothing>()
}

inline fun <T, R> NetworkResult<T>.map(f: (T) -> R): NetworkResult<R> = when (this) {
    is NetworkResult.Success -> NetworkResult.Success(f(data))
    is NetworkResult.Error -> this
}

suspend fun <T> safeApiCall(call: suspend () -> Response<T>): NetworkResult<T> = try {
    val res = call()
    if (res.isSuccessful) {
        @Suppress("UNCHECKED_CAST")
        if (res.body() == null) NetworkResult.Success(Unit as T)
        else NetworkResult.Success(res.body()!!)
    } else {
        val msg = try {
            val body = res.errorBody()?.string()
            if (body.isNullOrBlank()) null
            else Json.parseToJsonElement(body).jsonObject?.get("error")?.jsonPrimitive?.content
        } catch (_: Exception) { null }
        NetworkResult.Error(res.code(), msg ?: "Request failed (${res.code()})")
    }
} catch (e: CancellationException) {
    throw e   // CRITICAL: cancellation is not an error — fixes map flickering app-wide
} catch (e: IOException) {
    NetworkResult.Error(null, "Network error — check your connection")
} catch (e: Exception) {
    NetworkResult.Error(null, "Unexpected error: ${e.message}")
}

/** Optimistic UI engine: apply instantly, sync in background, rollback on failure. */
suspend fun <T> optimistic(
    applyLocal: suspend () -> Unit,
    remote: suspend () -> NetworkResult<T>,
    rollback: suspend () -> Unit,
): NetworkResult<T> {
    applyLocal()
    val result = remote()
    if (result is NetworkResult.Error) rollback()
    return result
}