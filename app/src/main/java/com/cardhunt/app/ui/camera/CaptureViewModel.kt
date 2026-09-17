package com.cardhunt.app.ui.camera

import android.content.Context
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardhunt.app.core.NetworkResult
import com.cardhunt.app.data.remote.dto.CollectResponse
import com.cardhunt.app.data.repository.CardRepository
import com.cardhunt.app.location.LocationTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class CapturePhase { IDLE, CAPTURING, CONFIRMING, PROCESSING, COLLECTED, FAILED }

data class CaptureState(
    val phase: CapturePhase = CapturePhase.IDLE,
    val capturedPhoto: File? = null,
    val result: CollectResponse? = null,
    val error: String? = null,
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val cardRepo: CardRepository,
    private val locationTracker: LocationTracker,
) : ViewModel() {

    val cardId: Int = savedState.get<String>("cardId")!!.toInt()
    val cardName: String = Uri.decode(savedState.get<String>("cardName")!!)
    val rarity: String = savedState.get<String>("rarity") ?: "COMMON"

    private var imageCapture: ImageCapture? = null
    private val _state = MutableStateFlow(CaptureState())
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    fun bindImageCapture(capture: ImageCapture) { imageCapture = capture }

    fun takePhoto(context: Context) {
        val capture = imageCapture ?: return
        _state.update { it.copy(phase = CapturePhase.CAPTURING, error = null) }
        val file = File(context.cacheDir, "shoot_${System.currentTimeMillis()}.jpg")

        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                    _state.update { it.copy(phase = CapturePhase.CONFIRMING, capturedPhoto = file) }
                }
                override fun onError(e: ImageCaptureException) {
                    _state.update { it.copy(phase = CapturePhase.IDLE, error = e.message) }
                }
            })
    }

    fun confirmPhoto() {
        val file = _state.value.capturedPhoto ?: return
        viewModelScope.launch { processAndCollect(file) }
    }

    fun retakePhoto() {
        _state.value.capturedPhoto?.delete()
        _state.update { it.copy(phase = CapturePhase.IDLE, capturedPhoto = null, error = null) }
    }

    private suspend fun processAndCollect(file: File) {
        _state.update { it.copy(phase = CapturePhase.PROCESSING) }

        val loc = locationTracker.lastKnown()
        if (loc == null) {
            _state.update { it.copy(phase = CapturePhase.FAILED, error = "Waiting for a GPS fix — try again") }
            return
        }

        when (val r = cardRepo.collectWithPhoto(cardId, file, loc.latitude, loc.longitude, rarity)) {
            is NetworkResult.Success ->
                _state.update { it.copy(phase = CapturePhase.COLLECTED, result = r.data) }
            is NetworkResult.Error ->
                _state.update { it.copy(phase = CapturePhase.FAILED, error = r.message) }
        }
    }

    fun retryFromFailure() {
        _state.value.capturedPhoto?.delete()
        _state.update { it.copy(phase = CapturePhase.IDLE, capturedPhoto = null, error = null) }
    }
}