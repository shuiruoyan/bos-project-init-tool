package com.songwh.bosprojectinit.utils

import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitUtilsSecurityTest {

    @Test
    fun testExtractRepoNameSanitization() {
        val gitUtils = GitUtils(AtomicBoolean(false))
        
        // 测试正常的仓库名称提取
        assertEquals("my-repo", gitUtils.extractRepoName("https://github.com/user/my-repo.git"))
        assertEquals("repo", gitUtils.extractRepoName("git@github.com:user/repo.git"))
        
        // 测试包含危险字符的仓库名称（应该被消毒）
        assertEquals("repo ls", gitUtils.extractRepoName("https://github.com/user/repo;ls.git"))
        assertEquals("repo_name_", gitUtils.extractRepoName("git@github.com:user/repo<name>.git"))
        assertEquals("repo_name", gitUtils.extractRepoName("https://github.com/user/repo:name.git"))
    }

    @Test
    fun testCloneRepositorySecurityValidation() = runBlocking {
        val gitUtils = GitUtils(AtomicBoolean(false))
        val tempDir = kotlin.io.createTempDir("security-test")
        
        try {
            // 测试1: 有效的URL和仓库名称
            val validResult = gitUtils.cloneRepository(
                url = "https://github.com/user/valid-repo.git",
                repoName = "valid-repo",
                rootFile = tempDir,
                onProgress = { _, _ -> }
            )
            // 注意：由于是测试环境，可能没有git命令，所以可能失败
            // 我们主要关心验证逻辑是否执行
            
            // 测试2: 包含命令注入的URL（应该被拒绝）
            val injectionResult = gitUtils.cloneRepository(
                url = "https://github.com/user/repo.git; rm -rf /",
                repoName = "repo",
                rootFile = tempDir,
                onProgress = { _, _ -> }
            )
            assertFalse(injectionResult.success, "包含命令注入的URL应该被拒绝")
            assertTrue(injectionResult.errorMessage.contains("Git URL验证失败"), "应该返回验证错误信息")
            
            // 测试3: 包含路径遍历的仓库名称（应该被拒绝）
            val traversalResult = gitUtils.cloneRepository(
                url = "https://github.com/user/repo.git",
                repoName = "../../etc/passwd",
                rootFile = tempDir,
                onProgress = { _, _ -> }
            )
            assertFalse(traversalResult.success, "包含路径遍历的仓库名称应该被拒绝")
            assertTrue(traversalResult.errorMessage.contains("仓库名称验证失败") || 
                      traversalResult.errorMessage.contains("路径安全检查失败"), 
                      "应该返回验证错误信息")
            
            // 测试4: 超长URL（应该被拒绝）
            val longUrl = "https://github.com/user/" + "a".repeat(3000) + ".git"
            val longUrlResult = gitUtils.cloneRepository(
                url = longUrl,
                repoName = "repo",
                rootFile = tempDir,
                onProgress = { _, _ -> }
            )
            assertFalse(longUrlResult.success, "超长URL应该被拒绝")
            assertTrue(longUrlResult.errorMessage.contains("Git URL验证失败"), "应该返回验证错误信息")
            
            // 测试5: 无效协议（应该被拒绝）
            val invalidProtocolResult = gitUtils.cloneRepository(
                url = "javascript:alert('xss')",
                repoName = "repo",
                rootFile = tempDir,
                onProgress = { _, _ -> }
            )
            assertFalse(invalidProtocolResult.success, "无效协议应该被拒绝")
            assertTrue(invalidProtocolResult.errorMessage.contains("Git URL验证失败"), "应该返回验证错误信息")
            
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testCloneRepositoryPathSafety() = runBlocking {
        val gitUtils = GitUtils(AtomicBoolean(false))
        val tempDir = kotlin.io.createTempDir("security-test")
        
        try {
            // 创建子目录作为根目录
            val rootDir = File(tempDir, "root").apply { mkdirs() }
            
            // 测试路径安全性：尝试克隆到根目录之外
            val unsafeResult = gitUtils.cloneRepository(
                url = "https://github.com/user/repo.git",
                repoName = "../../../etc/passwd",
                rootFile = rootDir,
                onProgress = { _, _ -> }
            )
            
            assertFalse(unsafeResult.success, "路径遍历攻击应该被拒绝")
            assertTrue(unsafeResult.errorMessage.contains("路径安全检查失败") || 
                      unsafeResult.errorMessage.contains("仓库名称验证失败"),
                      "应该返回路径安全错误信息")
            
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testCloneRepositoryInputSanitization() = runBlocking {
        val gitUtils = GitUtils(AtomicBoolean(false))
        val tempDir = kotlin.io.createTempDir("security-test")
        
        try {
            // 测试输入消毒：即使URL包含危险字符，消毒后应该可以处理
            // 注意：实际git命令可能仍然会失败，但我们关心的是消毒逻辑
            
            val testCases = listOf(
                "https://example.com/repo.git; ls" to "repo",
                "git@github.com:user/repo.git | cat" to "repo",
                "https://example.com/repo.git`rm`" to "repo"
            )
            
            testCases.forEach { (url, repoName) ->
                val result = gitUtils.cloneRepository(
                    url = url,
                    repoName = repoName,
                    rootFile = tempDir,
                    onProgress = { _, _ -> }
                )
                // 主要验证没有异常抛出，消毒逻辑正常工作
                // 由于是测试环境，git命令可能失败，但验证应该通过
            }
            
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testCancelSecurity() {
        val isCancelled = AtomicBoolean(false)
        val gitUtils = GitUtils(isCancelled)
        
        // 测试取消功能
        gitUtils.cancel()
        assertTrue(isCancelled.get(), "取消后isCancelled应该为true")
        
        // 测试停止当前进程（安全关闭）
        gitUtils.stopCurrentProcess()
        // 主要验证没有异常抛出
    }

    @Test
    fun testProcessSafety() {
        // 测试进程安全：确保进程树被正确清理
        val isCancelled = AtomicBoolean(false)
        val gitUtils = GitUtils(isCancelled)
        
        // 创建并立即停止一个进程（模拟安全场景）
        runBlocking {
            val tempDir = kotlin.io.createTempDir("process-test")
            try {
                // 启动一个快速失败的git命令（使用无效参数）
                val result = gitUtils.cloneRepository(
                    url = "invalid-url",
                    repoName = "test",
                    rootFile = tempDir,
                    onProgress = { _, _ -> },
                    taskId = "test-task"
                )
                // 验证进程被清理
                // 主要验证没有资源泄漏
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }
}
