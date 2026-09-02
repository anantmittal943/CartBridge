package com.anantmittal.cartbridge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.anantmittal.cartbridge.data.CartDao
import com.anantmittal.cartbridge.domain.HybridExtractionRepository

class MainViewModelFactory(
    private val repository: HybridExtractionRepository,
    private val cartDao: CartDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, cartDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
