package com.filesflow.features.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileManagerModelsTest {
    private fun file(
        id: String,
        path: String? = "/storage/emulated/0/Download/$id.txt",
        source: FileSource = FileSource.DirectFile,
        isDirectory: Boolean = false,
    ) = FilesFlowFile(
        id = id,
        name = id,
        metadata = "",
        uri = null,
        path = path,
        mimeType = "text/plain",
        sizeBytes = 1L,
        modifiedAtMillis = 1L,
        source = source,
        isDirectory = isDirectory,
    )

    @Test
    fun toggledSelectionAddsAndRemovesIds() {
        val item = file("one")
        assertEquals(setOf("one"), toggledSelectedFileIds(emptySet(), item))
        assertEquals(emptySet<String>(), toggledSelectedFileIds(setOf("one"), item))
    }

    @Test
    fun selectAllOnlyUsesVisibleItems() {
        val visible = listOf(file("one"), file("two"))
        assertEquals(setOf("one", "two"), selectAllVisibleFileIds(visible))
        assertTrue(isAllVisibleSelected(visible, setOf("one", "two", "hidden")))
        assertFalse(isAllVisibleSelected(emptyList(), emptySet()))
        assertFalse(isAllVisibleSelected(visible, setOf("one")))
    }

    @Test
    fun categoryFiltersAreDistinctSortedAndApplied() {
        val files = listOf(
            file("b", "/storage/emulated/0/Pictures/Zeta/b.jpg", FileSource.MediaStore),
            file("a", "/storage/emulated/0/Pictures/Alpha/a.jpg", FileSource.MediaStore),
            file("a2", "/storage/emulated/0/Pictures/Alpha/a2.jpg", FileSource.MediaStore),
        )

        val filters = categoryFolderFilters(files)
        assertEquals(listOf("Alpha", "Zeta"), filters.map { it.name })
        assertEquals(listOf("a", "a2"), filesForCategoryFolder(files, filters.first().id).map { it.id })
        assertEquals(files, filesForCategoryFolder(files, null))
    }

    @Test
    fun destinationOnlyResolvesFolderModes() {
        val root = file("root", path = "/storage/emulated/0", isDirectory = true)
        assertEquals(root, destinationFolderForBrowseMode(BrowseMode.Folder(null, "Browse Files"), root))
        assertNull(destinationFolderForBrowseMode(BrowseMode.Home, root))
        assertNull(destinationFolderForBrowseMode(BrowseMode.Search("pdf"), root))

        val nested = destinationFolderForBrowseMode(
            BrowseMode.Folder(null, "Downloads", path = "/storage/emulated/0/Download"),
            root,
        )
        assertEquals("Downloads", nested?.name)
        assertEquals("/storage/emulated/0/Download", nested?.path)
        assertTrue(nested?.isDirectory == true)
    }

    @Test
    fun favoriteFolderIdsRemainStableBySource() {
        assertEquals("file:/storage/emulated/0/Download/one.txt", favoriteFolderIdFor(file("one")))
        assertEquals("app:pkg", favoriteFolderIdFor(file("pkg", path = null, source = FileSource.AppPackage)))
    }
}
