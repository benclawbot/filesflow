package com.filesflow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilesFlowSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardLaunchesAndBrowseNavigationSurvivesRecreation() {
        composeRule.onNodeWithText("Find Files").assertIsDisplayed()
        composeRule.onNodeWithText("Images").assertIsDisplayed()

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand(
            "appops set ${instrumentation.targetContext.packageName} MANAGE_EXTERNAL_STORAGE allow",
        ).use { descriptor ->
            descriptor.fileDescriptor.sync()
        }
        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithContentDescription("Browse files").performClick()
        composeRule.onNodeWithText("Browse Files").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText("Browse Files").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back to dashboard").performClick()
        composeRule.onNodeWithText("Find Files").assertIsDisplayed()
    }
}
