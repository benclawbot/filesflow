package com.filesflow.features.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDashboardContentTest {
    @Test
    fun portraitLayoutUsesStatusSafeTopBarTokens() {
        assertEquals(72, FilesFlowTopBarContentHeightDp)
        assertEquals(24, FilesFlowHorizontalPaddingDp)
        assertEquals(393, FilesFlowPortraitWidthDp)
    }

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
                "Quarterly Report" to "2.4 MB - 2h ago",
                "IMG_8421.jpg" to "4.8 MB - 5h ago",
                "Brand_Guidelines_v2.pdf" to "15.2 MB - Yesterday",
                "Project_Assets_Archive" to "245 MB - Oct 12",
            ),
            defaultRecentFiles().map { it.name to it.metadata },
        )
    }

    @Test
    fun fileMetadataFormattingUsesHumanReadableUnits() {
        assertEquals("0 B", formatBytes(-1))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 MB", formatBytes(1_572_864))
    }

    @Test
    fun categoryInferenceUsesMimeTypesNamesAndPaths() {
        assertEquals(FileCategoryType.Images, inferCategoryType("photo.jpg", "image/jpeg"))
        assertEquals(FileCategoryType.Videos, inferCategoryType("clip.mp4", "video/mp4"))
        assertEquals(FileCategoryType.Music, inferCategoryType("song.mp3", "audio/mpeg"))
        assertEquals(FileCategoryType.Downloads, inferCategoryType("statement.bin", null, "Download/statement.bin"))
        assertEquals(FileCategoryType.Apps, inferCategoryType("demo.apk", null))
        assertEquals(FileCategoryType.Docs, inferCategoryType("notes.pdf", "application/pdf"))
    }

    @Test
    fun previewSummariesCoverTheSixDashboardCategories() {
        assertEquals(FileCategoryType.entries.toList(), previewFileCategorySummaries().map { it.type })
        assertTrue(previewRecentFiles().all { it.name.isNotBlank() && it.metadata.isNotBlank() })
    }
}
