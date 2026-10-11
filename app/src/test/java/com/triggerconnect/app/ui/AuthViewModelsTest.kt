package com.triggerconnect.app.ui

import app.cash.turbine.test
import com.triggerconnect.app.core.AppError
import com.triggerconnect.app.core.AppErrorType
import com.triggerconnect.app.fake.FakeAuthRepository
import com.triggerconnect.app.ui.screens.login.LoginViewModel
import com.triggerconnect.app.ui.screens.welcome.WelcomeViewModel
import com.triggerconnect.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelsTest {

    @get:Rule val mainRule = MainDispatcherRule()

    @Test
    fun welcome_nameValidation() {
        val vm = WelcomeViewModel(FakeAuthRepository())
        vm.onNameChange("A")
        assertFalse(vm.state.value.valid)
        vm.onNameChange("Alex")
        assertTrue(vm.state.value.valid)
    }

    @Test
    fun welcome_register_emitsCode() = runTest {
        val vm = WelcomeViewModel(FakeAuthRepository(registerCode = "654321"))
        vm.onNameChange("Alex")
        vm.registered.test {
            vm.createAccount()
            assertEquals("654321", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun login_invalidCode_setsError_andClearsInput() = runTest {
        val repo = FakeAuthRepository(failLogin = AppError(AppErrorType.INVALID_INPUT, "bad code"))
        val vm = LoginViewModel(repo)
        vm.onCodeChange("000000") // triggers auto-login at 6 digits
        assertEquals("bad code", vm.state.value.error)
        assertEquals("", vm.state.value.code)
    }

    @Test
    fun login_valid_emitsLoggedIn() = runTest {
        val vm = LoginViewModel(FakeAuthRepository())
        vm.loggedIn.test {
            vm.onCodeChange("123456")
            awaitItem() // Unit
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(vm.state.value.error)
    }
}
