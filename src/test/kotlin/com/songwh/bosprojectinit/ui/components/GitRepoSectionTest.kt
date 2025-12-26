package com.songwh.bosprojectinit.ui.components

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitRepoSectionTest {

    @Test
    fun testGetInvalidGitUrls() {
        val input = """
            https://github.com/user/repo1.git
            invalid-url
            https://github.com/user/repo2.git
            another-invalid
            
        """.trimIndent()

        val invalidUrls = GitRepoSection.getInvalidGitUrls(input)
        
        assertEquals(2, invalidUrls.size)
        assertTrue(invalidUrls.contains("invalid-url"))
        assertTrue(invalidUrls.contains("another-invalid"))
    }

    @Test
    fun testRemoveInvalidGitUrls() {
        val input = """
            https://github.com/user/repo1.git
            invalid-url
            https://github.com/user/repo2.git
            another-invalid
        """.trimIndent()

        val cleaned = GitRepoSection.removeInvalidGitUrls(input)
        val lines = cleaned.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        
        assertEquals(2, lines.size)
        assertTrue(lines.contains("https://github.com/user/repo1.git"))
        assertTrue(lines.contains("https://github.com/user/repo2.git"))
    }

    @Test
    fun testNormalizeGitUrls() {
        val input = """
            https://github.com/user/repo1.git
            invalid-url
            https://github.com/user/repo2.git
            
            another-invalid
        """.trimIndent()

        val normalized = GitRepoSection.normalizeGitUrls(input)
        val lines = normalized.split("\n").filter { it.isNotBlank() }
        
        // normalizeGitUrls 应该只保留有效的URL（这个函数用于初始化前的清理）
        assertEquals(2, lines.size)
        assertTrue(lines.contains("https://github.com/user/repo1.git"))
        assertTrue(lines.contains("https://github.com/user/repo2.git"))
    }

    @Test
    fun testCountValidGitUrls() {
        val input = """
            https://github.com/user/repo1.git
            invalid-url
            https://github.com/user/repo2.git
            https://github.com/user/repo1.git
            another-invalid
        """.trimIndent()

        val count = GitRepoSection.countValidGitUrls(input)
        
        // 应该只计数去重后的有效URL
        assertEquals(2, count)
    }

    @Test
    fun testGetInvalidGitUrlsWithEmptyLines() {
        val input = """
            https://github.com/user/repo1.git
            
            
            invalid-url
        """.trimIndent()

        val invalidUrls = GitRepoSection.getInvalidGitUrls(input)
        
        // 空行不应该被认为是无效URL
        assertEquals(1, invalidUrls.size)
        assertTrue(invalidUrls.contains("invalid-url"))
    }
}
