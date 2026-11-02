package com.visualtrace.ai.features

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.visualtrace.ai.core.designsystem.VisualTraceTheme
import com.visualtrace.ai.core.ui.MessageState
import com.visualtrace.ai.domain.model.MatchType
import com.visualtrace.ai.domain.model.Provenance
import com.visualtrace.ai.domain.model.ResultCategory
import com.visualtrace.ai.domain.model.SearchResultItem
import com.visualtrace.ai.features.auth.AuthViewModel
import com.visualtrace.ai.features.results.ResultCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ComponentsUiTest {
    @get:Rule val rule = createComposeRule()

    private fun item(mock: Boolean = false) = SearchResultItem(
        id = "r1", searchId = "s", providerKey = "reddit", alsoFoundBy = listOf("reddit"), platform = "Reddit",
        category = ResultCategory.SOCIAL, matchType = MatchType.EXACT, sourceUrl = "https://www.reddit.com/r/cars/1",
        imageUrl = null, thumbnailUrl = null, pageTitle = "Red car", description = null, similarityScore = 0.97f,
        similarityMethod = "opencv", publishedAtEpochMs = null, authorDisplayName = null, authorUsername = "car_spotter",
        profileUrl = "https://www.reddit.com/user/car_spotter", profileBio = null, websiteUrl = null, caption = null,
        frameTimestampMs = 3000, provenance = mapOf("author_username" to Provenance.PROVIDER), via = null, isMock = mock,
    )

    @Test fun messageStateShowsRetry() {
        var clicks = 0
        rule.setContent { VisualTraceTheme { MessageState("No internet", "Try again", actionLabel = "Retry", onAction = { clicks++ }) } }
        rule.onNodeWithText("No internet").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, clicks)
    }

    @Test fun resultCardShowsSimilarityMatchTypeAndProvenance() {
        rule.setContent { VisualTraceTheme { ResultCard(item(mock = true), saved = false, onView = {}, onSave = {}, onReport = {}, onCopied = {}) } }
        rule.onNodeWithText("Exact match").assertIsDisplayed()
        rule.onNodeWithText("MOCK").assertIsDisplayed()
        rule.onNodeWithText("Visual similarity 97%").assertIsDisplayed()
        rule.onNodeWithText("Matched frame at 0:03", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Public information").performClick()
        rule.onNodeWithText("@car_spotter").assertIsDisplayed()
        rule.onNodeWithText("From source").assertIsDisplayed()
    }

    @Test fun saveButtonReflectsState() {
        var saves = 0
        rule.setContent { VisualTraceTheme { ResultCard(item(), saved = true, onView = {}, onSave = { saves++ }, onReport = {}, onCopied = {}) } }
        rule.onNodeWithContentDescription("Saved").performClick()
        assertEquals(1, saves)
    }

    @Test fun authValidation() {
        assertTrue(AuthViewModel.isValidEmail("a@b.co"))
        assertFalse(AuthViewModel.isValidEmail("nope"))
        assertFalse(AuthViewModel.isValidPassword("short"))
        assertTrue(AuthViewModel.isValidPassword("long-enough"))
    }
}
