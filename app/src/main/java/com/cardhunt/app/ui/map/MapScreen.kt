package com.cardhunt.app.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cardhunt.app.R
import com.cardhunt.app.ui.card.CardDetailSheet
import com.cardhunt.app.ui.common.RecenterButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.time.Duration.Companion.milliseconds

private const val COLLECTED_TAG = "collected"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    nav: NavController,
    vm: MapViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val scaffoldState = rememberBottomSheetScaffoldState()

    var mapRef by remember { mutableStateOf<MapView?>(null) }
    val markerCache = remember { mutableMapOf<String, Marker>() }
    var viewportJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(mapRef) {
        mapRef?.onResume()
        onDispose { mapRef?.onPause() }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasPermission = grants.values.any { it }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            vm.startLocationUpdates()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(mapRef, state.initialCenter) {
        val map = mapRef ?: return@LaunchedEffect
        state.initialCenter?.let { gp ->
            map.controller.apply {
                setZoom(15.0)
                animateTo(gp)
            }
            vm.consumeInitialCenter()
        }
    }

    LaunchedEffect(mapRef, state.zoomToBox) {
        val map = mapRef ?: return@LaunchedEffect
        state.zoomToBox?.let { bb ->
            map.zoomToBoundingBox(bb, true, 80)
            vm.consumeZoomToBox()
        }
    }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHost.showSnackbar(it)
            vm.consumeSnackbar()
        }
    }

    LaunchedEffect(mapRef, state.mapItems) {
        val map = mapRef ?: return@LaunchedEffect
        renderMapItems(
            map = map,
            items = state.mapItems,
            cache = markerCache,
            onClick = { vm.onMapItemClick(it) }
        )
    }

    LaunchedEffect(mapRef, state.routePoints) {
        val map = mapRef ?: return@LaunchedEffect
        val existing = map.overlays.filterIsInstance<RoutePolyline>()
        val target = state.routePoints?.takeIf { it.isNotEmpty() }

        if (target == null && existing.isEmpty()) return@LaunchedEffect

        existing.forEach(map.overlays::remove)

        target?.let { points ->
            RoutePolyline(map).also {
                it.setPoints(points)
                map.overlays.add(it)
            }
        }

        map.invalidate()
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 0.dp,
        containerColor = Color.Transparent,
        sheetContainerColor = Color.Transparent,
        sheetContent = {
            Spacer(Modifier.height(1.dp))
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        mapRef = this

                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

                        val saved = vm.savedCenter
                        if (saved != null) {
                            controller.setZoom(vm.savedZoom)
                            controller.setCenter(saved)
                        } else {
                            controller.setZoom(4.0)
                            controller.setCenter(GeoPoint(48.85837, 2.29448))
                        }

                        overlays.add(
                            MapEventsOverlay(
                                object : MapEventsReceiver {
                                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                        return false
                                    }

                                    override fun longPressHelper(p: GeoPoint): Boolean {
                                        if (state.isStaff) {
                                            nav.navigate("create_card/${p.latitude}/${p.longitude}")
                                        }
                                        return state.isStaff
                                    }
                                }
                            )
                        )

                        addMapListener(
                            object : MapListener {
                                override fun onScroll(e: ScrollEvent?): Boolean {
                                    mapRef?.let {
                                        vm.rememberMapView(
                                            GeoPoint(it.mapCenter),
                                            it.zoomLevelDouble
                                        )
                                    }
                                    scheduleFetch()
                                    return false
                                }

                                override fun onZoom(e: ZoomEvent?): Boolean {
                                    mapRef?.let {
                                        vm.onZoomChanged(it.zoomLevelDouble)
                                        vm.rememberMapView(
                                            GeoPoint(it.mapCenter),
                                            it.zoomLevelDouble
                                        )
                                    }
                                    scheduleFetch()
                                    return false
                                }

                                private fun scheduleFetch() {
                                    viewportJob?.cancel()
                                    viewportJob = scope.launch {
                                        delay(600.milliseconds)
                                        mapRef?.let {
                                            vm.loadCardsForViewport(it.boundingBox)
                                        }
                                    }
                                }
                            }
                        )
                    }
                },
                update = { map ->
                    state.userLocation?.let { loc ->
                        val me = map.overlays.filterIsInstance<UserMarker>().firstOrNull()
                            ?: UserMarker(map).also { map.overlays.add(it) }

                        me.position = GeoPoint(loc.latitude, loc.longitude)
                        map.invalidate()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            RecenterButton(
                onClick = { vm.centerOnUser() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )

            state.selectedCard?.let { selected ->
                CardDetailSheet(
                    card = state.cards.firstOrNull { it.id == selected.id } ?: selected,
                    userLocation = state.userLocation,
                    navController = nav,
                    mapVm = vm,
                    onDismiss = { vm.dismissSheet() },
                )
            }

            SnackbarHost(
                hostState = snackbarHost,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

class UserMarker(map: MapView) : Marker(map) {
    init {
        setAnchor(ANCHOR_CENTER, ANCHOR_CENTER)
        icon = ContextCompat.getDrawable(map.context, R.drawable.ic_user_dot)
        title = "You"
    }
}

class RoutePolyline(map: MapView) : Polyline(map) {
    init {
        outlinePaint.color = AndroidColor.BLUE
        outlinePaint.strokeWidth = 12f
    }
}

private fun renderMapItems(
    map: MapView,
    items: List<MapItem>,
    cache: MutableMap<String, Marker>,
    onClick: (MapItem) -> Unit,
) {
    val expected = mutableMapOf<String, MapItem>()

    items.forEach { item ->
        if (item is MapItem.Single) {
            val key = "c_${item.card.id}_${item.card.collected}_${item.card.collectedByPhoto}"
            expected[key] = item
        }
    }

    var changed = false

    (cache.keys - expected.keys).forEach { key ->
        cache.remove(key)?.let(map.overlays::remove)
        changed = true
    }

    expected.forEach { (key, item) ->
        if (cache.containsKey(key)) return@forEach

        // Safe cast since we only add Single items to expected
        val single = item as MapItem.Single

        val marker = Marker(map).apply {
            position = GeoPoint(single.card.lat, single.card.lng)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(
                map.context,
                when {
                    single.card.collected && single.card.collectedByPhoto -> R.drawable.ic_card_collected
                    single.card.collected                                -> R.drawable.ic_card_shared
                    else                                               -> R.drawable.ic_card_target
                }
            )
            title = single.card.name
            relatedObject = if (single.card.collected) COLLECTED_TAG else null

            setOnMarkerClickListener { _, _ ->
                onClick(item)
                true
            }
        }

        map.overlays.add(marker)
        cache[key] = marker
        changed = true
    }

    if (changed) map.invalidate()
}