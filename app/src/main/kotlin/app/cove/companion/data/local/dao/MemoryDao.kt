package app.cove.companion.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.cove.companion.data.local.entity.MemoryEntity

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity)

    /** Memories that still answer questions, newest first. */
    @Query("SELECT * FROM memories WHERE active = 1 AND (expiresAt IS NULL OR expiresAt > :now) ORDER BY createdAt DESC")
    suspend fun live(now: Long): List<MemoryEntity>

    /** Ids of the active memories about [subject], so a newer one can replace them. */
    @Query("SELECT id FROM memories WHERE subject = :subject AND active = 1 AND id != :except")
    suspend fun activeIds(subject: String, except: String): List<String>

    @Query("UPDATE memories SET active = :active WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)
}
