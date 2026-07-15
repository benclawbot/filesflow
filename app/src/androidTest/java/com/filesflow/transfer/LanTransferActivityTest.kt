package com.filesflow.transfer

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanTransferActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<LanTransferActivity>()

    @Test
    fun selectedFileStartsQrSessionAndCanBeStopped() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "lan-transfer-test.txt").apply {
            writeText("FilesFlow LAN transfer smoke test")
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(context, LanTransferActivity::class.java).apply {
            putStringArrayListExtra(LanTransferActivity.EXTRA_URIS, arrayListOf(uri.toString()))
            putStringArrayListExtra(LanTransferActivity.EXTRA_NAMES, arrayListOf(file.name))
            putStringArrayListExtra(LanTransferActivity.EXTRA_MIME_TYPES, arrayListOf("text/plain"))
            putExtra(LanTransferActivity.EXTRA_SIZES, longArrayOf(file.length()))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.intent.replaceExtras(intent)
            activity.recreate()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Copy link").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Send with FilesFlow").assertIsDisplayed()
        composeRule.onNodeWithText("Scan this QR code on the receiving device:").assertIsDisplayed()
        composeRule.onNodeWithTag("transfer-qr-code").assertIsDisplayed()
        composeRule.onNodeWithText(file.name).assertIsDisplayed()
        composeRule.onNodeWithText("Copy link").assertIsDisplayed()
        composeRule.onNodeWithText("Share link").assertIsDisplayed()
        composeRule.onNodeWithText("Stop transfer").performClick()
    }
}
