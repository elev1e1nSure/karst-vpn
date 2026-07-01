package karst.vpn.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "servers",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["subscriptionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("subscriptionId")],
)
data class ServerEntity(
    @PrimaryKey val id: String,
    val subscriptionId: String?,
    val displayName: String,
    val rawLink: String,
    val outboundConfigJson: String,
    val host: String,
    val port: Int,
    val sortOrder: Int,
    val latencyMs: Long?,
    val latencyStatus: String,
    val latencyMeasuredAtEpochMs: Long?,
    val addedAtEpochMs: Long,
)
