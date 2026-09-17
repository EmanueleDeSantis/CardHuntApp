package com.cardhunt.app.ui.staff

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.cardhunt.app.data.remote.dto.AdminCardDto
import com.cardhunt.app.data.remote.dto.AdminUserDto
import com.cardhunt.app.ui.common.CardSort
import com.cardhunt.app.ui.common.SortSelector
import com.cardhunt.app.ui.common.UserSort
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StaffDashboardScreen(
    navController: NavController,
    vm: StaffDashboardViewModel = hiltViewModel(),
) {
    val users by vm.users.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val snackHost = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.loadData()
        vm.snackbar.collect { snackHost.showSnackbar(it) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackHost) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Users") })
                Tab(selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Cards") })
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> UsersList(users, vm)
                    1 -> CardsTab(cards, vm, navController)
                }
            }
        }
    }
}

@Composable
fun UsersList(users: List<AdminUserDto>, vm: StaffDashboardViewModel) {
    val sorted = remember(users, vm.userSort) { vm.sortUsers(users) }
    Column(Modifier.fillMaxSize()) {
        SortSelector(
            options = UserSort.entries,
            selected = vm.userSort,
            label = { it.label },
            onSelect = { vm.updateUserSort(it) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No users found.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sorted, key = { it.id }) { user ->
                    val isSelf = user.id == vm.currentUserId
                    val targetIsStaff = user.role == "ADMIN" || user.role == "MODERATOR"
                    val canModerateStatus = !isSelf && (vm.currentRole == "ADMIN" || !targetIsStaff)
                    val canChangeRole = vm.currentRole == "ADMIN" && !isSelf && user.role != "ADMIN"

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelf) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(user.username + if (isSelf) " (you)" else "",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelf) FontWeight.Bold else FontWeight.Normal)
                                Text("${if (user.role == "MODERATOR") "MOD" else user.role} · ${user.status} · ${user.xp} XP · ${user.cardsCount} cards",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (canModerateStatus) {
                                var expStatus by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(onClick = { expStatus = true }) { Text(user.status) }
                                    DropdownMenu(expanded = expStatus,
                                        onDismissRequest = { expStatus = false }) {
                                        listOf("ACTIVE", "SUSPENDED", "BANNED").forEach { s ->
                                            DropdownMenuItem(text = { Text(s) }, onClick = {
                                                expStatus = false; vm.updateStatus(user.id, s)
                                            })
                                        }
                                    }
                                }
                            }
                            if (canChangeRole) {
                                var expRole by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(onClick = { expRole = true }) {
                                        Text(if (user.role == "MODERATOR") "MOD" else user.role)
                                    }
                                    DropdownMenu(expanded = expRole,
                                        onDismissRequest = { expRole = false }) {
                                        listOf("USER", "MODERATOR").forEach { r ->
                                            DropdownMenuItem(text = { Text(if (r == "MODERATOR") "MOD" else r) }, onClick = {
                                                expRole = false; vm.updateRole(user.id, r)
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CardsTab(cards: List<AdminCardDto>, vm: StaffDashboardViewModel, navController: NavController) {
    var pendingDelete by remember { mutableStateOf<AdminCardDto?>(null) }
    val sorted = remember(cards, vm.cardSort) { vm.sortCards(cards) }

    Column(Modifier.fillMaxSize()) {
        SortSelector(
            options = CardSort.entries,
            selected = vm.cardSort,
            label = { it.label },
            onSelect = { vm.updateCardSort(it) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (sorted.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No cards yet — place the first one!", textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sorted, key = { it.id }) { card ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            navController.navigate("place_card/${card.latitude}/${card.longitude}")
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(card.name, style = MaterialTheme.typography.titleMedium)
                                Text("%.5f, %.5f · ${card.rarity} · ${card.points} XP"
                                    .format(card.latitude, card.longitude),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("👥 ${card.collections} collected · 📷 ${card.photoCollections} original shoots",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { pendingDelete = card }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Remove card",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = { navController.navigate("place_card") },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) {
            Text("📍 Place a new card on the map")
        }
    }

    pendingDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this card?") },
            text = { Text("\"${card.name}\" will be permanently removed for all players.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteCard(card.id); pendingDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}