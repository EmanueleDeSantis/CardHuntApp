package com.cardhunt.app.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class CardSort(val label: String) { NAME("Name A–Z"), RARITY("Rarity"), DATE("Newest") }
enum class UserSort(val label: String) { NAME("Name A–Z"), XP("XP"), CARDS("Cards") }

fun rarityRank(rarity: String): Int = when (rarity) {
    "LEGENDARY" -> 4
    "EPIC" -> 3
    "RARE" -> 2
    else -> 1
}

@Composable
fun <T> SortSelector(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) })
        }
    }
}