package com.triggerconnect.app.core

/** App-wide constants. Keep tunable limits here, not scattered across the code. */
object Constants {
    const val LOGIN_CODE_LENGTH = 6
    const val MESSAGE_MAX_LENGTH = 280
    const val GROUP_NAME_MAX_LENGTH = 60
    const val GROUP_MEMBER_LIMIT = 100
    const val DISPLAY_NAME_MAX_LENGTH = 40
    const val DISPLAY_NAME_MIN_LENGTH = 2

    /** Edge Function endpoint names (must match the supabase/functions folder names). */
    object Functions {
        const val REGISTER = "auth-register"
        const val LOGIN = "auth-login"
        const val REGISTER_DEVICE = "register-device"
        const val PERSONAL_INVITE_CREATE = "personal-invite-create"
        const val CONNECTION_REQUEST = "connection-request"
        const val CONNECTION_RESPOND = "connection-respond"
        const val GROUP_INVITE_CREATE = "group-invite-create"
        const val GROUP_MANAGE = "group-manage"
        const val INVITE_RESOLVE = "invite-resolve"
        const val GROUP_JOIN = "group-join"
        const val TRIGGER_DISPATCH = "trigger-dispatch"
        const val EVENT_ACKNOWLEDGE = "event-acknowledge"
        const val ACCOUNT_DELETE = "account-delete"
    }

    /** Postgres RPC names for reads (must match supabase/migrations functions). */
    object Rpc {
        const val MY_CONTACTS = "my_contacts"
        const val INCOMING_REQUESTS = "incoming_requests"
        const val MY_GROUPS = "my_groups"
        const val GROUP_MEMBERS = "group_members_of"
        const val MY_SAVED_MESSAGES = "my_saved_messages"
        const val MY_HISTORY = "my_history"
        const val EVENT_RECIPIENTS = "event_recipients_of"
    }

    /** Local secure-storage keys. */
    object SecureKeys {
        const val PREFS_FILE = "trigger_connect_secure_prefs"
        const val USER_ID = "user_id"
        const val DISPLAY_NAME = "display_name"
        const val AUTH_EMAIL = "auth_email"
        const val SESSION_ACCESS_TOKEN = "session_access_token"
        const val SESSION_REFRESH_TOKEN = "session_refresh_token"
    }
}
