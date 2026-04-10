package com.jabook.app.jabook.compose.domain.model

import androidx.compose.runtime.Immutable

@Immutable
public data class SearchHistoryItem(
    val id: Long = 0L,
    val query: String,
    val timestamp: Long,
    val resultCount: Int = 0,
)