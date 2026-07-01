package karst.vpn.data.dao

import androidx.room.Dao
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

    @Query("SELECT * FROM subscriptions ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getById(id: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE url = :url")
    suspend fun getByUrl(url: String): SubscriptionEntity?

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT id FROM subscriptions")
    suspend fun getAllIds(): List<String>

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
