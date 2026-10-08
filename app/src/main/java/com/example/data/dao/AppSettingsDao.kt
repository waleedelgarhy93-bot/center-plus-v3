package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AppSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingsDao {

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): AppSettings?

    @Query("SELECT isSetupComplete FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun isSetupComplete(): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: AppSettings): Long

    @Update
    suspend fun updateSettings(settings: AppSettings)

    @Query("SELECT COUNT(*) FROM app_settings")
    suspend fun getCount(): Int
}
