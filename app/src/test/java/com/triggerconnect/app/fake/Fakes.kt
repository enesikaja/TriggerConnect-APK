package com.triggerconnect.app.fake

import com.triggerconnect.app.core.AppError
import com.triggerconnect.app.core.AppErrorType
import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.core.network.NetworkMonitor
import com.triggerconnect.app.domain.model.DestinationType
import com.triggerconnect.app.domain.model.EventDirection
import com.triggerconnect.app.domain.model.EventRecipient
import com.triggerconnect.app.domain.model.EventStatus
import com.triggerconnect.app.domain.model.TriggerEvent
import com.triggerconnect.app.domain.model.TriggerPriority
import com.triggerconnect.app.domain.model.UserProfile
import com.triggerconnect.app.domain.repository.AuthRepository
import com.triggerconnect.app.domain.repository.AuthState
import com.triggerconnect.app.domain.repository.RegistrationResult
import com.triggerconnect.app.domain.repository.TriggerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock

class FakeNetworkMonitor(online: Boolean) : NetworkMonitor {
    override val isOnline: Flow<Boolean> = flowOf(online)
}

class FakeTriggerRepository(
    var failWith: AppError? = null,
) : TriggerRepository {

    var lastClientEventId: String? = null
    var sendCount = 0

    override suspend fun sendTrigger(
        clientEventId: String,
        destinationType: DestinationType,
        destinationId: String,
        content: String,
        priority: TriggerPriority,
        notifySelf: Boolean,
    ): AppResult<TriggerEvent> {
        sendCount++
        lastClientEventId = clientEventId
        failWith?.let { return AppResult.Failure(it) }
        return AppResult.Success(
            TriggerEvent(
                eventId = "evt-$clientEventId",
                senderId = "me",
                senderName = "Me",
                destinationType = destinationType,
                destinationId = destinationId,
                destinationName = "Target",
                content = content,
                priority = priority,
                status = EventStatus.DISPATCHED,
                createdAt = Clock.System.now(),
                direction = EventDirection.SENT,
            ),
        )
    }

    override fun observeHistory(): Flow<List<TriggerEvent>> = flowOf(emptyList())
    override suspend fun refreshHistory(): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun eventDetail(eventId: String): AppResult<Pair<TriggerEvent, List<EventRecipient>>> =
        AppResult.Failure(AppError(AppErrorType.NOT_FOUND, "not found"))
    override suspend fun acknowledge(eventId: String): AppResult<Unit> = AppResult.Success(Unit)
}

class FakeAuthRepository(
    var registerCode: String = "123456",
    var failRegister: AppError? = null,
    var failLogin: AppError? = null,
) : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState

    override suspend fun bootstrap() {}

    override suspend fun register(displayName: String): AppResult<RegistrationResult> {
        failRegister?.let { return AppResult.Failure(it) }
        val profile = UserProfile("u1", displayName)
        _authState.value = AuthState.Authenticated(profile)
        return AppResult.Success(RegistrationResult(profile, registerCode))
    }

    override suspend fun login(code: String): AppResult<UserProfile> {
        failLogin?.let { return AppResult.Failure(it) }
        val profile = UserProfile("u1", "Alex")
        _authState.value = AuthState.Authenticated(profile)
        return AppResult.Success(profile)
    }

    override suspend fun logout() { _authState.value = AuthState.Unauthenticated }
    override fun currentUserId(): String? = "u1"
}
