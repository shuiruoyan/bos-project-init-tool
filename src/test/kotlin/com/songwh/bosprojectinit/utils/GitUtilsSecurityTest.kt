package com.songwh.bosprojectinit.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.util.concurrent.atomic.AtomicBoolean

class GitUtilsSecurityTest {

    private val gitUtils = GitUtils(AtomicBoolean(false))

    @Test
    fun `test extractRepoName with normal URL`() {
        val normalUrl = "https://github.com/user/repo.git"
        val repoName = gitUtils.extractRepoName(normalUrl)
        assertEquals("repo", repoName)
    }

    @Test
    fun `test extractRepoName with path traversal attack`() {
        val maliciousUrl1 = "https://github.com/user/../../../etc/passwd/repo.git"
        assertFailsWith<IllegalArgumentException> {
            gitUtils.extractRepoName(maliciousUrl1)
        }

        val maliciousUrl2 = "https://github.com/user/repo..git"
        assertFailsWith<IllegalArgumentException> {
            gitUtils.extractRepoName(maliciousUrl2)
        }

        val maliciousUrl3 = "https://github.com/user/repo../subdir.git"
        assertFailsWith<IllegalArgumentException> {
            gitUtils.extractRepoName(maliciousUrl3)
        }
    }

    @Test
    fun `test extractRepoName with special characters`() {
        val maliciousUrl = "https://github.com/user/repo;rm -rf .git"
        assertFailsWith<IllegalArgumentException> {
            gitUtils.extractRepoName(maliciousUrl)
        }

        val maliciousUrl2 = "https://github.com/user/repo&&echo test.git"
        assertFailsWith<IllegalArgumentException> {
            gitUtils.extractRepoName(maliciousUrl2)
        }
    }

    @Test
    fun `test extractRepoName with valid special names`() {
        val validUrl1 = "https://github.com/user/my-repo.git"
        assertEquals("my-repo", gitUtils.extractRepoName(validUrl1))

        val validUrl2 = "https://github.com/user/my_repo.git"
        assertEquals("my_repo", gitUtils.extractRepoName(validUrl2))

        val validUrl3 = "https://github.com/user/my.repo.git"
        assertEquals("my.repo", gitUtils.extractRepoName(validUrl3)) // Fixed: now it should return "my.repo"
    }
}