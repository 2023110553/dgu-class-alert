package kr.ac.dongguk.classalert.data

import androidx.room.withTransaction
import kr.ac.dongguk.classalert.notification.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class EventRepository(
    private val db: AppDatabase,
    private val client: OkHttpClient,
    private val settings: SecureSettings,
    private val alarms: AlarmScheduler
) {
    fun upcoming(days: Long = 14): Flow<List<EventEntity>> = db.events().observeRange(System.currentTimeMillis(), System.currentTimeMillis() + days * 86_400_000)
    fun all(): Flow<List<EventEntity>> = db.events().observeAll()
    fun hidden(): Flow<List<EventEntity>> = db.events().observeHidden()

    suspend fun sync(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val url = settings.calendarUrl ?: error("설정에서 eClass 캘린더 URL을 등록해 주세요.")
            require(url.startsWith("https://eclass.dongguk.edu/calendar/export_execute.php")) { "동국대학교 eClass 캘린더 내보내기 URL만 사용할 수 있습니다." }
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            response.use {
                require(it.isSuccessful) {
                    if (it.code == 401 || it.code == 403) "캘린더 토큰이 만료되었거나 유효하지 않습니다. eClass에서 URL을 다시 발급해 주세요." else "서버 응답 오류: ${it.code}"
                }
                val raw = it.body.string()
                require(raw.contains("BEGIN:VCALENDAR")) { "캘린더 토큰이 만료되었거나 응답이 올바르지 않습니다. eClass에서 URL을 다시 발급해 주세요." }
                IcsParser.parse(raw)
            }
        }.mapCatching { parsed ->
            val now = System.currentTimeMillis()
            db.withTransaction {
                parsed.forEach { p ->
                    val old = db.events().byId(p.uid)
                    val entity = EventEntity(p.uid, p.title, p.description, p.category, p.type, p.startsAt, p.endsAt, p.lastModified,
                        hidden = old?.hidden ?: false, notificationEnabled = old?.notificationEnabled ?: true, syncedAt = now)
                    if (old != null && (old.startsAt != entity.startsAt || old.type != entity.type)) alarms.cancel(old)
                    db.events().upsert(entity)
                    alarms.schedule(entity, settings)
                }
            }
            settings.lastSuccess = now; settings.lastError = null
            parsed.size
        }.onFailure { settings.lastError = it.message ?: "동기화 실패" }
    }

    suspend fun restoreAlarms() = db.events().all().forEach { alarms.schedule(it, settings) }
    suspend fun hide(event: EventEntity) { db.events().setHidden(event.uid, true); alarms.cancel(event) }
    suspend fun restore(event: EventEntity) {
        db.events().setHidden(event.uid, false)
        alarms.schedule(event.copy(hidden = false), settings)
    }
}

