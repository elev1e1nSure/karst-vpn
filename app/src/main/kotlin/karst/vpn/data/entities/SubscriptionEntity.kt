package karst.vpn.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val url: String,
    val displayName: String,
    val announce: String? = null,
    val profileUpdateIntervalHours: Int? = null,
    val profileWebPageUrl: String? = null,
    val routingEnabled: Boolean? = null,
    val uploadBytes: Long? = null,
    val downloadBytes: Long? = null,
    val totalBytes: Long? = null,
    val expireAtEpochSeconds: Long? = null,
    val lastRefreshedAtEpochMs: Long?,
    val lastRefreshError: String?,
    val createdAtEpochMs: Long,
)
