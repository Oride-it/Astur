package com.oriftts.astur.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oriftts.astur.data.StatsRepository
import com.oriftts.astur.data.TokenRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatsUiState(
    val history: List<TokenRecord> = emptyList()
)

/** Token 统计页状态：历史记录持久化；人物设定移至独立子页面。 */
class StatsViewModel(stats: StatsRepository) : ViewModel() {

    private val _ui = MutableStateFlow(StatsUiState())
    val ui: StateFlow<StatsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            stats.history.collect { _ui.value = _ui.value.copy(history = it) }
        }
    }
}
