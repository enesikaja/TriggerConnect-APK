package com.triggerconnect.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResultTest {

    @Test
    fun map_transformsSuccess() {
        val result: AppResult<Int> = AppResult.Success(2)
        assertEquals(AppResult.Success(4), result.map { it * 2 })
    }

    @Test
    fun map_passesThroughFailure() {
        val failure = AppResult.Failure(AppError(AppErrorType.NETWORK, "offline"))
        val mapped = (failure as AppResult<Int>).map { it * 2 }
        assertTrue(mapped is AppResult.Failure)
    }

    @Test
    fun getOrNull_behaviour() {
        assertEquals(7, AppResult.Success(7).getOrNull())
        assertNull(AppResult.Failure(AppError(AppErrorType.UNKNOWN, "x")).getOrNull())
    }

    @Test
    fun onSuccess_onFailure_sideEffects() {
        var seen = 0
        AppResult.Success(1).onSuccess { seen++ }.onFailure { seen += 10 }
        assertEquals(1, seen)
        AppResult.Failure(AppError(AppErrorType.SERVER, "x")).onSuccess { seen++ }.onFailure { seen += 10 }
        assertEquals(11, seen)
    }
}
