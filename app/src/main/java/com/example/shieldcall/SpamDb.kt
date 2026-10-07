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
data class SpamNumber(@PrimaryKey val number: String, val type: String, val source: String = "mine")

@Entity(tableName = "blocked")
data class BlockedNumber(
    @PrimaryKey val number: String,
    val name: String,
    val type: String = "number"
)

@Entity(tableName = "hangups")
data class HangupEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val time: Long,
    val secondsSaved: Int = 15,
    val reason: String = ""
)

@Entity(tableName = "calls")
data class CallEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val raw: String,
    val name: String,
    val status: String,
    val time: Long,
    val duration: Int = 0
)

@Entity(tableName = "identifications")
data class IdentificationEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val type: String,
    val time: Long
)

@Dao
interface SpamDao {
    @Query("SELECT * FROM numbers WHERE number = :tail LIMIT 1")
    fun entry(tail: String): SpamNumber?

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

    @Query("SELECT * FROM blocked")
    fun blockedList(): List<BlockedNumber>

    @Insert
    fun addCall(item: CallEntry)

    @Query("DELETE FROM calls WHERE id NOT IN (SELECT id FROM calls ORDER BY time DESC LIMIT 100)")
    fun trimCalls()

    @Query("SELECT * FROM calls ORDER BY time DESC LIMIT 100")
    fun recentFlow(): Flow<List<CallEntry>>

    @Insert
    fun addIdentification(item: IdentificationEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addIdentifications(items: List<IdentificationEntry>)

    @Query("SELECT * FROM identifications ORDER BY time DESC")
    fun identificationsFlow(): Flow<List<IdentificationEntry>>

    @Query("SELECT * FROM identifications")
    fun getAllIdentifications(): List<IdentificationEntry>

    @Insert
    fun addHangup(item: HangupEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addHangups(items: List<HangupEntry>)

    @Query("SELECT * FROM hangups ORDER BY time DESC")
    fun hangupsFlow(): Flow<List<HangupEntry>>

    @Query("SELECT * FROM hangups")
    fun getAllHangups(): List<HangupEntry>

    @Query("DELETE FROM identifications")
    fun clearIdentifications()

    @Query("DELETE FROM identifications WHERE number = :number")
    fun deleteIdentifications(number: String)

    @Query("DELETE FROM hangups")
    fun clearHangups()

    @Query("DELETE FROM numbers WHERE source IN ('list', 'skip')")
    fun clearPublic()

    @Query("DELETE FROM numbers WHERE number = :tail AND source = 'mine'")
    fun removeMine(tail: String)

    @Query("SELECT * FROM calls WHERE status = 'Blocked' ORDER BY time DESC LIMIT 100")
    fun blockedCallsFlow(): Flow<List<CallEntry>>

    @Query("DELETE FROM calls WHERE status = 'Blocked'")
    fun clearBlockedCalls()

    @Query("SELECT * FROM blocked")
    fun allBlocked(): List<BlockedNumber>

    @Query("SELECT COUNT(*) FROM calls WHERE number = :number AND status = 'Missed' AND time > :since")
    fun recentMissed(number: String, since: Long): Int

    @Query("SELECT COUNT(*) FROM identifications WHERE LOWER(type) = :type")
    fun identifiedCount(type: String): Int

    @Query("SELECT COUNT(*) FROM identifications")
    fun identifiedTotal(): Int

    @Query("SELECT * FROM calls ORDER BY time DESC")
    fun allCallsFlow(): Flow<List<CallEntry>>

    @Query("DELETE FROM calls WHERE id IN (:ids)")
    fun deleteCalls(ids: List<Long>)

    @Query("DELETE FROM calls")
    fun clearCalls()

    @Query("SELECT number FROM calls WHERE status = 'Outgoing' ORDER BY time DESC LIMIT 1")
    fun lastOutgoing(): String?

    @Query("SELECT * FROM calls WHERE number = :number ORDER BY time DESC LIMIT 5")
    fun callsFor(number: String): Flow<List<CallEntry>>
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

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE numbers ADD COLUMN source TEXT NOT NULL DEFAULT 'list'")
    }
}

private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
    db.query("PRAGMA table_info(`$table`)").use { c ->
        val i = c.getColumnIndex("name")
        while (c.moveToNext()) if (c.getString(i) == column) return true
    }
    return false
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!hasColumn(db, "numbers", "source")) {
            db.execSQL("ALTER TABLE numbers ADD COLUMN source TEXT NOT NULL DEFAULT 'list'")
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS `identifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `number` TEXT NOT NULL, `type` TEXT NOT NULL, `time` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `hangups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `number` TEXT NOT NULL, `time` INTEGER NOT NULL, `secondsSaved` INTEGER NOT NULL)")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!hasColumn(db, "blocked", "type")) {
            db.execSQL("ALTER TABLE blocked ADD COLUMN type TEXT NOT NULL DEFAULT 'number'")
        }
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!hasColumn(db, "calls", "duration")) {
            db.execSQL("ALTER TABLE calls ADD COLUMN duration INTEGER NOT NULL DEFAULT 0")
        }
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!hasColumn(db, "hangups", "reason")) {
            db.execSQL("ALTER TABLE hangups ADD COLUMN reason TEXT NOT NULL DEFAULT ''")
        }
    }
}

@Database(
    entities = [SpamNumber::class, BlockedNumber::class, CallEntry::class, IdentificationEntry::class, HangupEntry::class],
    version = 8,
    exportSchema = false
)
abstract class SpamDb : RoomDatabase() {
    abstract fun dao(): SpamDao

    companion object {
        @Volatile private var instance: SpamDb? = null

        fun get(context: Context): SpamDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, SpamDb::class.java, "spam.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .allowMainThreadQueries()
                .build()
                .also { instance = it }
        }
    }
}