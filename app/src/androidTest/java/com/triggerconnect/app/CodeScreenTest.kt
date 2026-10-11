package com.triggerconnect.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.triggerconnect.app.ui.screens.code.CodeScreen
import com.triggerconnect.app.ui.theme.TriggerConnectTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CodeScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun codeScreen_showsDigits_andContinueWorks() {
        var continued = false
        composeRule.setContent {
            TriggerConnectTheme {
                CodeScreen(loginCode = "135790", onContinue = { continued = true })
            }
        }
        // Each digit is rendered in its own box.
        composeRule.onNodeWithText("1").assertIsDisplayed()
        composeRule.onNodeWithText("3").assertIsDisplayed()
        composeRule.onNodeWithText("0").assertIsDisplayed()

        composeRule.onNodeWithText("I've saved it — continue").performClick()
        assertTrue(continued)
    }
}
