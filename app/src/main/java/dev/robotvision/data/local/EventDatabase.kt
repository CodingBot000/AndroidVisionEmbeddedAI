package dev.robotvision.data.local

import androidx.room.*
import dev.robotvision.domain.Event
import dev.robotvision.domain.EventRepository
import kotlinx.coroutines.flow.map

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long, val type: String, val label: String?, val score: Float?,
    val backend: String, val latencyMs: Double?, val detail: String, val simulated: Boolean,
) {
    fun domain() = Event(id, time, type, label, score, backend, latencyMs, detail, simulated)
}
@Dao interface EventDao {
    @Query("SELECT * FROM events ORDER BY time DESC, id DESC") fun observe(): kotlinx.coroutines.flow.Flow<List<EventEntity>>
    @Insert suspend fun insert(event: EventEntity)
    @Query("DELETE FROM events") suspend fun clear()
}
@Database(entities = [EventEntity::class], version = 1, exportSchema = true)
abstract class EventDatabase : RoomDatabase() { abstract fun events(): EventDao }
class RoomEventRepository(private val dao: EventDao) : EventRepository {
    override val events = dao.observe().map { rows -> rows.map(EventEntity::domain) }
    override suspend fun save(event: Event) = dao.insert(EventEntity(
        time = event.time, type = event.type, label = event.label, score = event.score,
        backend = event.backend, latencyMs = event.latencyMs, detail = event.detail, simulated = event.simulated,
    ))
    override suspend fun clear() = dao.clear()
}
