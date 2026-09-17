package com.cardhunt.app.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardhunt.app.data.remote.dto.NotificationDto
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private const val TAB_MAP = 0
private const val TAB_PLAYERS = 3
private const val TAB_PROFILE = 4

@Composable
fun NotificationsScreen(
    onOpenTab: (Int) -> Unit,
    onFocusCard: (lat: Double, lng: Double) -> Unit,
    vm: NotificationsViewModel = hiltViewModel()
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val redeeming by vm.redeemingToken.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val snackHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.startPolling()
        vm.markAllRead()
        vm.snackbar.collect { snackHost.showSnackbar(it) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackHost) }) { padding ->
        when {
            loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Loading notifications...",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    contentAlignment = Alignment.Center) {
                    Text("No notifications yet.\nNew shares and alerts will appear here.")
                }
            }
            else -> {
                LazyColumn(Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    // Clear all button at the top
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("${items.size} notifications",
                                style = MaterialTheme.typography.titleMedium)
                            TextButton(
                                onClick = { vm.clearAll() },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error)) {
                                Icon(Icons.Filled.Delete, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Clear all")
                            }
                        }
                    }

                    items(items, key = { it.id }) { n ->
                        NotificationRow(
                            n = n,
                            busy = redeeming == shareTokenOf(n),
                            onRedeem = { token -> scope.launch { vm.redeem(token) } },
                            onDelete = { vm.delete(n.id) },
                            onOpenTab = onOpenTab,
                            onFocusCard = onFocusCard
                        )
                    }
                }
            }
        }
    }
}

private fun shareTokenOf(n: NotificationDto): String? =
    n.payload?.get("share_token")?.jsonPrimitive?.contentOrNull

private fun cardLocationOf(n: NotificationDto): Pair<Double, Double>? {
    val lat = n.payload?.get("lat")?.jsonPrimitive?.doubleOrNull
    val lng = n.payload?.get("lng")?.jsonPrimitive?.doubleOrNull
    return if (lat != null && lng != null) Pair(lat, lng) else null
}

private fun targetTabFor(type: String): Int? = when (type) {
    "FRIEND_REQUEST", "FRIEND_ACCEPTED" -> TAB_PLAYERS
    "ACHIEVEMENT" -> TAB_PROFILE
    else -> null
}

private fun clickActionFor(
    type: String,
    cardLocation: Pair<Double, Double>?,
    targetTab: Int?,
    onOpenTab: (Int) -> Unit,
    onFocusCard: (Double, Double) -> Unit
): (() -> Unit)? {
    if (type == "CARD_NEARBY") {
        val loc = cardLocation
        if (loc != null) {
            val lat = loc.first
            val lng = loc.second
            return { onFocusCard(lat, lng) }
        }
        return { onOpenTab(TAB_MAP) }
    }
    val tab = targetTab
    if (tab != null) {
        val t = tab
        return { onOpenTab(t) }
    }
    return null
}

private fun formatTimestamp(isoString: String?): String {
    if (isoString == null) return ""
    return try {
        val cleanString = isoString.trim()

        // Determine the format and parse accordingly
        val date = if (cleanString.contains("T")) {
            // ISO format: "2024-01-15T14:30:00" or with timezone
            val normalizedString = cleanString.let { s ->
                when {
                    s.endsWith("Z") -> s
                    s.contains("+") -> s.split("+")[0] + "Z"
                    s.matches(Regex(".*-[0-9]{2}:[0-9]{2}$")) -> s.split(Regex("-[0-9]{2}:[0-9]{2}$"))[0] + "Z"
                    else -> s + "Z"
                }
            }
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            inputFormat.parse(normalizedString)
        } else {
            // SQLite format: "2024-01-15 14:30:00" (this is UTC from CURRENT_TIMESTAMP)
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            inputFormat.parse(cleanString)
        }

        if (date == null) return cleanString.take(16).replace('T', ' ')

        // Format in local timezone
        val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        outputFormat.timeZone = TimeZone.getDefault()
        outputFormat.format(date)
    } catch (e: Exception) {
        isoString.take(16).replace('T', ' ')
    }
}

@Composable
private fun NotificationRow(
    n: NotificationDto,
    busy: Boolean,
    onRedeem: (String) -> Unit,
    onDelete: () -> Unit,
    onOpenTab: (Int) -> Unit,
    onFocusCard: (Double, Double) -> Unit
) {
    val token = shareTokenOf(n)
    val cardLocation = cardLocationOf(n)
    val targetTab = targetTabFor(n.type)
    val clickAction = clickActionFor(n.type, cardLocation, targetTab, onOpenTab, onFocusCard)
    val isNearby = n.type == "CARD_NEARBY"

    Card(modifier = Modifier.fillMaxWidth().then(
        if (clickAction != null) Modifier.clickable(onClick = clickAction) else Modifier
    )) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (n.type) {
                        "SHARE_RECEIVED" -> "🎁 "
                        "FRIEND_REQUEST" -> "🤝 "
                        "FRIEND_ACCEPTED" -> "✅ "
                        "CARD_NEARBY" -> "📍 "
                        "ACHIEVEMENT" -> "🏆 "
                        "CARD_REDEEMED" -> "📥 "
                        else -> "🔔 "
                    } + n.message,
                    fontWeight = if (!n.isRead) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyMedium)
                formatTimestamp(n.createdAt).let { formatted ->
                    if (formatted.isNotEmpty()) {
                        Text(formatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (token != null) {
                    Button(onClick = { onRedeem(token) }, enabled = !busy) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Accept gift — add card to my collection")
                    }
                    Text("Shared cards do not include the Original Shoot badge.",
                        style = MaterialTheme.typography.labelSmall)
                } else if (isNearby && cardLocation != null) {
                    Text("Tap to go collect it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                } else if (targetTab != null) {
                    Text("Tap to open",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Close,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}