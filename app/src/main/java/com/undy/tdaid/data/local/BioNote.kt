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

/** A TD's manually-entered notes about a player, persisted so they carry forward to future rounds.
 *  No hometown field — that's already real PDGA data ([com.undy.tdaid.data.model.PdgaProfile.homeLocation]),
 *  not something a TD needs to re-enter.
 *
 *  [sponsor] and [walkOnSong] are set only by the CSV importer, never by the manual bio editor —
 *  kept in their own columns rather than folded into [bio] so a bulk CSV re-import can't clobber
 *  (or get confused with) whatever a TD has typed by hand. */
data class BioNote(
    val playerId: String,
    val pronunciation: String,
    val bio: String,
    val sponsor: String,
    val walkOnSong: String,
    val savedToLibrary: Boolean,
    val sourceRoundLabel: String?,
    val updatedAtMillis: Long,
)

@Entity(tableName = "bio_notes")
data class BioNoteEntity(
    @PrimaryKey val playerId: String,
    val pronunciation: String,
    val bio: String,
    val sponsor: String,
    val walkOnSong: String,
    val savedToLibrary: Boolean,
    val sourceRoundLabel: String?,
    val updatedAtMillis: Long,
)

fun BioNoteEntity.toDomain() = BioNote(playerId, pronunciation, bio, sponsor, walkOnSong, savedToLibrary, sourceRoundLabel, updatedAtMillis)
fun BioNote.toEntity() = BioNoteEntity(playerId, pronunciation, bio, sponsor, walkOnSong, savedToLibrary, sourceRoundLabel, updatedAtMillis)

@Dao
interface BioNoteDao {
    @Query("SELECT * FROM bio_notes WHERE playerId = :playerId")
    fun observe(playerId: String): Flow<BioNoteEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BioNoteEntity)

    @Query("DELETE FROM bio_notes")
    suspend fun clearAll()
}

@Database(entities = [BioNoteEntity::class, PlayerProfileCacheEntity::class], version = 5, exportSchema = true)
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
    /** Sets [playerId]'s CSV-sourced sponsor/walk-on song, creating a bio note if none exists yet.
     *  A blank value here means the CSV had nothing for that column on this row, so it leaves
     *  whatever was already stored alone rather than clobbering it — only a non-blank cell
     *  overwrites. Never touches [BioNote.bio]/[BioNote.pronunciation], which are the TD's own,
     *  manually-entered fields and CSV import has no business changing them. */
    suspend fun importSponsorInfo(playerId: String, sponsor: String, walkOnSong: String)
    suspend fun clearAll()
}

class RoomBioNotesRepository(private val dao: BioNoteDao) : BioNotesRepository {
    override fun observeNote(playerId: String): Flow<BioNote?> =
        dao.observe(playerId).map { it?.toDomain() }

    override suspend fun saveNote(note: BioNote) {
        dao.upsert(note.toEntity())
    }

    override suspend fun importSponsorInfo(playerId: String, sponsor: String, walkOnSong: String) {
        val existing = dao.observe(playerId).first()?.toDomain()
        val merged = existing?.copy(
            sponsor = sponsor.ifBlank { existing.sponsor },
            walkOnSong = walkOnSong.ifBlank { existing.walkOnSong },
            updatedAtMillis = System.currentTimeMillis(),
        ) ?: BioNote(
            playerId = playerId,
            pronunciation = "",
            bio = "",
            sponsor = sponsor,
            walkOnSong = walkOnSong,
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
