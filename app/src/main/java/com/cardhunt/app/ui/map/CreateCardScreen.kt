package com.cardhunt.app.ui.map

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.cardhunt.app.core.NetworkResult
import com.cardhunt.app.data.repository.CardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import javax.inject.Inject

@HiltViewModel
class CreateCardViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val cardRepo: CardRepository,
    @ApplicationContext private val ctx: Context,) : ViewModel() {

    var lat by mutableStateOf(savedState.get<String>("lat")!!.toDouble())
    var lng by mutableStateOf(savedState.get<String>("lng")!!.toDouble())
    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var radius by mutableStateOf("50")
    var points by mutableStateOf("10")
    var rarity by mutableStateOf("COMMON")
    var busy by mutableStateOf(false)

    val rarities = listOf("COMMON", "RARE", "EPIC", "LEGENDARY")

    fun updatePin(newLat: Double, newLng: Double) {
        lat = newLat; lng = newLng
    }

    fun submit(nav: NavController) {
        if (name.isBlank()) {
            Toast.makeText(ctx, "Please enter a card name", Toast.LENGTH_SHORT).show()
            return
        }
        busy = true
        viewModelScope.launch {
            val r = cardRepo.createCard(name, description.ifBlank { null }, lat, lng,
                radius.toIntOrNull() ?: 50, rarity, points.toIntOrNull() ?: 10)
            busy = false
            when (r) {
                is NetworkResult.Success -> {
                    Toast.makeText(ctx, "Card created ✓", Toast.LENGTH_SHORT).show()
                    nav.popBackStack()
                }
                is NetworkResult.Error ->
                    Toast.makeText(ctx, r.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
fun CreateCardScreen(nav: NavController, vm: CreateCardViewModel = hiltViewModel()) {

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        // ---- Mini-map preview with draggable pin ----
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            AndroidView(
                factory = { mapCtx ->
                    MapView(mapCtx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(17.0)
                        controller.setCenter(GeoPoint(vm.lat, vm.lng))

                        overlays.add(Marker(this).apply {
                            position = GeoPoint(vm.lat, vm.lng)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "Drag me to adjust the position"
                            isDraggable = true
                            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                                override fun onMarkerDragStart(marker: Marker?) {}
                                override fun onMarkerDrag(marker: Marker?) {}
                                override fun onMarkerDragEnd(marker: Marker?) {
                                    marker?.position?.let {
                                        vm.updatePin(it.latitude, it.longitude)
                                    }
                                }
                            })
                        })
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Surface(tonalElevation = 4.dp, shape = MaterialTheme.shapes.small,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                Text("📍 ${"%.5f".format(vm.lat)}, ${"%.5f".format(vm.lng)}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall)
            }
        }

        // ---- Card details form ----
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Create a New Card", style = MaterialTheme.typography.titleLarge)
            Text("Drag the pin on the map to fine-tune the target location.",
                style = MaterialTheme.typography.bodySmall)

            OutlinedTextField(vm.name, { vm.name = it },
                label = { Text("Name (e.g. Eiffel Tower)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(vm.description, { vm.description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(), minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(vm.radius, { vm.radius = it }, label = { Text("Radius (m)") },
                    singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(vm.points, { vm.points = it }, label = { Text("XP") },
                    singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.rarities.forEach { r ->
                    FilterChip(selected = vm.rarity == r, onClick = { vm.rarity = r },
                        label = { Text(r) })
                }
            }
            Button(onClick = { vm.submit(nav) }, enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (vm.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("Create Card")
            }
        }
    }
}