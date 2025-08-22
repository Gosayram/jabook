package com.jabook.app.core.data.network.model

/** Search result item from RuTracker */
data class AudiobookSearchResult(
  val id: String,
  val title: String,
  val author: String,
  val narrator: String? = null,
  val duration: String? = null,
  val size: String,
  val seeds: Int,
  val leeches: Int,
  val category: String,
  val subcategory: String,
  val uploadDate: Long,
  val magnetLink: String,
)

/** Detailed torrent information from RuTracker */
data class RuTrackerTorrent(
  val id: String,
  val title: String,
  val author: String,
  val narrator: String? = null,
  val description: String? = null,
  val duration: String? = null,
  val size: String,
  val seeds: Int,
  val leeches: Int,
  val category: String,
  val subcategory: String,
  val uploadDate: Long,
  val magnetLink: String,
  val language: String,
  val year: Int?,
  val coverImageUrl: String?,
)

/** Torrent file information */
data class TorrentFile(
  val path: String,
  val size: Long,
  val priority: Int = 1,
)

/** Torrent metadata information */
data class TorrentInfo(
  val name: String,
  val totalSize: Long,
  val files: List<TorrentFile>,
  val hash: String,
)
