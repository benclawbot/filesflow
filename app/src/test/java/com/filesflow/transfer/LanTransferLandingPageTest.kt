package com.filesflow.transfer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanTransferLandingPageTest {
    @Test
    fun landing_page_lists_files_and_safe_relative_download_paths() {
        val html = LanTransferLandingPage.render(
            listOf(
                LanTransferLandingPage.Item("photo.jpg", 1536L, "/t/token/0"),
                LanTransferLandingPage.Item("notes.txt", 0L, "/t/token/1"),
            ),
        )

        assertTrue(html.contains("2 files available"))
        assertTrue(html.contains("photo.jpg"))
        assertTrue(html.contains("1.5 KB"))
        assertTrue(html.contains("href=\"/t/token/0\""))
        assertTrue(html.contains("Size unavailable"))
        assertTrue(html.contains("noindex,nofollow,noarchive"))
    }

    @Test
    fun landing_page_escapes_untrusted_names_and_attributes() {
        val html = LanTransferLandingPage.render(
            listOf(LanTransferLandingPage.Item("<script>alert('x')</script>.txt", 1L, "/t/token/0\" onclick=\"bad")),
        )

        assertFalse(html.contains("<script>"))
        assertFalse(html.contains("onclick=\"bad"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("&quot; onclick=&quot;bad"))
    }
}
