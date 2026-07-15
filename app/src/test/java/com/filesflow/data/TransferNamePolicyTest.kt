package com.filesflow.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TransferNamePolicyTest {
    @Test
    fun `preserves file extension when adding suffix`() {
        val parts = TransferNamePolicy.parts("report.final.pdf", preserveExtension = true)
        assertEquals("report.final", parts.base)
        assertEquals(".pdf", parts.extension)
        assertEquals("report.final (2).pdf", TransferNamePolicy.withSuffix(parts, 2))
    }

    @Test
    fun `does not treat leading dot as extension separator`() {
        val parts = TransferNamePolicy.parts(".nomedia", preserveExtension = true)
        assertEquals(".nomedia", parts.base)
        assertEquals("", parts.extension)
    }

    @Test
    fun `keeps complete folder name when suffixing`() {
        val parts = TransferNamePolicy.parts("Photos.2026", preserveExtension = false)
        assertEquals("Photos.2026 (1)", TransferNamePolicy.withSuffix(parts, 1))
    }
}
