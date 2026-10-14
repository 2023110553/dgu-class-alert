package kr.ac.dongguk.classalert.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class EventType { ASSIGNMENT, LECTURE_START, LECTURE_END, EXAM, OTHER }

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val uid: String,
    val title: String,
    val description: String,
    val category: String?,
    val type: EventType,
    val startsAt: Long,
    val endsAt: Long?,
    val lastModified: Long?,
    val hidden: Boolean = false,
    val notificationEnabled: Boolean = true,
    val syncedAt: Long
)

data class ParsedEvent(
    val uid: String,
    val title: String,
    val description: String,
    val category: String?,
    val type: EventType,
    val startsAt: Long,
    val endsAt: Long?,
    val lastModified: Long?
)

data class SyncState(val lastSuccess: Long?, val lastError: String?)

