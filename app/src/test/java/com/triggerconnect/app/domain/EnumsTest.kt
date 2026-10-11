package com.triggerconnect.app.domain

import com.triggerconnect.app.domain.model.AcknowledgmentStatus
import com.triggerconnect.app.domain.model.DispatchStatus
import com.triggerconnect.app.domain.model.GroupRole
import com.triggerconnect.app.domain.model.TriggerPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnumsTest {

    @Test
    fun priority_fromRaw_isCaseInsensitive_andDefaultsNormal() {
        assertEquals(TriggerPriority.URGENT, TriggerPriority.fromRaw("urgent"))
        assertEquals(TriggerPriority.IMPORTANT, TriggerPriority.fromRaw("Important"))
        assertEquals(TriggerPriority.NORMAL, TriggerPriority.fromRaw("nonsense"))
        assertEquals(TriggerPriority.NORMAL, TriggerPriority.fromRaw(null))
    }

    @Test
    fun groupRole_permissions() {
        assertTrue(GroupRole.OWNER.canManageGroup)
        assertTrue(GroupRole.OWNER.canManageMembers)
        assertTrue(GroupRole.ADMIN.canManageMembers)
        assertFalse(GroupRole.ADMIN.canManageGroup)
        assertFalse(GroupRole.MEMBER.canManageMembers)
    }

    @Test
    fun dispatch_and_ack_defaults() {
        assertEquals(DispatchStatus.PENDING, DispatchStatus.fromRaw("?"))
        assertEquals(AcknowledgmentStatus.ACKNOWLEDGED, AcknowledgmentStatus.fromRaw("acknowledged"))
        assertEquals(AcknowledgmentStatus.NONE, AcknowledgmentStatus.fromRaw(null))
    }
}
