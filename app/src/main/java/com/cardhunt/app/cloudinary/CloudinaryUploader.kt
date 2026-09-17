package com.cardhunt.app.cloudinary

import android.content.Context
import android.net.Uri
import com.cardhunt.app.BuildConfig
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class CloudinaryResult(val secureUrl: String, val publicId: String)

@Singleton
class CloudinaryUploader @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    /**
     * The stylization lives in the presets (unsigned uploads reject client-side
     * "transformation" parameters). Each rarity has its own layout:
     * gray frame (COMMON), blue (RARE), purple (EPIC), thick gold (LEGENDARY).
     */
    private fun presetFor(rarity: String): String = when (rarity.uppercase()) {
        "COMMON" -> "cardhunt_common"
        "RARE" -> "cardhunt_rare"
        "EPIC" -> "cardhunt_epic"
        "LEGENDARY" -> "cardhunt_legendary"
        else -> BuildConfig.CLOUDINARY_UPLOAD_PRESET
    }

    suspend fun uploadAndStylize(photo: File, rarity: String): CloudinaryResult =
        suspendCancellableCoroutine { cont ->

            val requestId = MediaManager.get()
                .upload(Uri.fromFile(photo))
                .unsigned(presetFor(rarity))
                .option("folder", "cardhunt/shoots")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {}
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        if (cont.isActive) {
                            cont.resume(CloudinaryResult(
                                secureUrl = resultData["secure_url"] as String,
                                publicId = resultData["public_id"] as String))
                        }
                    }
                    override fun onError(requestId: String, error: ErrorInfo) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                RuntimeException("Cloudinary: ${error.description}"))
                        }
                    }
                    override fun onReschedule(requestId: String, error: ErrorInfo) {}
                })
                .dispatch(ctx)

            cont.invokeOnCancellation { MediaManager.get().cancelRequest(requestId) }
        }
}