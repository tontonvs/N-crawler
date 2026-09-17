package com.noven.ncrawler.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noven.ncrawler.ui.theme.AccentBlue
import com.noven.ncrawler.ui.theme.GlassBorderLight
import com.noven.ncrawler.ui.theme.GlassSurfaceLight
import com.noven.ncrawler.viewmodel.SourceSettingsViewModel
import com.noven.ncrawler.viewmodel.SourceUiItem
import kotlinx.coroutines.delay

@Composable
fun SourceSettingsScreen(
    onBack: () -> Unit,
    vm: SourceSettingsViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    // Same transient-message pattern as DetailScreen's updateMessage — show
    // once, then clear, rather than leaving a stale snackbar re-shown on
    // every recomposition.
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHost.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost   = { SnackbarHost(snackbarHost) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    "Sources",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight    = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier            = Modifier.padding(padding),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Novels are pulled from the highest-priority source that has them. Reorder or turn sources off below — at least one has to stay on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
            }

            items(state.items, key = { it.id }) { item ->
                SourceRow(
                    item     = item,
                    onToggle = { enabled -> vm.toggle(item.id, enabled) },
                    onUp     = { vm.moveUp(item.id) },
                    onDown   = { vm.moveDown(item.id) }
                )
            }
        }
    }
}

@Composable
private fun SourceRow(
    item: SourceUiItem,
    onToggle: (Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurfaceLight)
            .border(1.dp, GlassBorderLight, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {

            // Reorder arrows — only meaningful (and shown) for enabled sources
            if (item.enabled) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onUp, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Move up",
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDown, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Move down",
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
            } else {
                Spacer(Modifier.width(34.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.displayName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color    = if (item.enabled) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.isDefault) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(AccentBlue.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "DEFAULT",
                                color         = AccentBlue,
                                fontSize      = 9.sp,
                                fontWeight    = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
                Text(
                    item.baseUrl,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = item.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor  = Color.White,
                    checkedTrackColor  = AccentBlue
                )
            )
        }
    }
}
