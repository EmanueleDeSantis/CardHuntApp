package com.cardhunt.app.ui.card

import android.location.Location
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cardhunt.app.core.NetworkResult
import com.cardhunt.app.data.model.Card
import com.cardhunt.app.data.remote.dto.FriendDto
import com.cardhunt.app.data.repository.FriendRepository
import com.cardhunt.app.data.repository.ShareRepository
import com.cardhunt.app.sensor.TargetCompass
import com.cardhunt.app.ui.map.MapViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt
import androidx.lifecycle.viewModelScope
import com.cardhunt.app.sensor.ShakeDetector

fun distanceTo(loc: Location, card: Card): Float {
    val out = FloatArray(1)
    Location.distanceBetween(loc.latitude, loc.longitude, card.lat, card.lng, out)
    return out[0]
}

fun bearingTo(loc: Location, card: Card): Float {
    val out = FloatArray(3)
    Location.distanceBetween(loc.latitude, loc.longitude, card.lat, card.lng, out)
    return out[1]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailSheet(
    card: Card,
    userLocation: Location?,
    navController: NavController,
    mapVm: MapViewModel,
    onDismiss: () -> Unit,
    shareVm: ShareViewModel = hiltViewModel(),
    compass: TargetCompass = hiltViewModel<CompassHolder>().compass,
) {
    val distance = userLocation?.let { distanceTo(it, card) }
    val inRange = distance != null && distance <= card.radiusMeters + 20f

    // Card state flags
    val isCollected = card.collected
    val hasOriginalShoot = card.collectedByPhoto
    val isSharedCopy = isCollected && !hasOriginalShoot

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(card.name, style = MaterialTheme.typography.titleLarge)
                RarityChip(card.rarity)
            }
            card.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text("${card.points} XP · collection radius ${card.radiusMeters} m" +
                    (distance?.let { " · you are ${it.roundToInt()} m away" } ?: ""),
                style = MaterialTheme.typography.bodySmall)

            // Status section (shown when collected)
            if (isCollected) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text("✓ Collected") })
                    if (hasOriginalShoot) OriginalShootBadge()
                    if (card.pendingSync)
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }

            // Compass + camera section (shown when NOT collected OR when shared copy)
            if (!isCollected || isSharedCopy) {
                if (isSharedCopy) {
                    Text("You received this card as a share. Take your own photo to earn the Original Shoot badge!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }

                CompassRow(card, userLocation, compass)
                if (inRange) {
                    Button(
                        onClick = {
                            onDismiss()
                            navController.navigate("capture/${card.id}/${Uri.encode(card.name)}/${card.rarity}")
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(if (isSharedCopy) "📷 Take Original Shoot" else "📷 Collect by photo")
                    }
                } else {
                    OutlinedButton(
                        onClick = { mapVm.getDirections(card); onDismiss() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("🚶 Get Walking Directions") }
                    Text("You are outside the collection radius — get closer to collect.",
                        style = MaterialTheme.typography.labelSmall)
                }
            }

            // Share section (ONLY for original shoots)
            if (isCollected && hasOriginalShoot) {
                ShakeToShareCard(card, shareVm)
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@HiltViewModel
class CompassHolder @Inject constructor(val compass: TargetCompass) : ViewModel()

@Composable
fun CompassRow(card: Card, userLocation: Location?, compass: TargetCompass) {
    val azimuth by compass.azimuthFlow().collectAsState(initial = 0f)
    val bearing = userLocation?.let { bearingTo(it, card) } ?: 0f
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Direction to card",
            modifier = Modifier.size(28.dp).rotate(bearing - azimuth),
            tint = MaterialTheme.colorScheme.primary)
        Text("Point your phone this way to reach the card",
            style = MaterialTheme.typography.bodySmall)
    }
}

@HiltViewModel
class ShakeShareHolder @Inject constructor(val shakeDetector: ShakeDetector) : ViewModel()

@Composable
fun ShakeToShareCard(card: Card, vm: ShareViewModel) {
    val shakeHolder: ShakeShareHolder = hiltViewModel()
    val friends by vm.friends.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.loadFriends()
        shakeHolder.shakeDetector.shakeFlow().collect {
            if (status != ShareStatus.SENDING) {
                expanded = true
            }
        }
    }

    Column {
        Text("📱 Shake to share",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = { expanded = true },
            enabled = status != ShareStatus.SENDING,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(when (status) {
                ShareStatus.SENDING -> "Sharing…"
                ShareStatus.SENT -> "✓ Shared! Your friend will see it in their inbox"
                ShareStatus.FAILED -> "Share failed — tap to retry"
                else -> "🎁 Share this card with a friend"
            })
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (friends.isEmpty()) {
                DropdownMenuItem(text = { Text("No friends yet — add some in your Profile") }, onClick = {})
            }
            friends.forEach { f ->
                DropdownMenuItem(text = { Text("${f.username} · ${f.cardsCount} cards") },
                    onClick = { expanded = false; vm.share(card.id, f.id) })
            }
        }
    }
}

@Composable
fun RarityChip(rarity: String) {
    val color = when (rarity) {
        "LEGENDARY" -> Color(0xFFF5C518)
        "EPIC" -> Color(0xFF9C27B0)
        "RARE" -> Color(0xFF2196F3)
        else -> Color(0xFF9E9E9E)
    }
    SuggestionChip(onClick = {}, label = { Text(rarity) },
        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = color.copy(alpha = .2f)))
}

@Composable
fun OriginalShootBadge(modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OriginalShootIcon(modifier.size(22.dp))
        Text("Original Shoot", style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFB8860B))
    }
}

enum class ShareStatus { IDLE, SENDING, SENT, FAILED }

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val friendRepo: FriendRepository,
    private val shareRepo: ShareRepository,
) : ViewModel() {

    private val _friends = MutableStateFlow<List<FriendDto>>(emptyList())
    val friends = _friends.asStateFlow()
    private val _status = MutableStateFlow(ShareStatus.IDLE)
    val status = _status.asStateFlow()

    fun loadFriends() {
        viewModelScope.launch {
            val r = friendRepo.getFriends()
            if (r is NetworkResult.Success) _friends.value = r.data.friends
        }
    }

    fun share(cardId: Int, friendId: Int) {
        _status.value = ShareStatus.SENDING
        viewModelScope.launch {
            _status.value = when (shareRepo.share(cardId, friendId)) {
                is NetworkResult.Success -> ShareStatus.SENT
                is NetworkResult.Error -> ShareStatus.FAILED
            }
        }
    }
}

@Composable
fun ShareSection(card: Card, vm: ShareViewModel) {
    val friends by vm.friends.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadFriends() }

    OutlinedButton(
        onClick = { expanded = true },
        enabled = status != ShareStatus.SENDING,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(when (status) {
            ShareStatus.SENDING -> "Sharing…"
            ShareStatus.SENT -> "✓ Shared! Your friend will see it in their inbox"
            ShareStatus.FAILED -> "Share failed — tap to retry"
            else -> "🎁 Share this card with a friend"
        })
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        if (friends.isEmpty()) {
            DropdownMenuItem(text = { Text("No friends yet — add some in your Profile") }, onClick = {})
        }
        friends.forEach { f ->
            DropdownMenuItem(text = { Text("${f.username} · ${f.cardsCount} cards") },
                onClick = { expanded = false; vm.share(card.id, f.id) })
        }
    }
}