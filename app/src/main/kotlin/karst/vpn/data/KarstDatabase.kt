package karst.vpn.data

import androidx.room.Database
import androidx.room.RoomDatabase
import karst.vpn.data.dao.ServerDao
import karst.vpn.data.dao.SubscriptionDao
import karst.vpn.data.entities.ServerEntity
import karst.vpn.data.entities.SubscriptionEntity

@Database(
    entities = [SubscriptionEntity::class, ServerEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class KarstDatabase : RoomDatabase() {
    abstract fun subscriptions(): SubscriptionDao
    abstract fun servers(): ServerDao
}
