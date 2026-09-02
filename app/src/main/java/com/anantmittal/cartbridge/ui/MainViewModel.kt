package com.anantmittal.cartbridge.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantmittal.cartbridge.data.CartDao
import com.anantmittal.cartbridge.data.CartItem
import com.anantmittal.cartbridge.domain.HybridExtractionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: HybridExtractionRepository,
    private val cartDao: CartDao
) : ViewModel() {

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // Expose all items from Room directly
    val allItems: StateFlow<List<CartItem>> = cartDao.getAllItems()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun processImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _isProcessing.value = true
            val parsedItems = repository.processCartImage(bitmap)
            if (parsedItems.isNotEmpty()) {
                cartDao.insertItems(parsedItems)
            }
            _isProcessing.value = false
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            cartDao.clearHistory()
        }
    }
}
