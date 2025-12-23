package com.fattyleo.networkinfoapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fattyleo.networkinfoapp.data.NetworkInfoRepository
import com.fattyleo.networkinfoapp.model.NetworkInfo
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class NetworkInfoViewModel(
    private val networkInfoRepository: NetworkInfoRepository // Changed name
) : ViewModel() {
    val networkInfo: StateFlow<NetworkInfo> = networkInfoRepository.networkInfo // Changed name
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            NetworkInfo()
        )
    fun startNetworkUpdates() {
        networkInfoRepository.startListening() // Changed name
    }
    fun stopNetworkUpdates() {
        networkInfoRepository.stopListening() // Changed name
    }
    class Factory(private val networkInfoRepository: NetworkInfoRepository) : ViewModelProvider.Factory { // Changed name
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NetworkInfoViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return NetworkInfoViewModel(networkInfoRepository) as T // Changed name
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

