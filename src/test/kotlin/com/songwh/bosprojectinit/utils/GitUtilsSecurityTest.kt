package com.songwh.bosprojectinit.utils

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitUtilsSecurityTest {

    private val gitUtils = GitUtils(AtomicBoolean(false))

    @Test
    fun testExtractRepoNameSecurity() {
        // 正常情况
        assertEquals("my-repo", gitUtils.extractRepoName("https://github.com/user/my-repo.git"))
        assertEquals("my-repo", gitUtils.extractRepoName("git@github.com:user/my-repo.git"))

        // 路径遍历尝试
        assertEquals("repo", gitUtils.extractRepoName("https://github.com/user/../../repo.git"))
        assertEquals("repo", gitUtils.extractRepoName("/etc/passwd/repo.git"))
        
        // 特殊字符过滤
        assertEquals("repo-12_3", gitUtils.extractRepoName("https://github.com/user/repo-1.2_3!@#$%^&*().git"))
    }

    @Test
    fun testCloneRepositoryCommandInjection() = runBlocking {
        val rootFile = File("temp").apply { mkdirs() }
        try {
            // 尝试以减号开头的参数注入
            val result = gitUtils.cloneRepository(
                url = "--upload-pack=touch /tmp/pwned",
                repoName = "repo",
                rootFile = rootFile,
                onProgress = { _, _ -> }
            )
            assertFalse(result.success, "Should fail due to security check")
            assertEquals("Invalid parameters", result.errorMessage)

            val result2 = gitUtils.cloneRepository(
                url = "https://github.com/user/repo.git",
                repoName = "--anything",
                rootFile = rootFile,
                onProgress = { _, _ -> }
            )
            assertFalse(result2.success, "Should fail due to security check")
            assertEquals("Invalid parameters", result2.errorMessage)
        } finally {
            rootFile.deleteRecursively()
        }
    }
}
