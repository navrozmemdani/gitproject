package com.visualtrace.ai.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history", indices = [Index("createdAt")])
data class HistoryEntity(
    @PrimaryKey val searchId: String,
    val type: String,
    val status: String,
    val createdAt: Long,
    val resultCount: Int,
    val title: String?,
    val thumbnailPath: String?,
)

@Entity(tableName = "cached_results")
data class CachedResultsEntity(
    @PrimaryKey val searchId: String,
    val json: String,
    val cachedAt: Long,
)

@Entity(tableName = "collections", indices = [Index(value = ["name"], unique = true)])
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "saved_results",
    indices = [Index("collectionId"), Index(value = ["resultId"], unique = true)],
)
data class SavedResultEntity(
    @PrimaryKey val id: String,
    val resultId: String,
    val searchId: String,
    val json: String,
    val collectionId: String?,
    val note: String?,
    val savedAt: Long,
    val remoteId: String?,
)

/** Server-side deletions that could not be performed offline; retried by a worker. */
@Entity(tableName = "pending_remote_deletions")
data class PendingDeletionEntity(
    @PrimaryKey val key: String, // "history:<id>", "history:*" or "favorite:<remoteId>"
    val createdAt: Long,
)

data class CollectionWithCount(val id: String, val name: String, val createdAt: Long, val itemCount: Int)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE (:type IS NULL OR type = :type) ORDER BY createdAt DESC")
    fun observe(type: String?): Flow<List<HistoryEntity>>

    @Upsert
    suspend fun upsert(entity: HistoryEntity)

    @Query("SELECT * FROM history WHERE searchId = :id")
    suspend fun get(id: String): HistoryEntity?

    @Query("DELETE FROM history WHERE searchId = :id")
    suspend fun delete(id: String)

    @Query("SELECT thumbnailPath FROM history WHERE thumbnailPath IS NOT NULL")
    suspend fun thumbnailPaths(): List<String>

    @Query("DELETE FROM history")
    suspend fun clear()

    @Upsert
    suspend fun cacheResults(entity: CachedResultsEntity)

    @Query("SELECT * FROM cached_results WHERE searchId = :id")
    suspend fun cachedResults(id: String): CachedResultsEntity?

    @Query("DELETE FROM cached_results WHERE searchId = :id")
    suspend fun deleteCachedResults(id: String)

    @Query("DELETE FROM cached_results")
    suspend fun clearCachedResults()
}

@Dao
interface FavoritesDao {
    @Query("SELECT * FROM saved_results WHERE (:collectionId IS NULL OR collectionId = :collectionId) ORDER BY savedAt DESC")
    fun observe(collectionId: String?): Flow<List<SavedResultEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_results WHERE resultId = :resultId)")
    fun isSaved(resultId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SavedResultEntity)

    @Query("SELECT * FROM saved_results WHERE id = :id")
    suspend fun get(id: String): SavedResultEntity?

    @Query("SELECT * FROM saved_results WHERE remoteId IS NULL")
    suspend fun unsynced(): List<SavedResultEntity>

    @Query("SELECT remoteId FROM saved_results WHERE remoteId IS NOT NULL")
    suspend fun remoteIds(): List<String>

    @Query("UPDATE saved_results SET note = :note WHERE id = :id")
    suspend fun updateNote(id: String, note: String?)

    @Query("UPDATE saved_results SET collectionId = :collectionId WHERE id = :id")
    suspend fun move(id: String, collectionId: String?)

    @Query("UPDATE saved_results SET remoteId = :remoteId WHERE id = :id")
    suspend fun setRemoteId(id: String, remoteId: String)

    @Query("DELETE FROM saved_results WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        "SELECT c.id, c.name, c.createdAt, (SELECT COUNT(*) FROM saved_results s WHERE s.collectionId = c.id) AS itemCount " +
            "FROM collections c ORDER BY c.name COLLATE NOCASE",
    )
    fun observeCollections(): Flow<List<CollectionWithCount>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCollection(entity: CollectionEntity)

    @Query("UPDATE saved_results SET collectionId = NULL WHERE collectionId = :id")
    suspend fun detachCollection(id: String)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollection(id: String)
}

@Dao
interface PendingDeletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: PendingDeletionEntity)

    @Query("SELECT * FROM pending_remote_deletions ORDER BY createdAt")
    suspend fun all(): List<PendingDeletionEntity>

    @Query("DELETE FROM pending_remote_deletions WHERE `key` = :key")
    suspend fun remove(key: String)
}

@Database(
    entities = [
        HistoryEntity::class, CachedResultsEntity::class, CollectionEntity::class, SavedResultEntity::class,
        PendingDeletionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class VisualTraceDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun pendingDeletionDao(): PendingDeletionDao
}
