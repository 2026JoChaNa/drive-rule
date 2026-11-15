package com.jochana.driverule.core.data.local.law

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Cache storage is deliberately separate from profiles and driver-local state. */
@Database(entities = [LawCardEntity::class, LawCacheStateEntity::class], version = 1, exportSchema = true)
abstract class LawCardDatabase : RoomDatabase() {
    internal abstract fun lawCardDao(): LawCardDao

    companion object {
        fun create(context: Context): LawCardDatabase = Room.databaseBuilder(
            context.applicationContext, LawCardDatabase::class.java, "drive-rule-law-cache.db",
        ).build()
    }
}

@Entity(tableName = "cached_law_card", indices = [Index("topic"), Index("cache_id")])
internal data class LawCardEntity(
    @PrimaryKey @ColumnInfo(name = "card_id") val id: Long,
    val version: Int,
    val topic: String?,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "is_retired") val isRetired: Boolean,
    @ColumnInfo(name = "is_supported") val isSupported: Boolean,
    @ColumnInfo(name = "verified_at") val verifiedAtMillis: Long?,
    @ColumnInfo(name = "cache_id") val cacheId: Int = 1,
) {
    fun record() = LawCardRecord(id, version, topic, payloadJson, isRetired, isSupported, verifiedAtMillis)
}

@Entity(tableName = "law_cache_state")
internal data class LawCacheStateEntity(
    @PrimaryKey @ColumnInfo(name = "singleton_id") val singletonId: Int = 1,
    @ColumnInfo(name = "sync_token") val syncToken: String,
    @ColumnInfo(name = "last_successful_sync_at_millis") val lastSuccessfulSyncAtMillis: Long,
    @ColumnInfo(name = "conditions_json") val conditionsJson: String,
)

internal data class StoredLawCacheSnapshot(
    @Embedded val state: LawCacheStateEntity,
    @Relation(parentColumn = "singleton_id", entityColumn = "cache_id") val cards: List<LawCardEntity>,
)

@Dao
internal interface LawCardDao {
    @Transaction
    @Query("SELECT * FROM law_cache_state WHERE singleton_id = 1")
    fun observe(): Flow<List<StoredLawCacheSnapshot>>

    @Transaction
    @Query("SELECT * FROM law_cache_state WHERE singleton_id = 1")
    suspend fun read(): List<StoredLawCacheSnapshot>

    @Upsert suspend fun saveCards(cards: List<LawCardEntity>)
    @Upsert suspend fun saveState(state: LawCacheStateEntity)
}
