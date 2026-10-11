package com.triggerconnect.app.core

/**
 * A small, explicit result type used across the domain/data boundary so that
 * ViewModels can render success / error / loading states without exceptions
 * leaking into the UI. [AppError] carries a user-safe message and a type the
 * UI can branch on (e.g. show "too many attempts" vs a generic error).
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

enum class AppErrorType {
    NETWORK,
    UNAUTHORIZED,
    RATE_LIMITED,
    INVALID_INPUT,
    NOT_FOUND,
    FORBIDDEN,
    CONFLICT,
    SERVER,
    UNKNOWN,
}

data class AppError(
    val type: AppErrorType,
    val message: String,
    val cause: Throwable? = null,
)

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(error)
    return this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data
