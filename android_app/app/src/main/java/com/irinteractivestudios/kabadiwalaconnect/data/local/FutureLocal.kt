package com.irinteractivestudios.kabadiwalaconnect.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ChatMessageDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ConversationDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.DiyActivityDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.GovernmentSchemeDto

@Entity(tableName = "future_schemes")
data class SchemeCacheEntity(
    @androidx.room.PrimaryKey val id: String,
    val slug: String,
    val title: String,
    val description: String,
    val documentsCsv: String,
    val sourceUrl: String,
    val lastVerifiedAt: String,
    val cachedAtEpochMs: Long
)

@Entity(tableName = "future_activities")
data class ActivityCacheEntity(
    @androidx.room.PrimaryKey val id: String,
    val slug: String,
    val title: String,
    val description: String,
    val materialsCsv: String,
    val stepsCsv: String,
    val warningsCsv: String,
    val difficulty: String,
    val minutes: Int,
    val cachedAtEpochMs: Long
)

@Entity(tableName = "future_conversations")
data class ConversationCacheEntity(
    @androidx.room.PrimaryKey val id: String,
    val lotId: String,
    val quoteId: String?,
    val collectorId: String,
    val recyclerId: String,
    val status: String,
    val lastMessageAt: String?
)

@Entity(tableName = "future_messages", primaryKeys = ["id"])
data class MessageCacheEntity(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderRole: String,
    val clientMessageId: String,
    val body: String,
    val status: String,
    val createdAt: String?,
    val readAt: String?
)

@Dao
interface FutureCacheDao {
    @Query("SELECT * FROM future_schemes ORDER BY lastVerifiedAt DESC")
    suspend fun schemes(): List<SchemeCacheEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSchemes(items: List<SchemeCacheEntity>)
    @Query("DELETE FROM future_schemes")
    suspend fun clearSchemes()

    @Query("SELECT * FROM future_activities ORDER BY difficulty, minutes")
    suspend fun activities(): List<ActivityCacheEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActivities(items: List<ActivityCacheEntity>)
    @Query("DELETE FROM future_activities")
    suspend fun clearActivities()

    @Query("SELECT * FROM future_conversations ORDER BY lastMessageAt DESC")
    suspend fun conversations(): List<ConversationCacheEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConversations(items: List<ConversationCacheEntity>)
    @Query("DELETE FROM future_conversations")
    suspend fun clearConversations()

    @Query("SELECT * FROM future_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun messages(conversationId: String): List<MessageCacheEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMessages(items: List<MessageCacheEntity>)
    @Query("DELETE FROM future_messages WHERE id = :id")
    suspend fun deleteMessage(id: String)
    @Query("DELETE FROM future_messages")
    suspend fun clearMessages()
}

class FutureCacheStore(private val dao: FutureCacheDao) {
    suspend fun schemes(): List<GovernmentSchemeDto> = dao.schemes().map { GovernmentSchemeDto(it.id, it.slug, it.title, it.description, requiredDocuments = it.documentsCsv.csv(), sourceUrl = it.sourceUrl, lastVerifiedAt = it.lastVerifiedAt) }
    suspend fun saveSchemes(items: List<GovernmentSchemeDto>) { dao.clearSchemes(); dao.saveSchemes(items.map { SchemeCacheEntity(it.id, it.slug, it.title, it.description, it.requiredDocuments.joinToString("\u001f"), it.sourceUrl, it.lastVerifiedAt.orEmpty(), System.currentTimeMillis()) }) }
    suspend fun activities(): List<DiyActivityDto> = dao.activities().map { DiyActivityDto(it.id, it.slug, it.title, it.description, it.materialsCsv.csv(), it.stepsCsv.csv(), it.warningsCsv.csv(), it.difficulty, it.minutes) }
    suspend fun saveActivities(items: List<DiyActivityDto>) { dao.clearActivities(); dao.saveActivities(items.map { ActivityCacheEntity(it.id, it.slug, it.title, it.description, it.materials.joinToString("\u001f"), it.steps.joinToString("\u001f"), it.safetyWarnings.joinToString("\u001f"), it.difficulty, it.minutes, System.currentTimeMillis()) }) }
    suspend fun conversations(): List<ConversationDto> = dao.conversations().map { ConversationDto(it.id, it.lotId, it.quoteId, it.collectorId, it.recyclerId, it.status, it.lastMessageAt) }
    suspend fun saveConversations(items: List<ConversationDto>) { dao.saveConversations(items.map { ConversationCacheEntity(it.id, it.lotId, it.quoteId, it.collectorId, it.recyclerId, it.status, it.lastMessageAt) }) }
    suspend fun messages(conversationId: String): List<ChatMessageDto> = dao.messages(conversationId).map { ChatMessageDto(it.id, it.conversationId, it.senderId, it.senderRole, it.clientMessageId, it.body, it.status, it.createdAt, it.readAt) }
    suspend fun saveMessages(items: List<ChatMessageDto>) { dao.saveMessages(items.map { MessageCacheEntity(it.id, it.conversationId, it.senderId, it.senderRole, it.clientMessageId, it.body, it.status, it.createdAt, it.readAt) }) }
    suspend fun deleteMessage(id: String) { dao.deleteMessage(id) }
}

private fun String.csv(): List<String> = split('\u001f').filter { it.isNotBlank() }
