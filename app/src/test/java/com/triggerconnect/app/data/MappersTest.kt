package com.triggerconnect.app.data

import com.triggerconnect.app.data.mapper.parseInstant
import com.triggerconnect.app.data.mapper.toDomain
import com.triggerconnect.app.data.mapper.toEntity
import com.triggerconnect.app.data.remote.dto.EventRowDto
import com.triggerconnect.app.domain.model.DestinationType
import com.triggerconnect.app.domain.model.EventDirection
import com.triggerconnect.app.domain.model.TriggerPriority
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {

    @Test
    fun parseInstant_fallsBackToEpochZeroOnGarbage() {
        assertEquals(0L, parseInstant("not-a-date").toEpochMilliseconds())
    }

    @Test
    fun parseInstant_parsesIso8601() {
        val iso = "2026-10-09T12:00:00Z"
        assertEquals(
            kotlinx.datetime.Instant.parse(iso).toEpochMilliseconds(),
            parseInstant(iso).toEpochMilliseconds(),
        )
    }

    @Test
    fun eventRowDto_mapsToDomainThroughEntity() {
        val dto = EventRowDto(
            eventId = "e1",
            senderId = "s1",
            senderName = "Alex",
            destinationType = "GROUP",
            destinationId = "g1",
            destinationName = "Team Alpha",
            content = "Please come to the meeting room.",
            priority = "URGENT",
            status = "DISPATCHED",
            createdAt = "2026-10-09T12:00:00Z",
            direction = "SENT",
            recipientCount = 5,
            acknowledgedCount = 2,
            myAcknowledgment = "NONE",
        )
        val domain = dto.toEntity().toDomain()
        assertEquals("e1", domain.eventId)
        assertEquals(DestinationType.GROUP, domain.destinationType)
        assertEquals(TriggerPriority.URGENT, domain.priority)
        assertEquals(EventDirection.SENT, domain.direction)
        assertEquals(5, domain.recipientCount)
        assertEquals(2, domain.acknowledgedCount)
    }
}
