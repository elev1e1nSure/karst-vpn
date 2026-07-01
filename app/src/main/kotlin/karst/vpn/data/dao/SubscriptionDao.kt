package karst.vpn.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import karst.vpn.data.entities.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(subscription: SubscriptionEntity)

    @Update
    suspend fun update(subscription: SubscriptionEntity)

    @Delete
    suspend fun delete(subscription: SubscriptionEntity)

    @Query("SELECT * FROM subscriptions ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getById(id: String): SubscriptionEntity?

    @Query(
        """
        UPDATE subscriptions
        SET lastRefreshedAtEpochMs = :refreshedAt,
            lastRefreshError = :error
        WHERE id = :id
        """,
    )
    suspend fun updateRefreshResult(id: String, refreshedAt: Long?, error: String?)
}
