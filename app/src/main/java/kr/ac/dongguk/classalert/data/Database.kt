package kr.ac.dongguk.classalert.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter fun fromType(value: EventType) = value.name
    @TypeConverter fun toType(value: String) = EventType.valueOf(value)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE hidden = 0 ORDER BY startsAt") fun observeAll(): Flow<List<EventEntity>>
    @Query("SELECT * FROM events WHERE hidden = 0 AND startsAt BETWEEN :from AND :to ORDER BY startsAt") fun observeRange(from: Long, to: Long): Flow<List<EventEntity>>
    @Query("SELECT * FROM events") suspend fun all(): List<EventEntity>
    @Query("SELECT * FROM events WHERE uid = :uid") suspend fun byId(uid: String): EventEntity?
    @Upsert suspend fun upsert(event: EventEntity)
    @Query("UPDATE events SET hidden = :hidden WHERE uid = :uid") suspend fun setHidden(uid: String, hidden: Boolean)
    @Query("UPDATE events SET notificationEnabled = :enabled WHERE uid = :uid") suspend fun setNotification(uid: String, enabled: Boolean)
}

@Database(entities = [EventEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun events(): EventDao
    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "class-alert.db").build().also { instance = it }
        }
    }
}

