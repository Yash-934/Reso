package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY createdAt ASC")
    fun getAllServers(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultServer(): ServerEntity?

    @Query("SELECT * FROM servers WHERE id = :id LIMIT 1")
    suspend fun getServerById(id: String): ServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServers(servers: List<ServerEntity>)

    @Update
    suspend fun updateServer(server: ServerEntity)

    @Query("UPDATE servers SET isDefault = CASE WHEN id = :serverId THEN 1 ELSE 0 END")
    suspend fun setDefaultServer(serverId: String)

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun deleteServer(id: String)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE isTemporary = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("UPDATE conversations SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: String, isPinned: Boolean)

    @Query("SELECT * FROM conversations WHERE isTemporary = 0 AND (title LIKE '%' || :query || '%' OR lastMessagePreview LIKE '%' || :query || '%') ORDER BY updatedAt DESC")
    fun searchConversations(query: String): Flow<List<ConversationEntity>>
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessagesList(conversationId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)
}

@Dao
interface PersonaDao {
    @Query("SELECT * FROM personas ORDER BY isBuiltIn DESC, createdAt ASC")
    fun getAllPersonas(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas WHERE id = :id LIMIT 1")
    suspend fun getPersonaById(id: String): PersonaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersona(persona: PersonaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonas(personas: List<PersonaEntity>)

    @Query("DELETE FROM personas WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustomPersona(id: String)
}

@Dao
interface SavedPromptDao {
    @Query("SELECT * FROM saved_prompts ORDER BY createdAt DESC")
    fun getAllSavedPrompts(): Flow<List<SavedPromptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrompt(prompt: SavedPromptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrompts(prompts: List<SavedPromptEntity>)

    @Query("DELETE FROM saved_prompts WHERE id = :id")
    suspend fun deletePrompt(id: String)
}

@Dao
interface OfflineModelDao {
    @Query("SELECT * FROM offline_models ORDER BY isImported ASC, sizeInBytes ASC")
    fun getAllOfflineModels(): Flow<List<OfflineModelEntity>>

    @Query("SELECT * FROM offline_models ORDER BY isImported ASC, sizeInBytes ASC")
    suspend fun getAllOfflineModelsList(): List<OfflineModelEntity>

    @Query("SELECT * FROM offline_models WHERE id = :id")
    suspend fun getModelById(id: String): OfflineModelEntity?

    @Query("SELECT * FROM offline_models WHERE status = 'DOWNLOADED' OR isImported = 1")
    fun getDownloadedModels(): Flow<List<OfflineModelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: OfflineModelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<OfflineModelEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultModels(models: List<OfflineModelEntity>)

    @androidx.room.Transaction
    suspend fun syncDefaultModels(defaultModels: List<OfflineModelEntity>) {
        for (defaultModel in defaultModels) {
            val existing = getModelById(defaultModel.id)
            if (existing == null) {
                insertModel(defaultModel)
            } else if (existing.status != "DOWNLOADED") {
                updateModel(
                    existing.copy(
                        name = defaultModel.name,
                        modelId = defaultModel.modelId,
                        modelFile = defaultModel.modelFile,
                        downloadUrl = defaultModel.downloadUrl,
                        sizeInBytes = defaultModel.sizeInBytes,
                        description = defaultModel.description,
                        supportedAccelerators = defaultModel.supportedAccelerators,
                        contextLength = defaultModel.contextLength,
                        licenseUrl = defaultModel.licenseUrl,
                        isReasoningModel = defaultModel.isReasoningModel,
                        status = if (existing.status == "FAILED" || existing.status == "DOWNLOADING") "AVAILABLE" else existing.status
                    )
                )
            } else {
                updateModel(
                    existing.copy(
                        description = defaultModel.description,
                        licenseUrl = defaultModel.licenseUrl,
                        contextLength = defaultModel.contextLength
                    )
                )
            }
        }
    }

    @Update
    suspend fun updateModel(model: OfflineModelEntity)

    @Query("UPDATE offline_models SET status = :status, downloadedBytes = :downloadedBytes, filePath = :filePath WHERE id = :id")
    suspend fun updateDownloadProgress(id: String, status: String, downloadedBytes: Long, filePath: String?)

    @Query("UPDATE offline_models SET selectedAccelerator = :accelerator WHERE id = :id")
    suspend fun updateAccelerator(id: String, accelerator: String)

    @Query("DELETE FROM offline_models WHERE id = :id")
    suspend fun deleteModel(id: String)
}
