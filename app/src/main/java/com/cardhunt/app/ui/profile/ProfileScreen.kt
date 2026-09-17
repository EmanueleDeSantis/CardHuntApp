package com.cardhunt.app.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.cardhunt.app.data.local.ThemeStore
import com.cardhunt.app.data.remote.dto.AchievementDto
import com.cardhunt.app.data.remote.dto.ProfileDto
import com.cardhunt.app.data.remote.dto.StatsDto

@Composable
fun ProfileScreen(
    navController: NavController,
    onLogout: () -> Unit,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val snackHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showAvatarOptions by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { vm.uploadAvatar(it, context) }
    }

    LaunchedEffect(Unit) {
        vm.loadAll()
        vm.snackbar.collect { snackHost.showSnackbar(it) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackHost) }) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {

                item {
                    ProfileHeader(
                        state.profile,
                        state.avatarUploading,
                        onAvatarClick = { showAvatarOptions = true },
                        onLogout = { vm.logout(); onLogout() }
                    )
                }

                item {
                    val role = state.profile?.role
                    if (role == "ADMIN" || role == "MODERATOR") {
                        Button(onClick = { navController.navigate("staff") },
                            modifier = Modifier.fillMaxWidth()) {
                            Text("🛡️ Open Staff Dashboard")
                        }
                    }
                }

                item { StatsRow(state.profile?.stats) }

                item { SectionTitle("Appearance") }
                item {
                    val mode by vm.themeMode.collectAsStateWithLifecycle(initialValue = ThemeStore.SYSTEM)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChip("System", ThemeStore.SYSTEM, mode, vm::setThemeMode)
                        ThemeChip("Light", ThemeStore.LIGHT, mode, vm::setThemeMode)
                        ThemeChip("Dark", ThemeStore.DARK, mode, vm::setThemeMode)
                    }
                }

                item { SectionTitle("Achievements") }
                item { AchievementGrid(state.profile?.achievements.orEmpty()) }
            }
        }
    }

    if (showAvatarOptions) {
        AlertDialog(
            onDismissRequest = { showAvatarOptions = false },
            title = { Text("Profile picture") },
            text = {
                Column {
                    TextButton(onClick = {
                        showAvatarOptions = false
                        avatarPicker.launch("image/*")
                    }) { Text("Choose new picture") }
                    if (state.profile?.avatarUrl != null) {
                        TextButton(onClick = {
                            showAvatarOptions = false
                            vm.removeAvatar()
                        }) {
                            Text("Remove picture", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAvatarOptions = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProfileHeader(p: ProfileDto?, uploading: Boolean, onAvatarClick: () -> Unit, onLogout: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.clickable(onClick = onAvatarClick)) {
                Avatar(p?.avatarUrl, p?.username ?: "?")
                if (uploading) {
                    CircularProgressIndicator(Modifier.size(52.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Person, "Change avatar",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp).align(Alignment.BottomEnd).offset(4.dp, 4.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape).padding(2.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(p?.username ?: "…", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val roleLabel = when (p?.role) {
                        "MODERATOR" -> "MOD"
                        else -> p?.role ?: ""
                    }
                    AssistChip(onClick = {}, label = { Text(roleLabel, maxLines = 1) })
                    AssistChip(onClick = {}, label = { Text("${p?.xp ?: 0} XP", maxLines = 1) })
                }
            }
            TextButton(onClick = onLogout) { Text("Log out") }
        }
    }
}

@Composable
private fun ThemeChip(label: String, value: String, selectedMode: String, onPick: (String) -> Unit) {
    FilterChip(selected = selectedMode == value,
        onClick = { onPick(value) },
        label = { Text(label) })
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

@Composable
private fun Avatar(url: String?, fallback: String) {
    if (url != null) {
        AsyncImage(model = url, contentDescription = null,
            modifier = Modifier.size(52.dp).clip(CircleShape))
    } else {
        Box(Modifier.size(52.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center) {
            Text(fallback.take(1).uppercase(), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatsRow(s: StatsDto?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCell("${s?.totalCards ?: 0}", "Cards", Modifier.weight(1f))
        StatCell("${s?.photoCards ?: 0}", "Photo", Modifier.weight(1f))
        StatCell("${s?.sharedCards ?: 0}", "Shared", Modifier.weight(1f))
        StatCell("${s?.friendsCount ?: 0}", "Friends", Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AchievementGrid(list: List<AchievementDto>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { AchievementItem(it) }
    }
}

@Composable
private fun AchievementItem(a: AchievementDto) {
    Card(modifier = Modifier.width(160.dp).height(122.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (a.unlocked) Color(0xFFFFF6D9)
            else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BadgeIcon(unlocked = a.unlocked)
            Text(a.name, style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (a.unlocked) Color(0xFF7A5C00) else Color.Gray)
            Text(a.description, style = MaterialTheme.typography.labelSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (a.unlocked) Color(0xFF8D6E00) else Color.Gray)
        }
    }
}

@Composable
private fun BadgeIcon(unlocked: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.size(40.dp)) {
        val main = if (unlocked) Color(0xFFF5C518) else Color(0xFFB0B0B0)
        val accent = if (unlocked) Color(0xFF7A5C00) else Color(0xFF707070)
        drawCircle(color = main)
        drawCircle(color = Color.White, style = Stroke(2.dp.toPx()),
            radius = size.minDimension / 2 - 3.dp.toPx())
        val r = size.minDimension / 4
        val path = Path().apply {
            moveTo(center.x, center.y - r); lineTo(center.x + r, center.y)
            lineTo(center.x, center.y + r); lineTo(center.x - r, center.y); close()
        }
        drawPath(path, accent)
    }
}