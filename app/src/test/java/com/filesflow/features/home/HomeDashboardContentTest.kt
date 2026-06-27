package com.filesflow.features.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDashboardContentTest {
    @Test
    fun dashboardStaticContentMatchesSpec() {
        assertEquals("FilesFlow", FilesFlowAppName)

        assertEquals(78, defaultStorageOverview.usedPercent)
        assertEquals("92.4 GB Used", defaultStorageOverview.usedLabel)
        assertEquals("128 GB Total", defaultStorageOverview.totalLabel)

        assertEquals(
            listOf("Images", "Videos", "Docs", "Downloads", "Music", "Apps"),
            defaultFileCategories().map { it.label },
        )

        assertEquals(
            listOf(
                "Quarterly Report" to "2.4 MB • 2h ago",
                "IMG_8421.jpg" to "4.8 MB • 5h ago",
                "Brand_Guidelines_v2.pdf" to "15.2 MB • Yesterday",
                "Project_Assets_Archive" to "245 MB • Oct 12",
            ),
            defaultRecentFiles().map { it.name to it.metadata },
        )
    }
}
