package com.jabook.app.core.database.converters

import androidx.room.TypeConverter
import com.jabook.app.core.database.entities.DownloadStatus
import java.util.Date

/** Type converters for Room database to handle custom types. */
class DatabaseConverters {
  /** Convert Date to Long timestamp. */
  @TypeConverter
  fun fromDate(date: Date?): Long? = date?.time

  /** Convert Long timestamp to Date. */
  @TypeConverter
  fun toDate(timestamp: Long?): Date? = timestamp?.let { Date(it) }

  /** Convert DownloadStatus enum to String. */
  @TypeConverter
  fun fromDownloadStatus(status: DownloadStatus): String = status.name

  /** Convert String to DownloadStatus enum. */
  @TypeConverter
  fun toDownloadStatus(status: String): DownloadStatus = DownloadStatus.valueOf(status)
}
