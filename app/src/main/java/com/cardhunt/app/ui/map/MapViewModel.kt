package com.cardhunt.app.ui.map

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardhunt.app.core.NetworkResult
import com.cardhunt.app.data.model.Card
import com.cardhunt.app.data.repository.AuthRepository
import com.cardhunt.app.data.repository.CardRepository
import com.cardhunt.app.data.repository.RoutingRepository
import com.cardhunt.app.location.LocationTracker
import com.cardhunt.app.location.ProximityMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import javax.inject.Inject

sealed class MapItem {
    data class Single(val card: Card) : MapItem()
}

data class MapUiState(
    val cards: List<Card> = emptyList(),
    val mapItems: List<MapItem> = emptyList(),
    val userLocation: Location? = null,
    val initialCenter: GeoPoint? = null,
    val selectedCard: Card? = null,
    val snackbar: String? = null,
    val isStaff: Boolean = false,
    val routePoints: List<GeoPoint>? = null,
    val zoomToBox: BoundingBox? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val cardRepo: CardRepository,
    private val locationTracker: LocationTracker,
    private val proximityMonitor: ProximityMonitor,
    private val routingRepo: RoutingRepository,
    authRepo: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MapUiState(isStaff = authRepo.isAdmin || authRepo.snapshot()?.role == "MODERATOR")
    )
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    var savedCenter: GeoPoint? = null
        private set
    var savedZoom: Double = 15.0
        private set

    fun rememberMapView(center: GeoPoint, zoom: Double) {
        savedCenter = center
        savedZoom = zoom
    }

    private var started = false
    private var lastRegion: DoubleArray? = null
    private var loadJob: Job? = null
    private var initialCenterConsumed = false

    init {
        viewModelScope.launch {
            cardRepo.collectedEvents.collect { event ->
                // Refresh from server to get accurate state
                lastRegion?.let { region ->
                    loadRegion(region[0], region[1], region[2], region[3])
                }
            }
        }
    }

    fun startLocationUpdates() {
        if (started) return
        started = true
        viewModelScope.launch {
            locationTracker.lastKnown()?.let {
                if (_uiState.value.initialCenter == null && !initialCenterConsumed) {
                    _uiState.update { s -> s.copy(initialCenter = GeoPoint(it.latitude, it.longitude)) }
                }
            }
        }
        viewModelScope.launch {
            locationTracker.locationFlow.collect { loc ->
                _uiState.update { s -> s.copy(userLocation = loc) }
                if (_uiState.value.initialCenter == null && !initialCenterConsumed) {
                    _uiState.update { s -> s.copy(initialCenter = GeoPoint(loc.latitude, loc.longitude)) }
                }
                if (_uiState.value.cards.isEmpty() && lastRegion == null) {
                    loadRegion(loc.latitude - 0.03, loc.latitude + 0.03,
                        loc.longitude - 0.03, loc.longitude + 0.03)
                }
                proximityMonitor.check(loc)
            }
        }
    }

    fun centerOnUser() {
        val loc = _uiState.value.userLocation
        if (loc != null) {
            _uiState.update { it.copy(initialCenter = GeoPoint(loc.latitude, loc.longitude)) }
            return
        }
        viewModelScope.launch {
            val last = locationTracker.lastKnown()
            if (last != null) {
                _uiState.update { it.copy(initialCenter = GeoPoint(last.latitude, last.longitude)) }
            } else {
                _uiState.update { it.copy(snackbar = "No GPS fix yet — try again shortly") }
            }
        }
    }

    fun focusOnCard(lat: Double, lng: Double) {
        val pad = 0.002
        val bb = BoundingBox.fromGeoPoints(listOf(
            GeoPoint(lat - pad, lng - pad),
            GeoPoint(lat + pad, lng + pad)
        ))
        _uiState.update { it.copy(zoomToBox = bb) }
    }

    fun consumeInitialCenter() {
        initialCenterConsumed = true
        _uiState.update { it.copy(initialCenter = null) }
    }

    fun consumeZoomToBox() {
        _uiState.update { it.copy(zoomToBox = null) }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbar = null) }
    }

    fun dismissSheet() {
        _uiState.update { it.copy(selectedCard = null) }
    }

    fun loadCardsForViewport(bb: BoundingBox) =
        loadRegion(bb.latSouth, bb.latNorth, bb.lonWest, bb.lonEast)

    private fun loadRegion(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double) {
        lastRegion = doubleArrayOf(minLat, maxLat, minLng, maxLng)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            when (val r = cardRepo.getCards(minLat, maxLat, minLng, maxLng)) {
                is NetworkResult.Success -> {
                    // Trust server data completely
                    val serverCards = r.data
                    if (serverCards != _uiState.value.cards) {
                        _uiState.update { it.copy(cards = serverCards) }
                        recomputeMapItems(serverCards)
                    }
                }
                is NetworkResult.Error ->
                    _uiState.update { it.copy(snackbar = r.message) }
            }
        }
    }

    fun onZoomChanged(zoom: Double) {
        // intentionally empty
    }

    private fun recomputeMapItems(cards: List<Card>) {
        _uiState.update { it.copy(mapItems = cards.map { MapItem.Single(it) }) }
    }

    fun onMapItemClick(item: MapItem) {
        when (item) {
            is MapItem.Single ->
                _uiState.update { it.copy(selectedCard = item.card, routePoints = null) }
        }
    }

    fun getDirections(card: Card) {
        val start = _uiState.value.userLocation
        if (start == null) {
            _uiState.update { it.copy(snackbar = "Waiting for a GPS fix — try again in a moment") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(routePoints = emptyList(), selectedCard = null) }
            when (val res = routingRepo.getRoute(
                start = GeoPoint(start.latitude, start.longitude),
                end = GeoPoint(card.lat, card.lng)
            )) {
                is NetworkResult.Success -> {
                    _uiState.update { it.copy(routePoints = res.data) }
                    if (res.data.size > 1) {
                        _uiState.update { it.copy(zoomToBox = BoundingBox.fromGeoPoints(res.data)) }
                    }
                }
                is NetworkResult.Error ->
                    _uiState.update { it.copy(snackbar = "Routing failed: ${res.message}") }
            }
        }
    }
}