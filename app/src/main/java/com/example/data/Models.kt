package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// 1. Program File Entity
@Entity(tableName = "program_files")
data class ProgramFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val content: String,
    val language: String, // "javascript", "html", "css"
    val isCurrent: Boolean = false,
    val lastModified: Long = System.currentTimeMillis(),
    val folder: String = "",
    val externalUri: String? = null
)

// 2. Npm Library Entity
@Entity(tableName = "npm_libraries")
data class NpmLibrary(
    @PrimaryKey val name: String,
    val url: String,
    val isDownloaded: Boolean = false,
    val localContent: String? = null,
    val version: String = "latest"
)

// 3. Console Log Entity
@Entity(tableName = "console_logs")
data class ConsoleLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "log", "error", "warn", "info"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

// 4. Setting Entity
@Entity(tableName = "settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)

// --- DAOs ---

@Dao
interface ProgramFileDao {
    @Query("SELECT * FROM program_files ORDER BY lastModified DESC")
    fun getAllFilesFlow(): Flow<List<ProgramFile>>

    @Query("SELECT * FROM program_files ORDER BY lastModified DESC")
    suspend fun getAllFiles(): List<ProgramFile>

    @Query("SELECT * FROM program_files WHERE isCurrent = 1 LIMIT 1")
    fun getCurrentFileFlow(): Flow<ProgramFile?>

    @Query("SELECT * FROM program_files WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrentFile(): ProgramFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: ProgramFile): Long

    @Update
    suspend fun updateFile(file: ProgramFile)

    @Delete
    suspend fun deleteFile(file: ProgramFile)

    @Query("UPDATE program_files SET isCurrent = 0")
    suspend fun clearCurrentStatus()

    @Transaction
    suspend fun setCurrentFile(fileId: Int) {
        clearCurrentStatus()
        updateCurrentStatus(fileId, true)
    }

    @Query("UPDATE program_files SET isCurrent = :isCurrent WHERE id = :id")
    suspend fun updateCurrentStatus(id: Int, isCurrent: Boolean)
}

@Dao
interface NpmLibraryDao {
    @Query("SELECT * FROM npm_libraries ORDER BY name ASC")
    fun getAllLibrariesFlow(): Flow<List<NpmLibrary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibrary(library: NpmLibrary)

    @Delete
    suspend fun deleteLibrary(library: NpmLibrary)

    @Query("SELECT * FROM npm_libraries WHERE name = :name LIMIT 1")
    suspend fun getLibraryByName(name: String): NpmLibrary?
}

@Dao
interface ConsoleLogDao {
    @Query("SELECT * FROM console_logs ORDER BY timestamp ASC")
    fun getAllLogsFlow(): Flow<List<ConsoleLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ConsoleLog)

    @Query("DELETE FROM console_logs")
    suspend fun clearLogs()
}

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): AppSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: AppSetting)
}
