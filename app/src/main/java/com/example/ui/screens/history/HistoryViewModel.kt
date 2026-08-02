package com.example.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.TransferEntity
import com.example.data.repository.TransferRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val transferRepository: TransferRepository
) : ViewModel() {

    private val _historyLogs = MutableStateFlow<List<TransferEntity>>(emptyList())
    val historyLogs: StateFlow<List<TransferEntity>> = _historyLogs.asStateFlow()

    init {
        viewModelScope.launch {
            transferRepository.allTransfers.collect { logs ->
                _historyLogs.value = logs
            }
        }
    }

    fun clearAllHistory() {
        _historyLogs.value = emptyList()
        viewModelScope.launch {
            transferRepository.clearHistory()
        }
    }

    fun deleteItem(id: String) {
        _historyLogs.value = _historyLogs.value.filter { it.id != id }
        viewModelScope.launch {
            transferRepository.deleteTransfer(id)
        }
    }
}
