package com.songwh.bosprojectinit.utils

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitUtilsSecurityTest {

    @Test
    fun testExtractRepoNameFromHttpsUrl() {
        assertEquals("my-repo", GitUtils.extractRepoNameFromUrl("https://github.com/user/my-repo.git"))
    }

    @Test
    fun testExtractRepoNameFromSshScpLikeUrl() {
        assertEquals("repo2", GitUtils.extractRepoNameFromUrl("git@github.com:user/repo2.git"))
    }

    @Test
    fun testExtractRepoNameFromUrlSanitizesUnsafeCharacters() {
        assertEquals("repo_name", GitUtils.extractRepoNameFromUrl("https://example.com/user/repo name.git"))
    }

    @Test
    fun testIsSafeRepoNameRejectsPathTraversal() {
        assertFalse(GitUtils.isSafeRepoName(".."))
        assertFalse(GitUtils.isSafeRepoName("../a"))
        assertFalse(GitUtils.isSafeRepoName("a/.."))
        assertFalse(GitUtils.isSafeRepoName("a\\b"))
        assertTrue(GitUtils.isSafeRepoName("a-b_c.1"))
    }

    @Test
    fun testSanitizeGitUrlForDisplayMasksQuerySecrets() {
        val gitUtils = GitUtils(AtomicBoolean(false))
        val display = gitUtils.sanitizeGitUrlForDisplay("https://example.com/repo.git?token=abcd&x=1")
        assertTrue(display.contains("token=***"))
        assertFalse(display.contains("token=abcd"))
    }

    @Test
    fun testBuildCloneCommandContainsDashDashSeparator() {
        val gitUtils = GitUtils(AtomicBoolean(false))
        val cmd = gitUtils.buildCloneCommand("https://example.com/repo.git", "repo")
        assertTrue(cmd.contains("--"), "clone command should include '--' to prevent option injection")
        val dashDashIndex = cmd.indexOf("--")
        assertTrue(dashDashIndex >= 0)
        assertEquals("https://example.com/repo.git", cmd[dashDashIndex + 1])
        assertEquals("repo", cmd[dashDashIndex + 2])
    }

    @Test
    fun testIsSupportedGitRemoteRejectsFileSchemeAndNewlines() {
        val gitUtils = GitUtils(AtomicBoolean(false))
        assertFalse(gitUtils.isSupportedGitRemote("file:///c:/windows"))
        assertFalse(gitUtils.isSupportedGitRemote("https://example.com/repo.git\n--upload-pack=calc"))
        assertFalse(gitUtils.isSupportedGitRemote("-u https://example.com/repo.git"))
        assertTrue(gitUtils.isSupportedGitRemote("https://example.com/repo.git"))
    }

    @Test
    fun testValidateGitRemoteUrlReturnsReason() {
        assertEquals("file scheme is not allowed", GitUtils.validateGitRemoteUrl("file:///c:/windows"))
        assertEquals("starts with '-'", GitUtils.validateGitRemoteUrl("-u https://example.com/repo.git"))
        assertEquals("contains control characters", GitUtils.validateGitRemoteUrl("https://example.com/repo.git\n--x"))
        assertEquals(null, GitUtils.validateGitRemoteUrl("https://example.com/repo.git"))
    }

    @Test
    fun testExtractGitErrorLine() {
        assertEquals("fatal: repository 'x' not found", GitUtils.extractGitErrorLine("fatal: repository 'x' not found"))
        assertEquals("error: Permission denied", GitUtils.extractGitErrorLine("error: Permission denied"))
        assertEquals(null, GitUtils.extractGitErrorLine("Receiving objects:  45% (20/44)"))
    }
}
