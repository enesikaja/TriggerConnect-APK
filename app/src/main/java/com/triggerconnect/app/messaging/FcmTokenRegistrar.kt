package com.triggerconnect.app.messaging

import com.google.firebase.messaging.FirebaseMessaging
import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.domain.repository.UserRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Fetches the current FCM registration token and registers it with the
 * backend. Called after a successful sign-in. Safe to call when Firebase is
 * not configured — it simply no-ops.
 */
@Singleton
class FcmTokenRegistrar @Inject constructor(
    private val userRepository: UserRepository,
) {
    suspend fun registerCurrentToken(): Boolean {
        val token = try {
            currentToken()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
        return userRepository.registerDeviceToken(token) is AppResult.Success
    }

    suspend fun unregisterCurrentToken(): Boolean {
        val token = runCatching { currentToken() }.getOrNull() ?: return false
        return userRepository.unregisterDeviceToken(token) is AppResult.Success
    }

    private suspend fun currentToken(): String = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> if (cont.isActive) cont.resume(token) }
            .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
    }
}
