package com.triggerconnect.app.data.repository

import com.triggerconnect.app.core.AppError
import com.triggerconnect.app.core.AppErrorType
import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.core.Constants
import com.triggerconnect.app.core.map
import com.triggerconnect.app.core.onSuccess
import com.triggerconnect.app.data.local.dao.TriggerEventDao
import com.triggerconnect.app.data.mapper.toDomain
import com.triggerconnect.app.data.mapper.toEntity
import com.triggerconnect.app.data.remote.SupabaseApi
import com.triggerconnect.app.data.remote.dto.EventAcknowledgeBody
import com.triggerconnect.app.data.remote.dto.EventRecipientRowDto
import com.triggerconnect.app.data.remote.dto.EventRowDto
import com.triggerconnect.app.data.remote.dto.SimpleOk
import com.triggerconnect.app.data.remote.dto.TriggerDispatchBody
import com.triggerconnect.app.data.remote.dto.TriggerDispatchResponse
import com.triggerconnect.app.domain.model.DestinationType
import com.triggerconnect.app.domain.model.EventRecipient
import com.triggerconnect.app.domain.model.TriggerEvent
import com.triggerconnect.app.domain.model.TriggerPriority
import com.triggerconnect.app.domain.repository.TriggerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TriggerRepositoryImpl(
    private val api: SupabaseApi,
    private val eventDao: TriggerEventDao,
) : TriggerRepository {

    override suspend fun sendTrigger(
        clientEventId: String,
        destinationType: DestinationType,
        destinationId: String,
        content: String,
        priority: TriggerPriority,
        notifySelf: Boolean,
    ): AppResult<TriggerEvent> {
        val result = api.callFunction<TriggerDispatchBody, TriggerDispatchResponse>(
            name = Constants.Functions.TRIGGER_DISPATCH,
            body = TriggerDispatchBody(
                clientEventId = clientEventId,
                destinationType = destinationType.name,
                destinationId = destinationId,
                content = content.trim(),
                priority = priority.name,
                notifySelf = notifySelf,
            ),
        )
        return result.map { resp ->
            eventDao.upsert(resp.event.toEntity())
            resp.event.toDomain()
        }
    }

    override fun observeHistory(): Flow<List<TriggerEvent>> =
        eventDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun refreshHistory(): AppResult<Unit> =
        api.rpcList<EventRowDto>(Constants.Rpc.MY_HISTORY)
            .onSuccess { rows -> eventDao.replaceAll(rows.map { it.toEntity() }) }
            .map { }

    override suspend fun eventDetail(eventId: String): AppResult<Pair<TriggerEvent, List<EventRecipient>>> {
        val recipients = api.rpcList<EventRecipientRowDto>(
            name = Constants.Rpc.EVENT_RECIPIENTS,
            parameters = buildJsonObject { put("p_event_id", eventId) },
        )
        return recipients.map { rows ->
            val event = eventDao.getById(eventId)?.toDomain()
                ?: return AppResult.Failure(AppError(AppErrorType.NOT_FOUND, "Event not found."))
            event to rows.map { it.toDomain() }
        }
    }

    override suspend fun acknowledge(eventId: String): AppResult<Unit> {
        val result = api.callFunction<EventAcknowledgeBody, SimpleOk>(
            name = Constants.Functions.EVENT_ACKNOWLEDGE,
            body = EventAcknowledgeBody(eventId = eventId),
        ).map { }
        if (result is AppResult.Success) refreshHistory()
        return result
    }
}
