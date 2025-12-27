package com.songwh.bosprojectinit.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecurityUtilsTest {

    @Test
    fun testValidateGitUrlValidUrls() {
        // 有效的Git URL
        val validUrls = listOf(
            "https://github.com/user/repo.git",
            "http://github.com/user/repo.git",
            "git://github.com/user/repo.git",
            "ssh://git@github.com/user/repo.git",
            "git@github.com:user/repo.git",
            "https://gitlab.com/group/subgroup/repo.git",
            "http://example.com:8080/user/repo.git",
            "file:///home/user/repo.git"
        )
        
        validUrls.forEach { url ->
            val result = SecurityUtils.validateGitUrl(url)
            assertTrue(result.isValid, "URL '$url' 应该有效: ${result.message}")
        }
    }

    @Test
    fun testValidateGitUrlInvalidUrls() {
        // 无效的Git URL
        val invalidUrls = listOf(
            "", // 空字符串
            "   ", // 空白字符
            "javascript:alert('xss')", // JavaScript协议
            "ftp://example.com/repo.git", // 不允许的协议
            "https://example.com/repo.git; rm -rf /", // 命令注入
            "git@github.com:user/repo.git && ls", // 命令注入
            "../etc/passwd", // 路径遍历
            "https://" + "a".repeat(3000) + ".com/repo.git" // 超长URL
        )
        
        invalidUrls.forEach { url ->
            val result = SecurityUtils.validateGitUrl(url)
            assertFalse(result.isValid, "URL '$url' 应该无效: ${result.message}")
        }
    }

    @Test
    fun testValidateGitUrlDangerousCharacters() {
        // 包含危险字符的URL
        val dangerousUrls = listOf(
            "https://example.com/repo.git; ls",
            "git@github.com:user/repo.git | cat /etc/passwd",
            "https://example.com/repo.git`rm -rf /`",
            "https://example.com/repo.git\$ (ls)",
            "https://example.com/repo.git\nrm -rf /",
            "https://example.com/repo.git\trm -rf /",
            "https://example.com/repo.git\rrm -rf /"
        )
        
        dangerousUrls.forEach { url ->
            val result = SecurityUtils.validateGitUrl(url)
            assertFalse(result.isValid, "URL '$url' 应该被拒绝（危险字符）: ${result.message}")
        }
    }

    @Test
    fun testValidateRepoNameValidNames() {
        // 有效的仓库名称
        val validNames = listOf(
            "my-repo",
            "repo123",
            "test_repo",
            "my.repo",
            "a".repeat(100) // 边界长度
        )
        
        validNames.forEach { name ->
            val result = SecurityUtils.validateRepoName(name)
            assertTrue(result.isValid, "仓库名称 '$name' 应该有效: ${result.message}")
        }
    }

    @Test
    fun testValidateRepoNameInvalidNames() {
        // 无效的仓库名称
        val invalidNames = listOf(
            "", // 空字符串
            "   ", // 空白字符
            ".", // 当前目录
            "..", // 上级目录
            "repo; rm -rf /", // 命令注入
            "repo<name>", // 无效字符
            "repo:name", // 无效字符
            "repo\"name", // 无效字符
            "repo|name", // 无效字符
            "repo?name", // 无效字符
            "repo*name", // 无效字符
            "../etc/passwd", // 路径遍历
            "a".repeat(300) // 超长名称
        )
        
        invalidNames.forEach { name ->
            val result = SecurityUtils.validateRepoName(name)
            assertFalse(result.isValid, "仓库名称 '$name' 应该无效: ${result.message}")
        }
    }

    @Test
    fun testSanitizeGitUrl() {
        val testCases = mapOf(
            "https://example.com/repo.git; ls" to "https://example.com/repo.git ls",
            "git@github.com:user/repo.git | cat" to "git@github.com:user/repo.git  cat",
            "https://example.com/repo.git`rm`" to "https://example.com/repo.gitrm",
            "https://example.com/repo.git\$\$" to "https://example.com/repo.git",
            "https://example.com/repo.git\n" to "https://example.com/repo.git",
            "https://example.com/repo.git\r" to "https://example.com/repo.git",
            "https://example.com/repo.git\t" to "https://example.com/repo.git"
        )
        
        testCases.forEach { (input, expected) ->
            val sanitized = SecurityUtils.sanitizeGitUrl(input)
            assertEquals(expected, sanitized, "消毒后的URL不匹配: '$input' -> '$sanitized'")
        }
    }

    @Test
    fun testSanitizeRepoName() {
        val testCases = mapOf(
            "repo; ls" to "repo ls",
            "repo<name>" to "repo_name_",
            "repo:name" to "repo_name",
            "repo\"name" to "repo_name",
            "repo|name" to "repo_name",
            "repo?name" to "repo_name",
            "repo*name" to "repo_name",
            "../etc/passwd" to "etc/passwd",
            "  repo  " to "repo"
        )
        
        testCases.forEach { (input, expected) ->
            val sanitized = SecurityUtils.sanitizeRepoName(input)
            assertEquals(expected, sanitized, "消毒后的仓库名称不匹配: '$input' -> '$sanitized'")
        }
    }

    @Test
    fun testValidatePathSafety() {
        // 创建临时目录进行测试
        val tempDir = kotlin.io.createTempDir("security-test")
        try {
            val basePath = tempDir.canonicalPath
            
            // 安全路径
            val safeResult = SecurityUtils.validatePathSafety(basePath, "subdir/repo")
            assertTrue(safeResult.isValid, "安全路径应该有效: ${safeResult.message}")
            
            // 不安全路径（路径遍历）
            val unsafeResult = SecurityUtils.validatePathSafety(basePath, "../../etc/passwd")
            assertFalse(unsafeResult.isValid, "路径遍历应该被拒绝: ${unsafeResult.message}")
            
            // 测试绝对路径（应该失败）
            // 创建一个确定在basePath之外的绝对路径
            val outsideDir = kotlin.io.createTempDir("security-test-outside")
            try {
                val absolutePath = outsideDir.canonicalPath
                val absoluteResult = SecurityUtils.validatePathSafety(basePath, absolutePath)
                assertFalse(absoluteResult.isValid, "basePath外部的绝对路径应该被拒绝: ${absoluteResult.message}")
            } finally {
                outsideDir.deleteRecursively()
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testValidateTimeout() {
        // 有效超时时间
        val validTimeouts = listOf(1L, 60L, 300L, 3600L)
        validTimeouts.forEach { timeout ->
            val result = SecurityUtils.validateTimeout(timeout)
            assertTrue(result.isValid, "超时时间 $timeout 应该有效: ${result.message}")
        }
        
        // 无效超时时间
        val invalidTimeouts = listOf(0L, -1L, 3601L, 10000L)
        invalidTimeouts.forEach { timeout ->
            val result = SecurityUtils.validateTimeout(timeout)
            assertFalse(result.isValid, "超时时间 $timeout 应该无效: ${result.message}")
        }
    }

    @Test
    fun testContainsDangerousCharacters() {
        // 反射访问私有方法
        val method = SecurityUtils::class.java.getDeclaredMethod("containsDangerousCharacters", String::class.java)
        method.isAccessible = true
        
        val dangerousStrings = listOf(
            "test; ls",
            "test | cat",
            "test `rm`",
            "test \$\$",
            "test\n",
            "test\r",
            "test\t"
        )
        
        dangerousStrings.forEach { str ->
            val result = method.invoke(SecurityUtils, str) as Boolean
            assertTrue(result, "字符串 '$str' 应该包含危险字符")
        }
        
        val safeStrings = listOf(
            "test",
            "test-repo",
            "test.repo",
            "test_repo",
            "test repo"
        )
        
        safeStrings.forEach { str ->
            val result = method.invoke(SecurityUtils, str) as Boolean
            assertFalse(result, "字符串 '$str' 不应该包含危险字符")
        }
    }

    @Test
    fun testContainsPathTraversal() {
        // 反射访问私有方法
        val method = SecurityUtils::class.java.getDeclaredMethod("containsPathTraversal", String::class.java)
        method.isAccessible = true
        
        val traversalStrings = listOf(
            "../etc/passwd",
            "..\\windows\\system32",
            "test/../../etc",
            "test\\..\\..\\windows"
        )
        
        traversalStrings.forEach { str ->
            val result = method.invoke(SecurityUtils, str) as Boolean
            assertTrue(result, "字符串 '$str' 应该包含路径遍历序列")
        }
        
        val safeStrings = listOf(
            "test",
            "test/repo",
            "test\\repo",
            "test..repo",
            "..test"
        )
        
        safeStrings.forEach { str ->
            val result = method.invoke(SecurityUtils, str) as Boolean
            assertFalse(result, "字符串 '$str' 不应该包含路径遍历序列")
        }
    }
}
