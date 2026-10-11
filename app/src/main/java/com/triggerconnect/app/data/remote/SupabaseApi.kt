package com.triggerconnect.app.data.remote

import com.triggerconnect.app.core.AppError
import com.triggerconnect.app.core.AppErrorType
import com.triggerconnect.app.core.AppResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Single choke point for all Supabase network I/O. Converts transport/HTTP
 * failures into user-safe [AppError]s so repositories and ViewModels never
 * touch raw exceptions.
 *
 * NOTE: Targets supabase-kt v3.0.x. If you upgrade the SDK and the
 * `functions.invoke` / `postgrest.rpc` signatures change, this is the only
 * file you need to adjust.
 */
class SupabaseApi(
    @PublishedApi internal val client: SupabaseClient,
    @PublishedApi internal val json: Json,
) {
    /** POST to an Edge Function with a JSON body, decoding the JSON response. */
    suspend inline fun <reified Req : Any, reified Res : Any> callFunction(
        name: String,
        body: Req,
    ): AppResult<Res> = runCatchingRemote {
        val response: HttpResponse = client.functions.invoke(function = name) {
            headers {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
            setBody(body)
        }
        parseResponse(response)
    }

    /** POST to an Edge Function with no request body. */
    suspend inline fun <reified Res : Any> callFunction(name: String): AppResult<Res> =
        runCatchingRemote {
            val response: HttpResponse = client.functions.invoke(function = name)
            parseResponse(response)
        }

    @PublishedApi
    internal suspend inline fun <reified Res : Any> parseResponse(response: HttpResponse): Res {
        if (response.status.isSuccess()) {
            return response.body<Res>()
        }
        // Non-2xx: try to extract a server-provided error code/message.
        val raw = runCatching { response.body<JsonObject>() }.getOrNull()
        val code = (raw?.get("code")?.toString()?.trim('"'))
        val message = raw?.get("error")?.toString()?.trim('"')
        throw RemoteHttpException(status = response.status.value, code = code, message = message)
    }

    /** Call a Postgres RPC returning a list of rows. */
    suspend inline fun <reified Res : Any> rpcList(
        name: String,
        parameters: JsonObject? = null,
    ): AppResult<List<Res>> = runCatchingRemote {
        val result = if (parameters != null) {
            client.postgrest.rpc(function = name, parameters = parameters)
        } else {
            client.postgrest.rpc(function = name)
        }
        result.decodeList<Res>()
    }

    /** Insert/Update/Delete helpers for owner-enforced tables (RLS protected). */
    fun table(name: String) = client.postgrest.from(name)

    /** Run an arbitrary suspend block against Supabase, mapping failures. */
    suspend fun <T> runRemote(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (e: RemoteHttpException) {
        AppResult.Failure(e.toAppError())
    } catch (e: Throwable) {
        AppResult.Failure(mapThrowable(e))
    }

    @PublishedApi
    internal inline fun <T> runCatchingRemote(block: () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (e: RemoteHttpException) {
        AppResult.Failure(e.toAppError())
    } catch (e: Throwable) {
        AppResult.Failure(mapThrowable(e))
    }

    val serializer: Json get() = json
}

/** Thrown internally when an Edge Function returns a non-2xx status. */
class RemoteHttpException(
    val status: Int,
    val code: String?,
    override val message: String?,
) : Exception(message)

fun RemoteHttpException.toAppError(): AppError {
    val type = when {
        code == "RATE_LIMITED" || status == 429 -> AppErrorType.RATE_LIMITED
        code == "INVALID_CODE" || status == 400 -> AppErrorType.INVALID_INPUT
        status == 401 -> AppErrorType.UNAUTHORIZED
        status == 403 -> AppErrorType.FORBIDDEN
        status == 404 -> AppErrorType.NOT_FOUND
        status == 409 -> AppErrorType.CONFLICT
        status in 500..599 -> AppErrorType.SERVER
        else -> AppErrorType.UNKNOWN
    }
    return AppError(type = type, message = message ?: defaultMessageFor(type))
}

fun mapThrowable(e: Throwable): AppError {
    val isNetwork = e is java.io.IOException ||
        e.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
        e.message?.contains("timeout", ignoreCase = true) == true
    val type = if (isNetwork) AppErrorType.NETWORK else AppErrorType.UNKNOWN
    return AppError(type = type, message = defaultMessageFor(type), cause = e)
}

private fun defaultMessageFor(type: AppErrorType): String = when (type) {
    AppErrorType.NETWORK -> "No internet connection. Check your network and try again."
    AppErrorType.UNAUTHORIZED -> "Your session has expired. Please sign in again."
    AppErrorType.RATE_LIMITED -> "Too many attempts. Please wait a moment and try again."
    AppErrorType.INVALID_INPUT -> "That didn't look right. Please check and try again."
    AppErrorType.NOT_FOUND -> "We couldn't find that."
    AppErrorType.FORBIDDEN -> "You don't have permission to do that."
    AppErrorType.CONFLICT -> "That action conflicts with the current state."
    AppErrorType.SERVER -> "Something went wrong on our side. Please try again."
    AppErrorType.UNKNOWN -> "Something went wrong. Please try again."
}
