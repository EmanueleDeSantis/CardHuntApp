package com.cardhunt.app.ui.players

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.cardhunt.app.core.NetworkResult
import com.cardhunt.app.core.optimistic
import com.cardhunt.app.data.remote.dto.FriendDto
import com.cardhunt.app.data.remote.dto.FriendRequestDto
import com.cardhunt.app.data.remote.dto.PlayerDto
import com.cardhunt.app.data.remote.dto.UserSearchDto
import com.cardhunt.app.data.repository.FriendRepository
import com.cardhunt.app.ui.common.EmptyState
import com.cardhunt.app.ui.common.SortSelector
import com.cardhunt.app.ui.common.UserSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class PlayersViewModel @Inject constructor(
    private val friendRepo: FriendRepository
) : ViewModel() {

    private val _players = MutableStateFlow<List<PlayerDto>>(emptyList())
    val players = _players.asStateFlow()

    private val _myRole = MutableStateFlow("USER")
    val myRole = _myRole.asStateFlow()

    private val _incoming = MutableStateFlow<List<FriendRequestDto>>(emptyList())
    val incoming = _incoming.asStateFlow()

    private val _friends = MutableStateFlow<List<FriendDto>>(emptyList())
    val friends = _friends.asStateFlow()

    private val _outgoing = MutableStateFlow<Set<Int>>(emptySet())
    val outgoing = _outgoing.asStateFlow()

    private val _searchResults = MutableStateFlow<List<UserSearchDto>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    var searching by mutableStateOf(false)
        private set

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbar = _snackbar.asSharedFlow()

    var sort by mutableStateOf(UserSort.NAME)
        private set

    private var searchJob: Job? = null

    fun updateSort(s: UserSort) { sort = s }

    fun sortPlayers(list: List<PlayerDto>): List<PlayerDto> = when (sort) {
        UserSort.NAME -> list.sortedBy { it.username.lowercase() }
        UserSort.XP -> list.sortedByDescending { it.xp }
        UserSort.CARDS -> list.sortedByDescending { it.cardsCount }
    }

    fun sortFriends(list: List<FriendDto>): List<FriendDto> = when (sort) {
        UserSort.NAME -> list.sortedBy { it.username.lowercase() }
        UserSort.XP -> list.sortedByDescending { it.xp }
        UserSort.CARDS -> list.sortedByDescending { it.cardsCount }
    }

    fun sortRequests(list: List<FriendRequestDto>): List<FriendRequestDto> = when (sort) {
        UserSort.NAME -> list.sortedBy { it.from.username.lowercase() }
        UserSort.XP -> list.sortedByDescending { it.from.xp }
        UserSort.CARDS -> list.sortedByDescending { it.from.cardsCount }
    }

    fun load() {
        viewModelScope.launch {
            when (val r = friendRepo.getPlayers()) {
                is NetworkResult.Success -> _players.value = r.data
                is NetworkResult.Error -> _snackbar.emit("Failed to load players: ${r.message}")
            }
            // Load my role from profile
            val profileResult = friendRepo.getMyProfile()
            if (profileResult is NetworkResult.Success) {
                _myRole.value = profileResult.data.role
            }
        }
        loadFriends()
    }

    fun loadFriends() {
        viewModelScope.launch {
            val r = friendRepo.getFriends()
            if (r is NetworkResult.Success) {
                _friends.value = r.data.friends
                _incoming.value = r.data.incoming
                _outgoing.value = r.data.outgoing.toSet()
            }
        }
    }

    private fun patchLocal(playerId: Int, state: String) {
        _players.update { list ->
            list.map { if (it.id == playerId) it.copy(friendState = state) else it }
        }
    }

    fun sendRequest(p: PlayerDto) {
        viewModelScope.launch {
            optimistic(
                applyLocal = { patchLocal(p.id, "PENDING_OUT") },
                remote = { friendRepo.sendRequest(p.id) },
                rollback = { patchLocal(p.id, "NONE") }
            )
        }
    }

    fun sendRequestToSearchResult(user: UserSearchDto) {
        viewModelScope.launch {
            val r = friendRepo.sendRequest(user.id)
            if (r is NetworkResult.Success) {
                _outgoing.update { it + user.id }
                _snackbar.emit("Friend request sent to ${user.username}")
            } else if (r is NetworkResult.Error) {
                _snackbar.emit(r.message)
            }
        }
    }

    fun accept(request: FriendRequestDto) {
        val snapshot = _incoming.value
        viewModelScope.launch {
            optimistic(
                applyLocal = {
                    _incoming.update { it.filter { r -> r.requestId != request.requestId } }
                    _friends.update { it + request.from }
                    patchLocal(request.from.id, "FRIENDS")
                },
                remote = { friendRepo.accept(request.requestId) },
                rollback = {
                    _incoming.value = snapshot
                    _friends.update { it.filter { f -> f.id != request.from.id } }
                    patchLocal(request.from.id, "PENDING_IN")
                    _snackbar.emit("Accept failed — reverted")
                }
            )
        }
    }

    fun decline(request: FriendRequestDto) {
        val snapshot = _incoming.value
        viewModelScope.launch {
            optimistic(
                applyLocal = {
                    _incoming.update { it.filter { r -> r.requestId != request.requestId } }
                    patchLocal(request.from.id, "NONE")
                },
                remote = { friendRepo.decline(request.requestId) },
                rollback = {
                    _incoming.value = snapshot
                    _snackbar.emit("Decline failed — reverted")
                }
            )
        }
    }

    fun cancelRequest(requestId: Int, playerId: Int) {
        val snapshot = _players.value
        viewModelScope.launch {
            optimistic(
                applyLocal = { patchLocal(playerId, "NONE") },
                remote = { friendRepo.cancelRequest(requestId) },
                rollback = {
                    _players.value = snapshot
                    _snackbar.emit("Cancel failed — reverted")
                }
            )
        }
    }

    fun removeFriend(userId: Int) {
        val snapshotFriends = _friends.value
        viewModelScope.launch {
            optimistic(
                applyLocal = {
                    _friends.update { it.filter { f -> f.id != userId } }
                    patchLocal(userId, "NONE")
                },
                remote = { friendRepo.removeFriend(userId) },
                rollback = {
                    _friends.value = snapshotFriends
                    loadFriends()
                    _snackbar.emit("Failed to remove friend — reverted")
                }
            )
        }
    }

    fun onSearchChanged(q: String) {
        searchJob?.cancel()
        if (q.length < 2) { _searchResults.value = emptyList(); return }
        searchJob = viewModelScope.launch {
            delay(300.milliseconds)
            searching = true
            val r = friendRepo.search(q)
            searching = false
            _searchResults.value = if (r is NetworkResult.Success) r.data else emptyList()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayersScreen(vm: PlayersViewModel = hiltViewModel()) {
    val players by vm.players.collectAsStateWithLifecycle()
    val incoming by vm.incoming.collectAsStateWithLifecycle()
    val friends by vm.friends.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()
    val snackHost = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.load()
        vm.snackbar.collect { snackHost.showSnackbar(it) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackHost) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SortSelector(
                options = UserSort.entries,
                selected = vm.sort,
                label = { it.label },
                onSelect = { vm.updateSort(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("All players") })
                Tab(selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Friends") })
                Tab(selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    text = {
                        Text(if (incoming.isEmpty()) "Requests"
                        else "Requests (${incoming.size})")
                    })
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> AllPlayersTab(vm.sortPlayers(players), searchResults, vm)
                    1 -> FriendsTab(vm.sortFriends(friends), vm)
                    2 -> RequestsTab(vm.sortRequests(incoming), vm)
                }
            }
        }
    }
}

// ── Tab 0: All players ──
@Composable
private fun AllPlayersTab(
    players: List<PlayerDto>,
    searchResults: List<UserSearchDto>,
    vm: PlayersViewModel
) {
    var query by remember { mutableStateOf("") }
    val outgoing by vm.outgoing.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; vm.onSearchChanged(it) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Search by username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))

        if (vm.searching) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }

        if (query.length >= 2 && searchResults.isNotEmpty()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(searchResults, key = { it.id }) { user ->
                    val requested = user.id in outgoing
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PlayerAvatar(user.avatarUrl, user.username)
                            Text(user.username, modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge)
                            OutlinedButton(enabled = !requested,
                                onClick = { vm.sendRequestToSearchResult(user) }) {
                                Text(if (requested) "Requested" else "Add")
                            }
                        }
                    }
                }
            }
        } else if (players.isEmpty()) {
            EmptyState("No players found.")
        } else {
            LazyColumn(Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(players, key = { it.id }) { p -> PlayerRow(p, vm) }
            }
        }
    }
}

