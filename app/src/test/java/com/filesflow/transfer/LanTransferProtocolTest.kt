package com.filesflow.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanTransferProtocolTest {
    @Test
    fun tokens_are_long_random_hex_values() {
        val first = LanTransferProtocol.newToken()
        val second = LanTransferProtocol.newToken()

        assertEquals(48, first.length)
        assertTrue(first.all { it in "0123456789abcdef" })
        assertFalse(first == second)
    }

    @Test
    fun landing_path_accepts_only_exact_session_token() {
        val token = "abc123"

        assertEquals("/t/$token", LanTransferProtocol.landingPath(token))
        assertTrue(LanTransferProtocol.isLandingRequestPath("/t/$token", token))
        assertTrue(LanTransferProtocol.isLandingRequestPath("/t/$token/?view=1", token))
        assertFalse(LanTransferProtocol.isLandingRequestPath("/t/wrong", token))
        assertFalse(LanTransferProtocol.isLandingRequestPath("/t/$token/0", token))
    }

    @Test
    fun request_path_accepts_only_exact_session_and_bounded_index() {
        val token = "abc123"

        assertEquals(0, LanTransferProtocol.isValidRequestPath("/t/$token/0", token, 2))
        assertEquals(1, LanTransferProtocol.isValidRequestPath("/t/$token/1?download=1", token, 2))
        assertNull(LanTransferProtocol.isValidRequestPath("/t/wrong/0", token, 2))
        assertNull(LanTransferProtocol.isValidRequestPath("/t/$token/2", token, 2))
        assertNull(LanTransferProtocol.isValidRequestPath("/t/$token/../0", token, 2))
    }

    @Test
    fun file_names_are_safe_for_headers_and_urls() {
        assertEquals("bad_name_'quoted'.txt", LanTransferProtocol.safeHeaderFileName("bad\rname\n\"quoted\".txt"))
        assertEquals("my%20file%20%C3%A9.txt", LanTransferProtocol.encodedFileName("my file é.txt"))
        assertEquals("download", LanTransferProtocol.safeHeaderFileName(""))
    }
}
