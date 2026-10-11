package com.triggerconnect.app.domain.model

/** Who/what a trigger is aimed at. */
enum class DestinationType { USER, GROUP }

/** Trigger priority — drives the notification channel used on delivery. */
enum class TriggerPriority {
    NORMAL, IMPORTANT, URGENT;

    companion object {
        fun fromRaw(raw: String?): TriggerPriority =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NORMAL
    }
}

/** Role of a member within a group. Permissions are enforced server-side; this
 *  mirror is only used to decide which controls to show in the UI. */
enum class GroupRole {
    OWNER, ADMIN, MEMBER;

    val canManageMembers: Boolean get() = this == OWNER || this == ADMIN
    val canManageGroup: Boolean get() = this == OWNER

    companion object {
        fun fromRaw(raw: String?): GroupRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MEMBER
    }
}

/** Lifecycle of a one-to-one connection. */
enum class ConnectionStatus { PENDING, ACCEPTED, BLOCKED;
    companion object {
        fun fromRaw(raw: String?): ConnectionStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PENDING
    }
}

/** Per-recipient dispatch state. NOTE: ACCEPTED means the push provider (FCM)
 *  accepted the message for delivery — it is NOT proof of device delivery. */
enum class DispatchStatus { PENDING, ACCEPTED, FAILED, SKIPPED;
    companion object {
        fun fromRaw(raw: String?): DispatchStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PENDING
    }
}

/** Whether a recipient has opened + acknowledged the event. */
enum class AcknowledgmentStatus { NONE, ACKNOWLEDGED;
    companion object {
        fun fromRaw(raw: String?): AcknowledgmentStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NONE
    }
}

/** Overall event status as computed by the backend. */
enum class EventStatus { CREATED, DISPATCHING, DISPATCHED, PARTIAL_FAILURE, FAILED;
    companion object {
        fun fromRaw(raw: String?): EventStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CREATED
    }
}

/** From the current user's perspective, did they send or receive the event? */
enum class EventDirection { SENT, RECEIVED }
