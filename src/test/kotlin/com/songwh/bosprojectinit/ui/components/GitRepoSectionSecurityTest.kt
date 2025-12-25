package com.songwh.bosprojectinit.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitRepoSectionSecurityTest {

    @Test
    fun `test isValidGitUrl with normal URLs`() {
        assertTrue(GitRepoSection.isValidGitUrl("https://github.com/user/repo.git"))
        assertTrue(GitRepoSection.isValidGitUrl("http://github.com/user/repo.git"))
        assertTrue(GitRepoSection.isValidGitUrl("git@github.com:user/repo.git"))
        assertTrue(GitRepoSection.isValidGitUrl("https://gitlab.com/group/subgroup/repo.git"))
        assertTrue(GitRepoSection.isValidGitUrl("https://github.com/user/my.repo.git")) // 测试包含点号的仓库名
    }

    @Test
    fun `test isValidGitUrl with path traversal attacks`() {
        assertFalse(GitRepoSection.isValidGitUrl("https://github.com/user/../../../etc/passwd"))
        assertFalse(GitRepoSection.isValidGitUrl("https://github.com/user/repo/../etc/passwd"))
        assertFalse(GitRepoSection.isValidGitUrl("https://github.com/user/repo/../../secret"))
        assertFalse(GitRepoSection.isValidGitUrl("git@github.com:user/../../../secret.git"))
    }

    @Test
    fun `test isValidGitUrl with malicious command injection`() {
        assertFalse(GitRepoSection.isValidGitUrl("https://github.com/user/repo.git;rm -rf ."))
        assertFalse(GitRepoSection.isValidGitUrl("https://github.com/user/repo.git && echo test"))
        assertFalse(GitRepoSection.isValidGitUrl("https://github.com/user/repo.git | cat /etc/passwd"))
        assertFalse(GitRepoSection.isValidGitUrl("git@github.com:user/repo.git;rm -rf ."))
    }

    @Test
    fun `test isValidGitUrl with empty or blank input`() {
        assertFalse(GitRepoSection.isValidGitUrl(""))
        assertFalse(GitRepoSection.isValidGitUrl("   "))
        assertFalse(GitRepoSection.isValidGitUrl("\t\n"))
    }

    @Test
    fun `test normalizeGitUrls with malicious inputs`() {
        val input = """
            https://github.com/user/repo1.git
            
            https://github.com/user/../../../etc/passwd
            git@github.com:user/repo2.git
            https://github.com/user/repo3.git;rm -rf .
        """.trimIndent()
        
        val normalized = GitRepoSection.normalizeGitUrls(input)
        val lines = normalized.split("\n")
        
        // Only the valid URLs should remain
        assertEquals(2, lines.size)
        assertTrue(lines.contains("https://github.com/user/repo1.git"))
        assertTrue(lines.contains("git@github.com:user/repo2.git"))
    }
}