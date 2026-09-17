package com.cardhunt.app.ui.collection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.room3.Delete
import coil.compose.AsyncImage
import com.cardhunt.app.core.NetworkResult
import com.cardhunt.app.data.remote.dto.AvailableFriendDto
import com.cardhunt.app.data.remote.dto.InventoryCardDto
import com.cardhunt.app.data.repository.ProfileRepository
import com.cardhunt.app.data.repository.ShareRepository
import com.cardhunt.app.sensor.ShakeDetector
import com.cardhunt.app.ui.card.OriginalShootIcon
import com.cardhunt.app.ui.common.CardSort
import com.cardhunt.app.ui.common.EmptyState
import com.cardhunt.app.ui.common.SortSelector
import com.cardhunt.app.ui.common.rarityRank
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CardFilter(val label: String) {
    ALL("All"), ORIGINAL("📷 Original"), SHARED("🎁 Shared")
}

@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val profileRepo: ProfileRepository,
    private val shareRepo: ShareRepository,
) : ViewModel() {

    private val _cards = MutableStateFlow<List<InventoryCardDto>>(emptyList())
    val cards = _cards.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbar = _snackbar.asSharedFlow()

    var sort by mutableStateOf(CardSort.DATE)
        private set

    fun updateSort(s: CardSort) { sort = s }

    fun sortCards(list: List<InventoryCardDto>): List<InventoryCardDto> = when (sort) {
        CardSort.NAME -> list.sortedBy { it.name.lowercase() }
        CardSort.RARITY -> list.sortedByDescending { rarityRank(it.rarity) }
        CardSort.DATE -> list.sortedByDescending { it.collectedAt ?: "" }
    }

    fun filterCards(list: List<InventoryCardDto>, filter: CardFilter): List<InventoryCardDto> = when (filter) {
        CardFilter.ALL -> list
        CardFilter.ORIGINAL -> list.filter { it.acquisition == "PHOTO" }
        CardFilter.SHARED -> list.filter { it.acquisition == "SHARE" }
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            val r = profileRepo.getInventory()
            if (r is NetworkResult.Success) _cards.value = r.data
            else if (r is NetworkResult.Error) _snackbar.emit("Failed to load cards: ${r.message}")
            _loading.value = false
        }
    }

    fun shareCard(cardId: Int, friendId: Int, friendName: String) {
        viewModelScope.launch {
            val r = shareRepo.share(cardId, friendId)
            if (r is NetworkResult.Success) {
                _snackbar.emit("Card shared with $friendName!")
            } else if (r is NetworkResult.Error) {
                _snackbar.emit(r.message)
            }
        }
    }

    fun deleteCard(cardId: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val r = profileRepo.deleteCard(cardId)
            when (r) {
                is NetworkResult.Success -> {
                    _cards.update { list -> list.filter { it.id != cardId } }
                    _snackbar.emit("Card removed from your collection")
                    onResult(true)
                }
                is NetworkResult.Error -> {
                    _snackbar.emit("Failed to delete card: ${r.message}")
                    onResult(false)
                }
            }
        }
    }

    suspend fun getAvailableFriends(cardId: Int): List<AvailableFriendDto> {
        val r = shareRepo.getAvailableFriends(cardId)
        return if (r is NetworkResult.Success) r.data else emptyList()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionScreen(
    vm: CollectionViewModel = hiltViewModel(),
    shakeDetector: ShakeDetector = hiltViewModel<ShakeDetectorHolder>().shakeDetector
) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val snackHost = remember { SnackbarHostState() }
    var selectedCard by remember { mutableStateOf<InventoryCardDto?>(null) }
    var shareTarget by remember { mutableStateOf<InventoryCardDto?>(null) }

    val pagerState = rememberPagerState(pageCount = { CardFilter.values().size })
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.refresh()
        vm.snackbar.collect { snackHost.showSnackbar(it) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackHost) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SortSelector(
                options = CardSort.values().toList(),
                selected = vm.sort,
                label = { it.label },
                onSelect = { vm.updateSort(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            TabRow(selectedTabIndex = pagerState.currentPage) {
                CardFilter.values().forEachIndexed { index, filter ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(filter.label) })
                }
            }

            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Loading your collection...",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                else -> {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        val filter = CardFilter.values()[page]
                        val filtered = remember(cards, vm.sort) {
                            vm.sortCards(vm.filterCards(cards, filter))
                        }
                        CardsTab(filtered = filtered, filter = filter,
                            onSelect = { selectedCard = it },
                            onShare = { shareTarget = it })
                    }
                }
            }
        }
    }

    selectedCard?.let { card ->
        CardCloseUp(card, vm, shakeDetector) { selectedCard = null }
    }
    shareTarget?.let { card ->
        ShareDialog(card = card, vm = vm, onDismiss = { shareTarget = null })
    }
}

