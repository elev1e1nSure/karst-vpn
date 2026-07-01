package karst.vpn.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val url: String,
    val displayName: String,
    val lastRefreshedAtEpochMs: Long?,
    val lastRefreshError: String?,
    val createdAtEpochMs: Long,
)
