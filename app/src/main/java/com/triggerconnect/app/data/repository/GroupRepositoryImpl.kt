package com.triggerconnect.app.data.repository

import com.triggerconnect.app.core.AppError
import com.triggerconnect.app.core.AppErrorType
import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.core.Constants
import com.triggerconnect.app.core.map
import com.triggerconnect.app.core.onSuccess
import com.triggerconnect.app.data.local.dao.GroupDao
import com.triggerconnect.app.data.mapper.toDomain
import com.triggerconnect.app.data.mapper.toEntity
import com.triggerconnect.app.data.remote.SupabaseApi
import com.triggerconnect.app.data.remote.dto.GroupInviteCreateBody
import com.triggerconnect.app.data.remote.dto.GroupInvitationDto
import com.triggerconnect.app.data.remote.dto.GroupManageBody
import com.triggerconnect.app.data.remote.dto.GroupManageResponse
import com.triggerconnect.app.data.remote.dto.GroupMemberRowDto
import com.triggerconnect.app.data.remote.dto.GroupRowDto
import com.triggerconnect.app.domain.model.Group
import com.triggerconnect.app.domain.model.GroupInvitation
import com.triggerconnect.app.domain.model.GroupMember
import com.triggerconnect.app.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GroupRepositoryImpl(
    private val api: SupabaseApi,
    private val groupDao: GroupDao,
) : GroupRepository {

    override fun observeGroups(): Flow<List<Group>> =
        groupDao.observeGroups().map { list -> list.map { it.toDomain() } }

    override fun observeMembers(groupId: String): Flow<List<GroupMember>> =
        groupDao.observeMembers(groupId).map { list -> list.map { it.toDomain() } }

    override suspend fun refresh(): AppResult<Unit> =
        api.rpcList<GroupRowDto>(Constants.Rpc.MY_GROUPS)
            .onSuccess { rows -> groupDao.replaceAll(rows.map { it.toEntity() }) }
            .map { }

    override suspend fun refreshMembers(groupId: String): AppResult<Unit> =
        api.rpcList<GroupMemberRowDto>(
            name = Constants.Rpc.GROUP_MEMBERS,
            parameters = buildJsonObject { put("p_group_id", groupId) },
        ).onSuccess { rows -> groupDao.replaceMembers(groupId, rows.map { it.toEntity(groupId) }) }
            .map { }

    override suspend fun createGroup(
        name: String,
        description: String?,
        imageUrl: String?,
        requiresApproval: Boolean,
    ): AppResult<Group> {
        val result = api.callFunction<GroupManageBody, GroupManageResponse>(
            name = Constants.Functions.GROUP_MANAGE,
            body = GroupManageBody(
                action = "create",
                name = name.trim(),
                description = description?.trim()?.ifBlank { null },
                imageUrl = imageUrl?.trim()?.ifBlank { null },
                requiresApproval = requiresApproval,
            ),
        )
        return result.map { resp ->
            refresh()
            resp.group?.toEntity()?.toDomain()
                ?: return AppResult.Failure(AppError(AppErrorType.SERVER, "Group creation failed."))
        }
    }

    override suspend fun renameGroup(groupId: String, name: String): AppResult<Unit> =
        manage(GroupManageBody(action = "rename", groupId = groupId, name = name.trim()))

    override suspend fun leaveGroup(groupId: String): AppResult<Unit> =
        manage(GroupManageBody(action = "leave", groupId = groupId))

    override suspend fun deleteGroup(groupId: String): AppResult<Unit> =
        manage(GroupManageBody(action = "delete", groupId = groupId))

    override suspend fun removeMember(groupId: String, userId: String): AppResult<Unit> =
        manage(GroupManageBody(action = "removeMember", groupId = groupId, targetUserId = userId), groupId)

    override suspend fun setMemberRole(groupId: String, userId: String, makeAdmin: Boolean): AppResult<Unit> =
        manage(
            GroupManageBody(action = "setRole", groupId = groupId, targetUserId = userId, makeAdmin = makeAdmin),
            groupId,
        )

    override suspend fun reviewMember(groupId: String, userId: String, approve: Boolean): AppResult<Unit> =
        manage(
            GroupManageBody(
                action = "approveMember",
                groupId = groupId,
                targetUserId = userId,
                approve = approve,
            ),
            groupId,
        )

    override suspend fun createInvitation(groupId: String): AppResult<GroupInvitation> =
        api.callFunction<GroupInviteCreateBody, GroupInvitationDto>(
            name = Constants.Functions.GROUP_INVITE_CREATE,
            body = GroupInviteCreateBody(groupId = groupId),
        ).map { it.toDomain() }

    private suspend fun manage(body: GroupManageBody, refreshMembersFor: String? = null): AppResult<Unit> {
        val result = api.callFunction<GroupManageBody, GroupManageResponse>(
            name = Constants.Functions.GROUP_MANAGE,
            body = body,
        ).map { }
        if (result is AppResult.Success) {
            refresh()
            refreshMembersFor?.let { refreshMembers(it) }
        }
        return result
    }
}
