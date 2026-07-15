package com.filesflow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
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
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation.executeShellCommand(
            "appops set --uid $packageName MANAGE_EXTERNAL_STORAGE allow",
        ).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Browse files").performClick()
        composeRule.onNodeWithText("Browse Files").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Browse Files").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back to dashboard").performClick()
        composeRule.onNodeWithText("Find Files").assertIsDisplayed()
    }
}
