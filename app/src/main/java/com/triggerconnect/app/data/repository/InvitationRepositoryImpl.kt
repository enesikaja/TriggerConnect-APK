package com.triggerconnect.app.data.repository

import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.core.Constants
import com.triggerconnect.app.core.map
import com.triggerconnect.app.data.mapper.toDomain
import com.triggerconnect.app.data.mapper.toEntity
import com.triggerconnect.app.data.remote.SupabaseApi
import com.triggerconnect.app.data.remote.dto.GroupJoinBody
import com.triggerconnect.app.data.remote.dto.GroupJoinResponse
import com.triggerconnect.app.data.remote.dto.InviteResolveBody
import com.triggerconnect.app.data.remote.dto.InvitePreviewDto
import com.triggerconnect.app.domain.model.Group
import com.triggerconnect.app.domain.model.InvitePreview
import com.triggerconnect.app.domain.repository.GroupRepository
import com.triggerconnect.app.domain.repository.InvitationRepository

class InvitationRepositoryImpl(
    private val api: SupabaseApi,
    private val groupRepository: GroupRepository,
) : InvitationRepository {

    override suspend fun resolve(token: String): AppResult<InvitePreview> =
        api.callFunction<InviteResolveBody, InvitePreviewDto>(
            name = Constants.Functions.INVITE_RESOLVE,
            body = InviteResolveBody(token = token),
        ).map { it.toDomain() }

    override suspend fun joinGroup(invitationId: String): AppResult<Group> {
        val result = api.callFunction<GroupJoinBody, GroupJoinResponse>(
            name = Constants.Functions.GROUP_JOIN,
            body = GroupJoinBody(invitationId = invitationId),
        )
        return result.map { resp ->
            groupRepository.refresh()
            resp.group.toEntity().toDomain()
        }
    }
}
