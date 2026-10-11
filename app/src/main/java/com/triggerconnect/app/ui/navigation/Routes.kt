package com.triggerconnect.app.ui.navigation

/** Centralised navigation routes. Argument routes expose a builder. */
object Routes {
    // Auth graph
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val CODE = "code/{loginCode}"
    fun code(loginCode: String) = "code/$loginCode"

    // Main tabs (show bottom navigation bar)
    const val HOME = "home"
    const val PEOPLE = "people"
    const val GROUPS = "groups"
    const val SCAN = "scan"
    const val HISTORY = "history"

    val tabRoutes = setOf(HOME, PEOPLE, GROUPS, SCAN, HISTORY)

    // Detail / modal routes
    const val PERSONAL_QR = "personal_qr"
    const val CREATE_GROUP = "create_group"
    const val GROUP_DETAILS = "group_details/{groupId}"
    fun groupDetails(groupId: String) = "group_details/$groupId"
    const val GROUP_INVITE = "group_invite/{groupId}"
    fun groupInvite(groupId: String) = "group_invite/$groupId"
    const val MESSAGE_EDITOR = "message_editor"
    const val TRIGGER_CONFIRM = "trigger_confirm"
    const val TRIGGER_DETAILS = "trigger_details/{eventId}"
    fun triggerDetails(eventId: String) = "trigger_details/$eventId"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"

    // Argument keys
    const val ARG_LOGIN_CODE = "loginCode"
    const val ARG_GROUP_ID = "groupId"
    const val ARG_EVENT_ID = "eventId"
}
