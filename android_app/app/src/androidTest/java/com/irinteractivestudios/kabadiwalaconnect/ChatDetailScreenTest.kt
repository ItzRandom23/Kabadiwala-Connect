package com.irinteractivestudios.kabadiwalaconnect

import android.graphics.Point
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ChatMessageDto
import com.irinteractivestudios.kabadiwalaconnect.data.remote.ConversationDto
import com.irinteractivestudios.kabadiwalaconnect.ui.screens.future.ChatDetailScreen
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatDetailScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val conversation = ConversationDto(id = "chat-1", collectorId = "collector", recyclerId = "household", type = "PICKUP")

    @Test
    fun labelsBothSendersAndClearsDraftReminderAfterSend() {
        var suggestion by mutableStateOf<String?>("Suggested reply")
        var sent = ""
        composeRule.setContent {
            KabadiwalaConnectTheme {
                ChatDetailScreen(
                    conversation = conversation,
                    messages = listOf(
                        ChatMessageDto(id = "1", senderId = "household", senderRole = "HOUSEHOLD", body = "Hello"),
                        ChatMessageDto(id = "2", senderId = "collector", senderRole = "COLLECTOR", body = "On my way")
                    ),
                    sending = false,
                    currentAccountId = "collector",
                    onSend = { sent = it; suggestion = null },
                    draftSuggestion = suggestion
                )
            }
        }

        composeRule.onNodeWithText("Household").assertIsDisplayed()
        composeRule.onNodeWithText("You").assertIsDisplayed()
        composeRule.onNodeWithText("Review the draft before sending.").assertIsDisplayed()
        composeRule.onNodeWithText("Send").performClick()
        composeRule.runOnIdle { assertEquals("Suggested reply", sent) }
        composeRule.onNodeWithText("Review the draft before sending.").assertDoesNotExist()
    }

    @Test
    fun erasingSuggestedTextRemovesDraftReminder() {
        var suggestion by mutableStateOf<String?>("Suggested reply")
        composeRule.setContent {
            KabadiwalaConnectTheme {
                ChatDetailScreen(
                    conversation = conversation,
                    messages = emptyList(),
                    sending = false,
                    onSend = {},
                    draftSuggestion = suggestion,
                    onDraftCleared = { suggestion = null }
                )
            }
        }

        composeRule.onNodeWithText("Suggested reply").performTextClearance()
        composeRule.onNodeWithText("Review the draft before sending.").assertDoesNotExist()
    }

    @Test
    fun sendButtonStaysAboveOpenKeyboard() {
        composeRule.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(composeRule.activity.window, false)
            composeRule.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        composeRule.setContent {
            KabadiwalaConnectTheme {
                ChatDetailScreen(conversation = conversation, messages = emptyList(), sending = false, onSend = {})
            }
        }

        composeRule.onNodeWithText("Message").performClick()
        composeRule.onNodeWithText("Message").performTextInput("Hello")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            (ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
                ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0) > 0
        }

        val imeBottom = ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)!!
            .getInsets(WindowInsetsCompat.Type.ime()).bottom
        val screenSize = Point().also { composeRule.activity.windowManager.defaultDisplay.getRealSize(it) }
        val sendBottom = composeRule.onNodeWithText("Send").fetchSemanticsNode().boundsInWindow.bottom
        assertTrue("Send button is behind the keyboard", sendBottom <= screenSize.y - imeBottom)
    }
}
