package com.triggerconnect.app.data.repository

import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.core.Constants
import com.triggerconnect.app.core.map
import com.triggerconnect.app.core.onSuccess
import com.triggerconnect.app.data.local.dao.ContactDao
import com.triggerconnect.app.data.mapper.toDomain
import com.triggerconnect.app.data.mapper.toEntity
import com.triggerconnect.app.data.remote.SupabaseApi
import com.triggerconnect.app.data.remote.dto.ConnectionRequestBody
import com.triggerconnect.app.data.remote.dto.ConnectionRequestRowDto
import com.triggerconnect.app.data.remote.dto.ConnectionRespondBody
import com.triggerconnect.app.data.remote.dto.ContactRowDto
import com.triggerconnect.app.data.remote.dto.SimpleOk
import com.triggerconnect.app.domain.model.Contact
import com.triggerconnect.app.domain.model.ConnectionRequest
import com.triggerconnect.app.domain.repository.ConnectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConnectionRepositoryImpl(
    private val api: SupabaseApi,
    private val contactDao: ContactDao,
) : ConnectionRepository {

    override fun observeContacts(): Flow<List<Contact>> =
        contactDao.observeContacts().map { list -> list.map { it.toDomain() } }

    override fun observeIncomingRequests(): Flow<List<ConnectionRequest>> =
        contactDao.observeRequests().map { list -> list.map { it.toDomain() } }

    override suspend fun refresh(): AppResult<Unit> {
        val contacts = api.rpcList<ContactRowDto>(Constants.Rpc.MY_CONTACTS)
            .onSuccess { rows -> contactDao.replaceAll(rows.map { it.toEntity() }) }
        val requests = api.rpcList<ConnectionRequestRowDto>(Constants.Rpc.INCOMING_REQUESTS)
            .onSuccess { rows -> contactDao.replaceRequests(rows.map { it.toEntity() }) }
        return when {
            contacts is AppResult.Failure -> contacts.map { }
            requests is AppResult.Failure -> requests.map { }
            else -> AppResult.Success(Unit)
        }
    }

    override suspend fun requestConnection(inviteToken: String): AppResult<Unit> =
        api.callFunction<ConnectionRequestBody, SimpleOk>(
            name = Constants.Functions.CONNECTION_REQUEST,
            body = ConnectionRequestBody(inviteToken = inviteToken),
        ).map { }

    override suspend fun respondToRequest(connectionId: String, accept: Boolean): AppResult<Unit> {
        val result = api.callFunction<ConnectionRespondBody, SimpleOk>(
            name = Constants.Functions.CONNECTION_RESPOND,
            body = ConnectionRespondBody(connectionId = connectionId, action = "respond", accept = accept),
        ).map { }
        if (result is AppResult.Success) refresh()
        return result
    }

    override suspend fun removeContact(connectionId: String): AppResult<Unit> =
        mutateConnection(connectionId, "remove")

    override suspend fun blockContact(connectionId: String): AppResult<Unit> =
        mutateConnection(connectionId, "block")

    private suspend fun mutateConnection(connectionId: String, action: String): AppResult<Unit> {
        val result = api.callFunction<ConnectionRespondBody, SimpleOk>(
            name = Constants.Functions.CONNECTION_RESPOND,
            body = ConnectionRespondBody(connectionId = connectionId, action = action),
        ).map { }
        if (result is AppResult.Success) refresh()
        return result
    }
}
