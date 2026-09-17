package com.noven.ncrawler.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.scraper.SourcePreferences
import com.noven.ncrawler.data.scraper.SourceRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SourceUiItem(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val enabled: Boolean,
    val isDefault: Boolean   // true for the highest-priority enabled source
)

data class SourceSettingsUiState(
    val items: List<SourceUiItem> = emptyList(),
    val message: String? = null   // transient — e.g. "Can't disable your only source"
)

class SourceSettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo  = (app as NCrawlerApp).repository
    private val prefs: SourcePreferences = repo.sourcePreferences()

    private val _uiState = MutableStateFlow(SourceSettingsUiState())
    val uiState: StateFlow<SourceSettingsUiState> = _uiState.asStateFlow()

    init { refresh() }

    private fun refresh() {
        val order = prefs.getPriorityOrder()
        val all   = SourceRegistry.all()

        // Enabled ones first, in their saved priority order; anything not in
        // the saved list (disabled, or newly added to the registry) after,
        // in registry order.
        val enabled  = order.mapNotNull { id -> all.find { it.id == id } }
        val disabled = all.filter { it.id !in order }

        val items = (enabled + disabled).mapIndexed { _, source ->
            SourceUiItem(
                id          = source.id,
                displayName = source.displayName,
                baseUrl     = source.baseUrl,
                enabled     = source.id in order,
                isDefault   = order.firstOrNull() == source.id
            )
        }
        _uiState.value = _uiState.value.copy(items = items)
    }

    fun toggle(sourceId: String, enabled: Boolean) {
        viewModelScope.launch {
            val ok = prefs.setEnabled(sourceId, enabled)
            if (!ok) {
                _uiState.value = _uiState.value.copy(
                    message = "At least one source has to stay enabled"
                )
            }
            refresh()
        }
    }

    fun moveUp(sourceId: String) {
        viewModelScope.launch { prefs.moveUp(sourceId); refresh() }
    }

    fun moveDown(sourceId: String) {
        viewModelScope.launch { prefs.moveDown(sourceId); refresh() }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
