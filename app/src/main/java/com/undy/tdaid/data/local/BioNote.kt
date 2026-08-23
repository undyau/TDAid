package com.undy.tdaid.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A TD's manually-entered notes about a player, persisted so they carry forward to future rounds. */
data class BioNote(
    val playerId: String,
    val pronunciation: String,
    val hometown: String,
    val bio: String,
    val savedToLibrary: Boolean,
    val sourceRoundLabel: String?,
    val updatedAtMillis: Long,
)

@Entity(tableName = "bio_notes")
data class BioNoteEntity(
    @PrimaryKey val playerId: String,
    val pronunciation: String,
    val hometown: String,
    val bio: String,
    val savedToLibrary: Boolean,
    val sourceRoundLabel: String?,
    val updatedAtMillis: Long,
)

fun BioNoteEntity.toDomain() = BioNote(playerId, pronunciation, hometown, bio, savedToLibrary, sourceRoundLabel, updatedAtMillis)
fun BioNote.toEntity() = BioNoteEntity(playerId, pronunciation, hometown, bio, savedToLibrary, sourceRoundLabel, updatedAtMillis)

@Dao
interface BioNoteDao {
    @Query("SELECT * FROM bio_notes WHERE playerId = :playerId")
    fun observe(playerId: String): Flow<BioNoteEntity?>

    @Query("SELECT COUNT(*) FROM bio_notes")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BioNoteEntity)
}

@Database(entities = [BioNoteEntity::class, PlayerProfileCacheEntity::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bioNoteDao(): BioNoteDao
    abstract fun playerProfileCacheDao(): PlayerProfileCacheDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "tdaid.db",
            )
                // No migration is worth writing yet for a pre-release dev build with only local
                // test data — revisit once this ships with real user data to preserve.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build().also { instance = it }
        }
    }
}

interface BioNotesRepository {
    fun observeNote(playerId: String): Flow<BioNote?>
    suspend fun saveNote(note: BioNote)
    suspend fun seedIfEmpty()
}

class RoomBioNotesRepository(private val dao: BioNoteDao) : BioNotesRepository {
    override fun observeNote(playerId: String): Flow<BioNote?> =
        dao.observe(playerId).map { it?.toDomain() }

    override suspend fun saveNote(note: BioNote) {
        dao.upsert(note.toEntity())
    }

    override suspend fun seedIfEmpty() {
        if (dao.count() == 0) {
            // A note saved during an earlier round, so the app has something real to retrieve
            // and demonstrate persistence across rounds with, rather than an empty form.
            dao.upsert(
                BioNoteEntity(
                    playerId = "kessler",
                    pronunciation = "KESS-lur",
                    hometown = "Cedar Falls, IA",
                    bio = "Local favorite, three-time club champion. Smooth forehand and clutch " +
                        "putting under pressure — the gallery loves him on the final few holes.",
                    savedToLibrary = true,
                    sourceRoundLabel = "Spring Kickoff, May 2026",
                    updatedAtMillis = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000,
                ),
            )
        }
    }
}
