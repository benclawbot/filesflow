package com.filesflow.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Environment
import com.filesflow.features.home.FileCategorySummary
import com.filesflow.features.home.FileCategoryType
import com.filesflow.features.home.FileSource
import com.filesflow.features.home.FilesFlowFile
import com.filesflow.features.home.formatFileMetadata
import com.filesflow.features.home.inferCategoryType
import java.io.File

/**
 * Durable, atomic index of files visible through Android's broad shared-storage access.
 *
 * A complete breadth-first scan is committed as one SQLite transaction. If Android stops
 * the process or a directory becomes unreadable, the previous complete generation remains
 * available. Traversal keeps only directory paths in memory; file metadata is streamed
 * directly into SQLite.
 */
internal class DirectStorageIndex(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE files (
                path TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                name_lower TEXT NOT NULL,
                parent_path TEXT NOT NULL,
                category TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                modified_at_millis INTEGER NOT NULL,
                generation INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX files_category_modified ON files(category, modified_at_millis DESC)")
        db.execSQL("CREATE INDEX files_name_lower ON files(name_lower)")
        db.execSQL("CREATE INDEX files_generation ON files(generation)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS files")
        onCreate(db)
    }

    fun invalidate() {
        preferences.edit().remove(KEY_LAST_COMPLETE_SCAN).apply()
    }

    fun ensureFresh(maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS): Boolean {
        @Suppress("DEPRECATION")
        val root = Environment.getExternalStorageDirectory()
        if (!root.isDirectory || !root.canRead()) return false

        val now = System.currentTimeMillis()
        val lastCompleteScan = preferences.getLong(KEY_LAST_COMPLETE_SCAN, 0L)
        val indexedRoot = preferences.getString(KEY_INDEXED_ROOT, null)
        if (indexedRoot == root.absolutePath && now - lastCompleteScan in 0 until maxAgeMillis) {
            return true
        }
        return rebuild(root, now)
    }

    fun categorySummaries(): Map<FileCategoryType, FileCategorySummary> {
        val summaries = mutableMapOf<FileCategoryType, FileCategorySummary>()
        readableDatabase.rawQuery(
            "SELECT category, COUNT(*), COALESCE(SUM(size_bytes), 0) FROM files GROUP BY category",
            emptyArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val type = runCatching { FileCategoryType.valueOf(cursor.getString(0)) }.getOrNull() ?: continue
                if (type == FileCategoryType.Apps) continue
                summaries[type] = FileCategorySummary(
                    type = type,
                    fileCount = cursor.getLong(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    totalBytes = cursor.getLong(2),
                )
            }
        }
        return summaries
    }

    fun listCategory(
        type: FileCategoryType,
        limit: Int = StorageIndexQueryPolicy.CATEGORY_RESULT_LIMIT,
    ): List<FilesFlowFile> {
        if (type == FileCategoryType.Apps) return emptyList()
        return queryFiles(
            selection = "category = ?",
            selectionArgs = arrayOf(type.name),
            orderBy = "modified_at_millis DESC, name_lower ASC",
            limit = StorageIndexQueryPolicy.boundedLimit(
                requested = limit,
                maximum = StorageIndexQueryPolicy.CATEGORY_RESULT_LIMIT,
            ),
        )
    }

    fun search(
        query: String,
        limit: Int = StorageIndexQueryPolicy.SEARCH_RESULT_LIMIT,
    ): List<FilesFlowFile> {
        val normalized = StorageIndexQueryPolicy.normalizeSearchQuery(query)
        if (normalized.isBlank()) return emptyList()
        return queryFiles(
            selection = "name_lower LIKE ? ESCAPE '\\'",
            selectionArgs = arrayOf(StorageIndexQueryPolicy.containsPattern(normalized)),
            orderBy = "modified_at_millis DESC, name_lower ASC",
            limit = StorageIndexQueryPolicy.boundedLimit(
                requested = limit,
                maximum = StorageIndexQueryPolicy.SEARCH_RESULT_LIMIT,
            ),
        )
    }

    private fun rebuild(root: File, generation: Long): Boolean {
        val db = writableDatabase
        val pending = ArrayDeque<File>()
        pending.add(root)
        var indexedFiles = 0L

        return runCatching {
            db.beginTransaction()
            try {
                while (pending.isNotEmpty()) {
                    val directory = pending.removeFirst()
                    directory.listFilesSafely().forEach { child ->
                        when {
                            child.isDirectory -> pending.add(child)
                            child.isFile -> {
                                upsert(db, child, generation)
                                indexedFiles += 1
                            }
                        }
                    }
                }
                db.delete(TABLE_FILES, "generation != ?", arrayOf(generation.toString()))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            preferences.edit()
                .putLong(KEY_LAST_COMPLETE_SCAN, generation)
                .putLong(KEY_INDEXED_FILE_COUNT, indexedFiles)
                .putString(KEY_INDEXED_ROOT, root.absolutePath)
                .apply()
            true
        }.getOrElse { false }
    }

    private fun upsert(db: SQLiteDatabase, file: File, generation: Long) {
        val path = file.absolutePath
        val category = inferCategoryType(file.name, mimeType = null, path = path)
        val values = ContentValues().apply {
            put("path", path)
            put("name", file.name.ifBlank { path })
            put("name_lower", file.name.lowercase())
            put("parent_path", file.parent.orEmpty())
            put("category", category.name)
            put("size_bytes", file.length().coerceAtLeast(0L))
            put("modified_at_millis", file.lastModified().coerceAtLeast(0L))
            put("generation", generation)
        }
        db.insertWithOnConflict(TABLE_FILES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun queryFiles(
        selection: String,
        selectionArgs: Array<String>,
        orderBy: String,
        limit: Int,
    ): List<FilesFlowFile> {
        val files = mutableListOf<FilesFlowFile>()
        readableDatabase.query(
            TABLE_FILES,
            arrayOf("path", "name", "size_bytes", "modified_at_millis"),
            selection,
            selectionArgs,
            null,
            null,
            orderBy,
            limit.toString(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val path = cursor.getString(0)
                val name = cursor.getString(1)
                val size = cursor.getLong(2)
                val modified = cursor.getLong(3)
                files += FilesFlowFile(
                    id = "file-$path",
                    name = name,
                    metadata = formatFileMetadata(size, modified),
                    uri = null,
                    path = path,
                    mimeType = null,
                    sizeBytes = size,
                    modifiedAtMillis = modified,
                    source = FileSource.DirectFile,
                )
            }
        }
        return files
    }

    private fun File.listFilesSafely(): List<File> {
        return runCatching { listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())
    }

    companion object {
        private const val DATABASE_NAME = "direct-storage-index.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_FILES = "files"
        private const val PREFERENCES_NAME = "filesflow-direct-storage-index"
        private const val KEY_LAST_COMPLETE_SCAN = "last-complete-scan"
        private const val KEY_INDEXED_FILE_COUNT = "indexed-file-count"
        private const val KEY_INDEXED_ROOT = "indexed-root"
        private const val DEFAULT_MAX_AGE_MILLIS = 5 * 60 * 1000L
    }
}
