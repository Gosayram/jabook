package com.jabook.app.jabook.compose.domain.model

import androidx.compose.runtime.Immutable

@Immutable
public data class SearchHistoryItem(
    val id: Int = 0,
    val query: String,
    val timestamp: Long = System.currentTimeMillis(),
    val resultCount: Int = 0,
)
