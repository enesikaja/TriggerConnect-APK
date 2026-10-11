package com.triggerconnect.app.ui

import app.cash.turbine.test
import com.triggerconnect.app.core.AppError
import com.triggerconnect.app.core.AppErrorType
import com.triggerconnect.app.domain.model.Recipient
import com.triggerconnect.app.fake.FakeNetworkMonitor
import com.triggerconnect.app.fake.FakeTriggerRepository
import com.triggerconnect.app.ui.composer.ComposerEvent
import com.triggerconnect.app.ui.composer.TriggerComposerViewModel
import com.triggerconnect.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TriggerComposerViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    @Test
    fun canSend_requiresRecipientAndMessage() {
        val vm = TriggerComposerViewModel(FakeTriggerRepository(), FakeNetworkMonitor(true))
        assertFalse(vm.state.value.canSend)
        vm.selectRecipient(Recipient.Person("p1", "Sam"))
        assertFalse(vm.state.value.canSend)
        vm.setMessage("Please come")
        assertTrue(vm.state.value.canSend)
    }

    @Test
    fun send_success_emitsSentAndClearsMessage() = runTest {
        val repo = FakeTriggerRepository()
        val vm = TriggerComposerViewModel(repo, FakeNetworkMonitor(true))
        vm.selectRecipient(Recipient.Person("p1", "Sam"))
        vm.setMessage("I need assistance")

        vm.events.test {
            vm.send()
            val event = awaitItem()
            assertTrue(event is ComposerEvent.Sent)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("", vm.state.value.message)
        assertEquals(1, repo.sendCount)
    }

    @Test
    fun send_whenOffline_emitsErrorAndDoesNotCallRepo() = runTest {
        val repo = FakeTriggerRepository()
        val vm = TriggerComposerViewModel(repo, FakeNetworkMonitor(false))
        vm.selectRecipient(Recipient.Person("p1", "Sam"))
        vm.setMessage("hi")

        vm.events.test {
            vm.send()
            assertTrue(awaitItem() is ComposerEvent.Error)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, repo.sendCount)
    }

    @Test
    fun send_failure_surfacesError() = runTest {
        val repo = FakeTriggerRepository(failWith = AppError(AppErrorType.FORBIDDEN, "nope"))
        val vm = TriggerComposerViewModel(repo, FakeNetworkMonitor(true))
        vm.selectRecipient(Recipient.GroupTarget("g1", "Team", 3))
        vm.setMessage("check update")

        vm.events.test {
            vm.send()
            val event = awaitItem()
            assertTrue(event is ComposerEvent.Error)
            assertEquals("nope", (event as ComposerEvent.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
