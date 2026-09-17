package com.cardhunt.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Shared "center the map on my location" button — used by the main map and the placement map. */
@Composable
fun RecenterButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier
    ) {
        Icon(Icons.Filled.LocationOn, contentDescription = "Center on my location")
    }
}