package cn.coindecision.app.data

import android.content.Context
import androidx.room.*
import cn.coindecision.app.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import java.util.UUID

@Entity(tableName = "draft") data class DraftRow(@PrimaryKey val id: Int = 1, val payload: String)
@Entity(tableName = "history", indices = [Index("decisionId")])
data class HistoryRow(@PrimaryKey val tossId: String, val decisionId: String, val createdAt: Long, val payload: String)
@Dao interface CoinDao {
    @Query("SELECT * FROM draft WHERE id=1") suspend fun draft(): DraftRow?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDraft(row: DraftRow)
    @Query("SELECT * FROM history ORDER BY createdAt DESC") fun history(): Flow<List<HistoryRow>>
    @Query("SELECT * FROM history WHERE tossId=:id") suspend fun find(id: String): HistoryRow?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(row: HistoryRow)
    @Query("UPDATE history SET payload=:payload WHERE tossId=:id") suspend fun updateFeedback(id: String, payload: String)
    @Query("DELETE FROM history WHERE tossId=:id") suspend fun delete(id: String)
    @Query("DELETE FROM history") suspend fun clear()
}
@Database(entities = [DraftRow::class, HistoryRow::class], version = 1, exportSchema = true)
abstract class CoinDatabase : RoomDatabase() {
    abstract fun dao(): CoinDao
    companion object {
        fun open(context: Context) = Room.databaseBuilder(context, CoinDatabase::class.java, "coin.db").build()
        // 数据库版本提升必须添加非破坏性迁移，禁止 fallbackToDestructiveMigration。
    }
}

class CoinRepository(private val db: CoinDatabase, private val engine: CoinTossEngine = CoinTossEngine()) {
    private val gate = Mutex()
    private val dao = db.dao()
    val history = dao.history()
    private var pending: TossSnapshot? = null
    suspend fun load(): Session = gate.withLock { dao.draft()?.let { codec.decodeFromString<Session>(it.payload) } ?: Session() }
    suspend fun save(session: Session) = gate.withLock {
        val previous = dao.draft()?.let { codec.decodeFromString<Session>(it.payload) }
        if (previous == null || session.revision >= previous.revision) dao.putDraft(DraftRow(payload = codec.encodeToString(session)))
    }
    suspend fun toss(session: Session): TossSnapshot = gate.withLock {
        val result = db.withTransaction {
            val persisted = dao.draft()?.let { codec.decodeFromString<Session>(it.payload) }
            require(persisted == null || session.revision >= persisted.revision ||
                (persisted.decisionId == session.decisionId && persisted.activeTossId != null)) { "草稿已改变，请返回重试" }
            val current = if (persisted?.decisionId == session.decisionId && persisted.revision > session.revision && persisted.activeTossId != null) persisted else session
            current.activeTossId?.let { id ->
                val row = dao.find(id) ?: error("已删除此结果，请开始新决策")
                return@withTransaction codec.decodeFromString<TossSnapshot>(row.payload)
            }
            require(current.stage == Stage.READY) { "当前不能投币" }
            Contract.request(current.request())
            require(!SafetyGate.blocked(current.request())) { SafetyGate.message }
            val probability = CoinProbabilityCalculator().calculate(PreferenceScorer().score(current.questions, current.answers))
            val oldPending = pending
            val snapshot = if (oldPending != null && oldPending.session == current) oldPending else TossSnapshot(
                UUID.randomUUID().toString(), current, probability, engine.toss(probability.pA), System.currentTimeMillis(), current.tossCount + 1
            ).also { pending = it }
            dao.insert(HistoryRow(snapshot.tossId, current.decisionId, snapshot.createdAt, codec.encodeToString(snapshot)))
            dao.putDraft(DraftRow(payload = codec.encodeToString(current.copy(stage = Stage.RESULT,
                activeTossId = snapshot.tossId, tossCount = snapshot.number, revision = current.revision + 1))))
            snapshot
        }
        pending = null
        result
    }
    suspend fun find(id: String): TossSnapshot? = dao.find(id)?.let { codec.decodeFromString<TossSnapshot>(it.payload) }
    suspend fun delete(id: String): Session = gate.withLock {
        db.withTransaction {
            dao.delete(id)
            val current = dao.draft()?.let { codec.decodeFromString<Session>(it.payload) } ?: Session()
            val next = if (current.activeTossId == id) Session(revision = current.revision + 1) else current
            dao.putDraft(DraftRow(payload = codec.encodeToString(next)))
            next
        }
    }
    suspend fun clear(): Session = gate.withLock {
        db.withTransaction {
            dao.clear()
            val current = dao.draft()?.let { codec.decodeFromString<Session>(it.payload) } ?: Session()
            val next = Session(revision = current.revision + 1)
            dao.putDraft(DraftRow(payload = codec.encodeToString(next)))
            next
        }
    }
    suspend fun feedback(id: String, text: String) = gate.withLock {
        require(text in listOf("松了一口气", "有点失望", "没什么感觉"))
        dao.find(id)?.let { row ->
            val snapshot = codec.decodeFromString<TossSnapshot>(row.payload).copy(feedback = text)
            dao.updateFeedback(id, codec.encodeToString(snapshot))
        }
    }
}