// ── Tab 1: Friends ──
@Composable
private fun FriendsTab(friends: List<FriendDto>, vm: PlayersViewModel) {
    var friendToRemove by remember { mutableStateOf<FriendDto?>(null) }

    if (friends.isEmpty()) {
        EmptyState("No friends yet.")
    } else {
        LazyColumn(Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(friends, key = { it.id }) { f ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlayerAvatar(f.avatarUrl, f.username)
                        Column(Modifier.weight(1f)) {
                            Text(f.username, fontWeight = FontWeight.Bold)
                            Text("${f.xp} XP · ${f.cardsCount} cards collected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { friendToRemove = f }) {
                            Icon(Icons.Filled.Delete,
                                contentDescription = "Remove friend",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    friendToRemove?.let { friend ->
        AlertDialog(
            onDismissRequest = { friendToRemove = null },
            title = { Text("Remove friend") },
            text = { Text("Remove ${friend.username} from your friends? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.removeFriend(friend.id)
                        friendToRemove = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { friendToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ── Tab 2: Requests ──
@Composable
private fun RequestsTab(incoming: List<FriendRequestDto>, vm: PlayersViewModel) {
    if (incoming.isEmpty()) {
        EmptyState("No pending friend requests.")
    } else {
        LazyColumn(Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(incoming, key = { it.requestId }) { req ->
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(req.from.username,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold)
                        TextButton(onClick = { vm.decline(req) },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error)) {
                            Text("Decline")
                        }
                        Button(onClick = { vm.accept(req) },
                            modifier = Modifier.padding(start = 8.dp)) {
                            Text("Accept")
                        }
                    }
                }
            }
        }
    }
}

// ── Shared avatar composable ──
@Composable
private fun PlayerAvatar(avatarUrl: String?, username: String) {
    if (avatarUrl != null) {
        AsyncImage(model = avatarUrl, contentDescription = null,
            modifier = Modifier.size(40.dp).clip(CircleShape))
    } else {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Text(username.take(1).uppercase(), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Shared player row ──
@Composable
private fun PlayerRow(p: PlayerDto, vm: PlayersViewModel) {
    val myRole by vm.myRole.collectAsStateWithLifecycle()

    // Determine if current user can add this player
    val canAdd = when (myRole) {
        "USER" -> p.role == "USER"
        "MODERATOR" -> p.role == "MODERATOR" || p.role == "ADMIN"
        "ADMIN" -> p.role == "MODERATOR"
        else -> false
    }

    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlayerAvatar(p.avatarUrl, p.username)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.username,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    if (p.role != "USER") {
                        val roleColor = when (p.role) {
                            "MODERATOR" -> Color(0xFF2196F3)
                            "ADMIN" -> Color(0xFFF44336)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            color = roleColor.copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.extraSmall) {
                            Text(
                                text = when (p.role) {
                                    "MODERATOR" -> "MOD"
                                    "ADMIN" -> "ADM"
                                    else -> p.role.take(3)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = roleColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
                Text("${p.xp} XP · ${p.cardsCount} cards collected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (p.friendState) {
                "NONE" -> {
                    if (canAdd) {
                        Button(onClick = { vm.sendRequest(p) }) { Text("Add friend") }
                    }
                    // If canAdd is false, show nothing (no button)
                }
                "PENDING_OUT" -> OutlinedButton(
                    onClick = { p.requestId?.let { vm.cancelRequest(it, p.id) } },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Cancel request") }
                "PENDING_IN" -> OutlinedButton(enabled = false, onClick = {}) {
                    Text("Wants to be friends")
                }
                else -> Text("✓ Friends",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}