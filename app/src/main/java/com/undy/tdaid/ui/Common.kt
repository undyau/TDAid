package com.undy.tdaid.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
inline fun <reified VM : ViewModel> rememberViewModel(crossinline create: () -> VM): VM {
    return viewModel(factory = viewModelFactory { initializer { create() } })
}

fun formatRelative(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val minutes = ((nowMillis - epochMillis) / 60000).coerceAtLeast(0)
    return if (minutes < 1) "just now" else "$minutes min ago"
}
