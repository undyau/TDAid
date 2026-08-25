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
import kotlinx.coroutines.flow.first
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BioNoteEntity)

    @Query("DELETE FROM bio_notes")
    suspend fun clearAll()
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
    /** Folds imported text into [playerId]'s bio note, creating one if none exists yet. Existing
     *  bio text is kept, with the new text appended — this is meant for bulk CSV import, where
     *  overwriting a TD's existing notes outright would be surprising. */
    suspend fun appendBio(playerId: String, additionalText: String)
    suspend fun clearAll()
}

class RoomBioNotesRepository(private val dao: BioNoteDao) : BioNotesRepository {
    override fun observeNote(playerId: String): Flow<BioNote?> =
        dao.observe(playerId).map { it?.toDomain() }

    override suspend fun saveNote(note: BioNote) {
        dao.upsert(note.toEntity())
    }

    override suspend fun appendBio(playerId: String, additionalText: String) {
        val existing = dao.observe(playerId).first()?.toDomain()
        val merged = existing?.copy(
            bio = if (existing.bio.isBlank()) additionalText else "${existing.bio}\n\n$additionalText",
            updatedAtMillis = System.currentTimeMillis(),
        ) ?: BioNote(
            playerId = playerId,
            pronunciation = "",
            hometown = "",
            bio = additionalText,
            savedToLibrary = true,
            sourceRoundLabel = null,
            updatedAtMillis = System.currentTimeMillis(),
        )
        dao.upsert(merged.toEntity())
    }

    override suspend fun clearAll() {
        dao.clearAll()
    }
}
