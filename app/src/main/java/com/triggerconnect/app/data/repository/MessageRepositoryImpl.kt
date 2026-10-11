package com.triggerconnect.app.data.repository

import com.triggerconnect.app.core.AppError
import com.triggerconnect.app.core.AppErrorType
import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.core.Constants
import com.triggerconnect.app.core.map
import com.triggerconnect.app.core.onSuccess
import com.triggerconnect.app.data.local.SecureStorage
import com.triggerconnect.app.data.local.dao.SavedMessageDao
import com.triggerconnect.app.data.mapper.toDomain
import com.triggerconnect.app.data.mapper.toEntity
import com.triggerconnect.app.data.remote.SupabaseApi
import com.triggerconnect.app.data.remote.dto.SavedMessageInsert
import com.triggerconnect.app.data.remote.dto.SavedMessageRowDto
import com.triggerconnect.app.domain.model.SavedMessage
import com.triggerconnect.app.domain.repository.MessageRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MessageRepositoryImpl(
    private val client: SupabaseClient,
    private val api: SupabaseApi,
    private val savedMessageDao: SavedMessageDao,
    private val secureStorage: SecureStorage,
) : MessageRepository {

    override fun observeSavedMessages(): Flow<List<SavedMessage>> =
        savedMessageDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun refresh(): AppResult<Unit> =
        api.rpcList<SavedMessageRowDto>(Constants.Rpc.MY_SAVED_MESSAGES)
            .onSuccess { rows -> savedMessageDao.replaceAll(rows.map { it.toEntity() }) }
            .map { }

    override suspend fun createMessage(content: String): AppResult<SavedMessage> {
        val uid = secureStorage.userId
            ?: return AppResult.Failure(AppError(AppErrorType.UNAUTHORIZED, "Not signed in."))
        return api.runRemote {
            val row = client.postgrest.from("saved_messages")
                .insert(SavedMessageInsert(ownerId = uid, content = content.trim())) { select() }
                .decodeSingle<SavedMessageRowDto>()
            refresh()
            row.toEntity().toDomain()
        }
    }

    override suspend fun updateMessage(messageId: String, content: String): AppResult<Unit> =
        api.runRemote {
            client.postgrest.from("saved_messages").update(
                { set("content", content.trim()) },
            ) { filter { eq("message_id", messageId) } }
            refresh()
            Unit
        }

    override suspend fun deleteMessage(messageId: String): AppResult<Unit> =
        api.runRemote {
            client.postgrest.from("saved_messages").delete { filter { eq("message_id", messageId) } }
            refresh()
            Unit
        }
}