@Composable
private fun CardsTab(
    filtered: List<InventoryCardDto>,
    filter: CardFilter,
    onSelect: (InventoryCardDto) -> Unit,
    onShare: (InventoryCardDto) -> Unit
) {
    if (filtered.isEmpty()) {
        EmptyState(when (filter) {
            CardFilter.ALL -> "Your collection is empty.\nFind cards on the map and shoot them!"
            CardFilter.ORIGINAL -> "No original cards yet.\nCollect cards by taking your own photo!"
            CardFilter.SHARED -> "No shared cards yet.\nFriends can share cards with you!"
        })
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(10.dp),
            modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.id }) { c ->
                InventoryCardItem(c,
                    onClick = { onSelect(c) },
                    onShare = { onShare(c) })
            }
        }
    }
}

@Composable
private fun InventoryCardItem(
    c: InventoryCardDto,
    onClick: () -> Unit,
    onShare: () -> Unit
) {
    Card(Modifier.padding(6.dp).aspectRatio(0.75f).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxSize()) {
            if (c.photoUrl != null) {
                AsyncImage(model = c.photoUrl, contentDescription = c.name,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFF37474F), Color(0xFF111418)))))
            }

            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent,
                        Color.Transparent, Color.Black.copy(alpha = 0.65f)))))

            Column(Modifier.align(Alignment.TopStart).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(c.name, color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RarityPill(c.rarity)
                    if (c.hasOriginalShoot) {
                        OriginalShootIcon(Modifier.size(20.dp))
                    } else if (c.acquisition == "SHARE") {
                        Surface(color = Color(0xFF9C27B0).copy(alpha = 0.9f),
                            shape = RoundedCornerShape(4.dp)) {
                            Text("🎁", style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
            }

            if (c.acquisition == "PHOTO") {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
                    Icon(Icons.Filled.Share, contentDescription = "Share",
                        tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Text("${c.points} XP",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
    }
}

@Composable
fun RarityPill(rarity: String) {
    val color = when (rarity) {
        "LEGENDARY" -> Color(0xFFF5C518)
        "EPIC" -> Color(0xFF9C27B0)
        "RARE" -> Color(0xFF2196F3)
        else -> Color(0xFF9E9E9E)
    }
    Box(Modifier.clip(RoundedCornerShape(4.dp))
        .background(color.copy(alpha = 0.9f))
        .padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(rarity, style = MaterialTheme.typography.labelSmall,
            color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CardCloseUp(
    c: InventoryCardDto,
    vm: CollectionViewModel,
    shakeDetector: ShakeDetector,
    onDismiss: () -> Unit
) {
    var showShareDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isOriginalShoot = c.acquisition == "PHOTO"

    // Shake detection for original shoots
    if (isOriginalShoot) {
        LaunchedEffect(c.id) {
            shakeDetector.shakeFlow().collect {
                showShareDialog = true
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)) {
            Box(Modifier.fillMaxWidth().aspectRatio(0.75f)) {
                if (c.photoUrl != null) {
                    AsyncImage(model = c.photoUrl, contentDescription = c.name,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0xFF37474F), Color(0xFF111418)))))
                }
            }
            Column(Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (c.hasOriginalShoot) {
                        OriginalShootIcon(Modifier.size(28.dp))
                    } else if (c.acquisition == "SHARE") {
                        Text("🎁 Shared", style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF9C27B0))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RarityPill(c.rarity)
                    Text("${c.points} XP", style = MaterialTheme.typography.bodySmall)
                }
                c.collectedAt?.take(10)?.let {
                    Text("Collected on $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Share button for original shoots
                if (isOriginalShoot) {
                    Text("📱 Shake device to share",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = { showShareDialog = true },
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Share, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share with a friend")
                    }
                }

                // Delete button
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Remove from collection")
                }

                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Close")
                }
            }
        }
    }

    if (showShareDialog) {
        ShareDialog(
            card = c,
            vm = vm,
            onDismiss = { showShareDialog = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove card?") },
            text = {
                Text("This will remove \"${c.name}\" from your collection. " +
                        if (isOriginalShoot) "You'll lose ${c.points} XP."
                        else "Shared cards don't affect your XP.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        vm.deleteCard(c.id) { success ->
                            if (success) onDismiss()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ShareDialog(
    card: InventoryCardDto,
    vm: CollectionViewModel,
    onDismiss: () -> Unit
) {
    var friends by remember { mutableStateOf<List<AvailableFriendDto>?>(null) }

    LaunchedEffect(card.id) {
        friends = vm.getAvailableFriends(card.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share \"${card.name}\"") },
        text = {
            val list = friends
            when {
                list == null -> {
                    Box(Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                list.isEmpty() -> {
                    Text("None of your friends need this card.\nThey either already own it or have a pending share.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(list, key = { it.id }) { friend ->
                            Card(Modifier.fillMaxWidth().clickable {
                                vm.shareCard(card.id, friend.id, friend.username)
                                onDismiss()
                            }) {
                                Row(Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (friend.avatarUrl != null) {
                                        AsyncImage(model = friend.avatarUrl, contentDescription = null,
                                            modifier = Modifier.size(36.dp).clip(CircleShape))
                                    } else {
                                        Surface(modifier = Modifier.size(36.dp),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(friend.username.take(1).uppercase(),
                                                    fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Text(friend.username,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// Simple holder to inject ShakeDetector into the composable tree
@HiltViewModel
class ShakeDetectorHolder @Inject constructor(val shakeDetector: ShakeDetector) : ViewModel()