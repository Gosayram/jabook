package com.jabook.app.jabook.compose.domain.model

import androidx.compose.runtime.Immutable

@Immutable
public data class FavoriteItem(
    val topicId: String,
    val title: String,
    val author: String,
    val category: String,
    val size: String,
    val seeders: Int = 0,
    val leechers: Int = 0,
    val magnetUrl: String,
    val coverUrl: String? = null,
    val performer: String? = null,
    val genres: String? = null,
    val addedDate: String,
    val addedToFavorites: String,
    val duration: String? = null,
    val bitrate: String? = null,
    val audioCodec: String? = null,
)
