package com.kascorp.webhooknotesender.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kascorp.webhooknotesender.data.local.entity.QueueItemEntity
import com.kascorp.webhooknotesender.data.local.entity.QueueStatus
import com.kascorp.webhooknotesender.data.repository.QueueRepository
import com.kascorp.webhooknotesender.work.QueueWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val queueRepository: QueueRepository
) : ViewModel() {

    val queueItems: StateFlow<List<QueueItemEntity>> = queueRepository.getAllItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingCount: StateFlow<Int> = queueRepository.getPendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun deleteItem(item: QueueItemEntity) {
        viewModelScope.launch {
            queueRepository.delete(item)
        }
    }

    fun deleteItemById(id: Long) {
        viewModelScope.launch {
            queueRepository.deleteById(id)
        }
    }

    fun retryItem(item: QueueItemEntity) {
        viewModelScope.launch {
            queueRepository.updateStatus(
                id = item.id,
                status = QueueStatus.PENDING.name,
                attempts = item.attempts,
                lastError = null
            )
        }
    }

    fun retryAllFailed() {
        viewModelScope.launch {
            val items = queueItems.value
            val failedItems = items.filter { it.status == QueueStatus.FAILED.name }
            for (item in failedItems) {
                queueRepository.updateStatus(
                    id = item.id,
                    status = QueueStatus.PENDING.name,
                    attempts = item.attempts,
                    lastError = null
                )
            }
            // Trigger queue worker
            if (failedItems.isNotEmpty()) {
                android.util.Log.d("QueueViewModel", "Retrying ${failedItems.size} failed items")
            }
        }
    }
}
