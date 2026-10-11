package com.triggerconnect.app.ui

import com.triggerconnect.app.fake.FakeAuthRepository
import com.triggerconnect.app.fake.FakeTriggerRepository
import com.triggerconnect.app.domain.repository.ConnectionRepository
import com.triggerconnect.app.domain.repository.GroupRepository
import com.triggerconnect.app.messaging.FcmTokenRegistrar
import com.triggerconnect.app.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    @Test fun resumeRetriesRegistrationAfterOfflineSignIn() = runTest {
        val auth = FakeAuthRepository()
        val registrar = mockk<FcmTokenRegistrar>()
        coEvery { registrar.registerCurrentToken() } returns false
        val vm = MainViewModel(auth, registrar, mockk<ConnectionRepository>(relaxed = true),
            mockk<GroupRepository>(relaxed = true), FakeTriggerRepository())
        auth.login("123456")
        advanceUntilIdle()
        coVerify(exactly = 3) { registrar.registerCurrentToken() }

        coEvery { registrar.registerCurrentToken() } returns true
        vm.refreshAuthenticatedData()
        advanceUntilIdle()
        coVerify(exactly = 4) { registrar.registerCurrentToken() }

        auth.logout()
        vm.refreshAuthenticatedData()
        advanceUntilIdle()
        coVerify(exactly = 4) { registrar.registerCurrentToken() }
    }
}
