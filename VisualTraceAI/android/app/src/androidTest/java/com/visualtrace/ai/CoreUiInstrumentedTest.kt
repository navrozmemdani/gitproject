package com.visualtrace.ai

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.visualtrace.ai.core.designsystem.VisualTraceTheme
import com.visualtrace.ai.core.ui.GradientButton
import com.visualtrace.ai.core.ui.VisualTraceLogo
import com.visualtrace.ai.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device/emulator UI tests (run with ./gradlew connectedDebugAndroidTest). */
@RunWith(AndroidJUnit4::class)
class CoreUiInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun searchButtonClicksAndDisables() {
        var clicks = 0
        rule.setContent {
            VisualTraceTheme(ThemeMode.DARK) {
                GradientButton("Search", onClick = { clicks++ })
                VisualTraceLogo()
            }
        }
        rule.onNodeWithText("Search").performClick()
        assertEquals(1, clicks)
        rule.onNodeWithContentDescription("VisualTrace AI logo").assertExists()
    }

    @Test fun disabledButtonIsNotClickable() {
        rule.setContent { VisualTraceTheme(ThemeMode.LIGHT) { GradientButton("Search", onClick = {}, enabled = false) } }
        rule.onNodeWithText("Search").assertIsNotEnabled()
    }
}
