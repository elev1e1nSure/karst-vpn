package karst.vpn.data.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import karst.vpn.data.entities.ServerEntity
import kotlinx.coroutines.flow.Flow

data class ServerWithSubscription(
    @Embedded val server: ServerEntity,
    @ColumnInfo(name = "subscriptionName") val subscriptionName: String?,
    @ColumnInfo(name = "subscriptionAnnounce") val subscriptionAnnounce: String?,
)

@Dao
interface ServerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(server: ServerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(servers: List<ServerEntity>)

    @Query(
        """
        SELECT servers.*,
            subscriptions.displayName AS subscriptionName,
            subscriptions.announce AS subscriptionAnnounce
        FROM servers
        LEFT JOIN subscriptions ON subscriptions.id = servers.subscriptionId
        ORDER BY servers.sortOrder ASC, servers.addedAtEpochMs ASC
        """,
    )
    fun observeAllWithSubscriptions(): Flow<List<ServerWithSubscription>>

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getById(id: String): ServerEntity?

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM servers WHERE subscriptionId = :subscriptionId")
    suspend fun deleteBySubscriptionId(subscriptionId: String)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM servers")
    suspend fun maxSortOrder(): Int

    @Query(
        """
        UPDATE servers
        SET latencyStatus = :status,
            latencyMs = :latencyMs,
            latencyMeasuredAtEpochMs = :measuredAt
        WHERE id = :id
        """,
    )
    suspend fun updateLatency(id: String, status: String, latencyMs: Long?, measuredAt: Long?)
}
