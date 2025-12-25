package com.songwh.bosprojectinit.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitRepoSectionSecurityTest {

    @Test
    fun testNormalizeGitUrlsFiltersUnsafeUrls() {
        val input = """
            https://example.com/repo1.git
            file:///c:/windows
            https://example.com/repo2.git?token=abcd
            -u https://example.com/repo3.git
            git@github.com:user/repo4.git
            https://example.com/repo5.git
            https://example.com/repo5.git
        """.trimIndent()

        val normalized = GitRepoSection.normalizeGitUrls(input)
        val lines = normalized.lines().filter { it.isNotBlank() }

        assertEquals(5, lines.size)
        assertTrue(lines.contains("https://example.com/repo1.git"))
        assertTrue(lines.contains("https://example.com/repo2.git?token=abcd"))
        assertTrue(lines.contains("git@github.com:user/repo4.git"))
        assertTrue(lines.contains("https://example.com/repo5.git"))
    }
}
