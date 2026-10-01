package com.example.shieldcall

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "numbers")
data class SpamNumber(@PrimaryKey val number: String, val type: String)

@Entity(tableName = "blocked")
data class BlockedNumber(@PrimaryKey val number: String, val name: String)

@Entity(tableName = "calls")
data class CallEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val raw: String,
    val name: String,
    val status: String,
    val time: Long
)

@Dao
interface SpamDao {
    @Query("SELECT type FROM numbers WHERE number = :tail LIMIT 1")
    fun find(tail: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: SpamNumber)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(items: List<SpamNumber>)

    @Query("DELETE FROM numbers")
    fun clear()

    @Query("SELECT COUNT(*) FROM blocked WHERE number = :number")
    fun isBlocked(number: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun block(item: BlockedNumber)

    @Query("DELETE FROM blocked WHERE number = :number")
    fun unblock(number: String)

    @Query("SELECT * FROM blocked")
    fun blockedFlow(): Flow<List<BlockedNumber>>

    @Insert
    fun addCall(item: CallEntry)

    @Query("DELETE FROM calls WHERE id NOT IN (SELECT id FROM calls ORDER BY time DESC LIMIT 100)")
    fun trimCalls()

    @Query("SELECT * FROM calls ORDER BY time DESC LIMIT 10")
    fun recentFlow(): Flow<List<CallEntry>>
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `blocked` (`number` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`number`))")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `calls` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `number` TEXT NOT NULL, `raw` TEXT NOT NULL, `name` TEXT NOT NULL, `status` TEXT NOT NULL, `time` INTEGER NOT NULL)")
    }
}

@Database(entities = [SpamNumber::class, BlockedNumber::class, CallEntry::class], version = 3, exportSchema = false)
abstract class SpamDb : RoomDatabase() {
    abstract fun dao(): SpamDao

    companion object {
        @Volatile private var instance: SpamDb? = null

        fun get(context: Context): SpamDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, SpamDb::class.java, "spam.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .allowMainThreadQueries()
                .build()
                .also { instance = it }
        }
    }
}