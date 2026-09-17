package com.cardhunt.app.ui.staff

import android.graphics.Color as AndroidColor
import android.location.Location
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.cardhunt.app.R
import com.cardhunt.app.core.NetworkResult
import com.cardhunt.app.data.model.Card
import com.cardhunt.app.data.repository.CardRepository
import com.cardhunt.app.location.LocationTracker
import com.cardhunt.app.ui.common.RecenterButton
import com.cardhunt.app.ui.map.UserMarker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class CardPlacementViewModel @Inject constructor(
    private val cardRepo: CardRepository,
    val locationTracker: LocationTracker,
) : ViewModel() {
    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var radiusMeters by mutableFloatStateOf(50f)
    var rarity by mutableStateOf("COMMON")
    var points by mutableStateOf("10")
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    val rarities = listOf("COMMON", "RARE", "EPIC", "LEGENDARY")

    private val _userLocation = MutableStateFlow<Location?>(null)
    val userLocation: StateFlow<Location?> = _userLocation.asStateFlow()

    private val _existingCards = MutableStateFlow<List<Card>>(emptyList())
    val existingCards: StateFlow<List<Card>> = _existingCards.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            locationTracker.locationFlow.collect { _userLocation.value = it }
        }
    }

    fun loadCardsForViewport(bb: BoundingBox) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            delay(300.milliseconds)
            val r = cardRepo.getCards(bb.latSouth, bb.latNorth, bb.lonWest, bb.lonEast)
            if (r is NetworkResult.Success) {
                _existingCards.value = r.data
            }
        }
    }

    fun place(center: GeoPoint, nav: NavController) {
        if (name.isBlank()) { error = "Give the card a name first"; return }
        busy = true; error = null
        viewModelScope.launch {
            val r = cardRepo.createCard(
                name, description.ifBlank { null },
                center.latitude, center.longitude,
                radiusMeters.toInt(), rarity, points.toIntOrNull() ?: 10)
            when (r) {
                is NetworkResult.Success -> nav.popBackStack()
                is NetworkResult.Error -> { busy = false; error = r.message }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardPlacementScreen(
    nav: NavController,
    vm: CardPlacementViewModel = hiltViewModel(),
    initialLat: Double? = null,
    initialLng: Double? = null
) {
    val PEEK_HEIGHT = 140.dp
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var circle by remember { mutableStateOf<Polygon?>(null) }
    var centerInfo by remember { mutableStateOf("") }
    var latInput by remember { mutableStateOf("") }
    var lngInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val userLocation by vm.userLocation.collectAsStateWithLifecycle()
    val existingCards by vm.existingCards.collectAsStateWithLifecycle()
    val scaffoldState = rememberBottomSheetScaffoldState()

    fun updateCircle() {
        val map = mapRef ?: return
        val center = GeoPoint(map.mapCenter)
        val poly = circle ?: Polygon(map).also {
            it.fillPaint.color = "#33F5C518".toColorInt()
            it.outlinePaint.color = "#F5C518".toColorInt()
            it.outlinePaint.strokeWidth = 4f
            circle = it
            map.overlays.add(it)
        }
        poly.points = Polygon.pointsAsCircle(center, vm.radiusMeters.toDouble())
        map.invalidate()
    }

    fun goToCoordinates() {
        val lat = latInput.trim().toDoubleOrNull()
        val lng = lngInput.trim().toDoubleOrNull()
        if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            vm.error = "Invalid coordinates — lat must be -90..90, lng -180..180"
            return
        }
        vm.error = null
        mapRef?.controller?.apply {
            setZoom(18.0)
            setCenter(GeoPoint(lat, lng))
        }
        centerInfo = "%.5f, %.5f".format(lat, lng)
        updateCircle()
    }

    fun centerOnMe() {
        val loc = userLocation
        if (loc == null) {
            vm.error = "Waiting for a GPS fix — try again"
            return
        }
        vm.error = null
        mapRef?.controller?.apply {
            setZoom(17.0)
            setCenter(GeoPoint(loc.latitude, loc.longitude))
        }
        centerInfo = "%.5f, %.5f".format(loc.latitude, loc.longitude)
        updateCircle()
    }

    // Render existing card markers (all red)
    val placementMarkerCache = remember { mutableMapOf<Int, Marker>() }
    LaunchedEffect(mapRef, existingCards) {
        val map = mapRef ?: return@LaunchedEffect
        val expected = existingCards.associateBy { it.id }

        (placementMarkerCache.keys - expected.keys).forEach { id ->
            placementMarkerCache.remove(id)?.let(map.overlays::remove)
        }

        expected.forEach { (id, card) ->
            if (!placementMarkerCache.containsKey(id)) {
                val marker = Marker(map).apply {
                    position = GeoPoint(card.lat, card.lng)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = ContextCompat.getDrawable(map.context, R.drawable.ic_card_target)
                    title = card.name
                }
                map.overlays.add(marker)
                placementMarkerCache[id] = marker
            }
        }

        map.invalidate()
    }

    Box(Modifier.fillMaxSize()) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = PEEK_HEIGHT,
            sheetContent = {
                Column(
                    Modifier.verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🎯 Card preview — drag the map to position it",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    if (centerInfo.isNotEmpty()) {
                        Text("Coordinates: $centerInfo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(latInput, { latInput = it },
                            label = { Text("Latitude") },
                            placeholder = { Text("41.9028") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f))
                        OutlinedTextField(lngInput, { lngInput = it },
                            label = { Text("Longitude") },
                            placeholder = { Text("12.4964") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f))
                    }
                    OutlinedButton(onClick = { goToCoordinates() },
                        modifier = Modifier.fillMaxWidth()) {
                        Text("🔎 Jump to these coordinates")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(vm.name, { vm.name = it },
                            label = { Text("Name (e.g. Rome)") },
                            singleLine = true, modifier = Modifier.weight(2f))
                        OutlinedTextField(vm.points, { vm.points = it },
                            label = { Text("XP") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(vm.description, { vm.description = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth(), maxLines = 2)
                    Text("Collection radius: ${vm.radiusMeters.toInt()} m",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Slider(value = vm.radiusMeters,
                        onValueChange = { vm.radiusMeters = it; updateCircle() },
                        valueRange = 10f..500f)
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        vm.rarities.forEach { r ->
                            FilterChip(selected = vm.rarity == r,
                                onClick = { vm.rarity = r },
                                label = { Text(r) })
                        }
                    }
                    vm.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { mapRef?.let { vm.place(GeoPoint(it.mapCenter), nav) } },
                        enabled = !vm.busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        if (vm.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Text("📍 Place card here")
                    }
                }
            }
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            mapRef = this
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

                            // Center on provided coordinates or user location
                            if (initialLat != null && initialLng != null) {
                                controller.setZoom(18.0)
                                controller.setCenter(GeoPoint(initialLat, initialLng))
                                centerInfo = "%.5f, %.5f".format(initialLat, initialLng)
                                latInput = "%.6f".format(initialLat)
                                lngInput = "%.6f".format(initialLng)
                                scope.launch {
                                    delay(300)
                                    updateCircle()
                                    vm.loadCardsForViewport(boundingBox)
                                }
                            } else {
                                controller.setZoom(17.0)
                                scope.launch {
                                    vm.locationTracker.lastKnown()?.let {
                                        controller.setCenter(GeoPoint(it.latitude, it.longitude))
                                        centerInfo = "%.5f, %.5f".format(it.latitude, it.longitude)
                                        latInput = "%.6f".format(it.latitude)
                                        lngInput = "%.6f".format(it.longitude)
                                        updateCircle()
                                        vm.loadCardsForViewport(boundingBox)
                                    }
                                }
                            }

                            addMapListener(object : MapListener {
                                override fun onScroll(e: ScrollEvent?): Boolean {
                                    centerInfo = "%.5f, %.5f".format(
                                        mapCenter.latitude, mapCenter.longitude)
                                    latInput = "%.6f".format(mapCenter.latitude)
                                    lngInput = "%.6f".format(mapCenter.longitude)
                                    updateCircle()
                                    vm.loadCardsForViewport(boundingBox)
                                    return false
                                }
                                override fun onZoom(e: ZoomEvent?): Boolean {
                                    updateCircle()
                                    vm.loadCardsForViewport(boundingBox)
                                    return false
                                }
                            })
                        }
                    },
                    update = { map ->
                        userLocation?.let { loc ->
                            val me = map.overlays.filterIsInstance<UserMarker>().firstOrNull()
                                ?: UserMarker(map).also { map.overlays.add(it) }
                            me.position = GeoPoint(loc.latitude, loc.longitude)
                            map.invalidate()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Icon(
                    painter = painterResource(R.drawable.ic_card_target),
                    contentDescription = "Card position",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(52.dp).align(Alignment.Center).offset(y = (-26).dp)
                )

                IconButton(onClick = { nav.popBackStack() },
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        }

        AnimatedVisibility(
            visible = scaffoldState.bottomSheetState.currentValue != SheetValue.Expanded,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = PEEK_HEIGHT - 28.dp),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            RecenterButton(onClick = { centerOnMe() })
        }
    }
}