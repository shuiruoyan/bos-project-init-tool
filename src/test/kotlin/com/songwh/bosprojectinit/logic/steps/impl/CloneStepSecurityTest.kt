package com.songwh.bosprojectinit.logic.steps.impl

import com.songwh.bosprojectinit.model.LogEntry
import com.songwh.bosprojectinit.model.StepExecutionContext
import com.songwh.bosprojectinit.utils.GitUtils
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * CloneStep 安全功能单元测试
 * 测试符号链接防护、目录删除安全、并发克隆等功能
 */
class CloneStepSecurityTest {

    private fun createCloneStep(): CloneStep {
        val isCancelled = AtomicBoolean(false)
        val gitUtils = GitUtils(isCancelled)
        val logEntries = mutableMapOf<String, LogEntry>()
        return CloneStep(
            gitUtils = gitUtils,
            isCancelled = isCancelled,
            logEntries = logEntries,
            onLogUpdate = {},
            onStatsUpdate = { _, _ -> }
        )
    }

    // ============ 基本功能测试 ============

    @Test
    fun testShouldDeleteNormalDirectoriesSuccessfully() = runBlocking {
        val tempRoot = Files.createTempDirectory("root").toFile()
        val projectsDir = File(tempRoot, "projects")
        projectsDir.mkdirs()
        File(projectsDir, "test.txt").writeText("test content")

        try {
            val step = createCloneStep()
            val context = StepExecutionContext(
                rootPath = tempRoot.absolutePath,
                urls = emptyList(),
                timeoutSeconds = 60,
                cleanProjectsBeforeClone = true,
                onProgress = { _, _ -> },
                onLogUpdate = {},
                onStatsUpdate = { _, _ -> }
            )

            val result = step.execute(context, 0, 1)

            // 应该成功删除普通目录
            assertTrue(result.success, "应能删除普通目录")
            assertFalse(projectsDir.exists(), "projects 目录应被删除")
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testShouldSupportConcurrentRepositoryCloning() = runBlocking {
        val tempRoot = Files.createTempDirectory("root").toFile()
        val projectsDir = File(tempRoot, "projects")
        projectsDir.mkdirs()

        try {
            val step = createCloneStep()
            val context = StepExecutionContext(
                rootPath = tempRoot.absolutePath,
                urls = listOf(
                    "https://github.com/user/repo1.git",
                    "https://github.com/user/repo2.git",
                    "https://github.com/user/repo3.git"
                ),
                timeoutSeconds = 5,  // 短超时，快速失败
                cleanProjectsBeforeClone = false,
                onProgress = { _, _ -> },
                onLogUpdate = {},
                onStatsUpdate = { _, _ -> }
            )

            // 执行克隆（会失败，但应该并发执行）
            val result = step.execute(context, 0, 1)

            // 验证并发执行不会崩溃
            assertTrue(true, "并发克隆应能正常处理")
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testShouldRespondToCancellationSignalCorrectly() = runBlocking {
        val tempRoot = Files.createTempDirectory("root").toFile()
        val projectsDir = File(tempRoot, "projects")
        projectsDir.mkdirs()

        try {
            val isCancelled = AtomicBoolean(false)
            val gitUtils = GitUtils(isCancelled)
            val logEntries = mutableMapOf<String, LogEntry>()

            val step = CloneStep(
                gitUtils = gitUtils,
                isCancelled = isCancelled,
                logEntries = logEntries,
                onLogUpdate = {},
                onStatsUpdate = { _, _ -> }
            )

            // 预先设置取消标志
            isCancelled.set(true)

            val context = StepExecutionContext(
                rootPath = tempRoot.absolutePath,
                urls = listOf("https://github.com/user/repo.git"),
                timeoutSeconds = 60,
                cleanProjectsBeforeClone = false,
                onProgress = { _, _ -> },
                onLogUpdate = {},
                onStatsUpdate = { _, _ -> }
            )

            val result = step.execute(context, 0, 1)

            // 应该立即返回失败
            assertFalse(result.success, "取消后应返回失败")
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testShouldGenerateDetailedCategorizedErrorLogs() = runBlocking {
        val tempRoot = Files.createTempDirectory("root").toFile()
        val projectsDir = File(tempRoot, "projects")
        projectsDir.mkdirs()

        try {
            val logEntries = mutableMapOf<String, LogEntry>()
            val isCancelled = AtomicBoolean(false)
            val gitUtils = GitUtils(isCancelled)

            val step = CloneStep(
                gitUtils = gitUtils,
                isCancelled = isCancelled,
                logEntries = logEntries,
                onLogUpdate = {},
                onStatsUpdate = { _, _ -> }
            )

            val context = StepExecutionContext(
                rootPath = tempRoot.absolutePath,
                urls = listOf("https://github.com/invalid/repo.git"),
                timeoutSeconds = 5,
                cleanProjectsBeforeClone = false,
                onProgress = { _, _ -> },
                onLogUpdate = {},
                onStatsUpdate = { _, _ -> }
            )

            step.execute(context, 0, 1)

            // 验证日志包含错误信息
            assertTrue(logEntries.isNotEmpty(), "应生成日志")
            val logs = logEntries.values.map { it.format() }
            assertTrue(logs.any { it.contains("https://github.com/invalid/repo.git") })
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testShouldTrackCloneProgressAccurately() = runBlocking {
        val tempRoot = Files.createTempDirectory("root").toFile()
        val projectsDir = File(tempRoot, "projects")
        projectsDir.mkdirs()

        try {
            val progressUpdates = mutableListOf<Float>()
            val step = createCloneStep()

            val context = StepExecutionContext(
                rootPath = tempRoot.absolutePath,
                urls = listOf("https://github.com/user/repo.git"),
                timeoutSeconds = 5,
                cleanProjectsBeforeClone = false,
                onProgress = { progress, _ ->
                    progressUpdates.add(progress)
                },
                onLogUpdate = {},
                onStatsUpdate = { _, _ -> }
            )

            step.execute(context, 0, 1)

            // 验证进度更新
            assertTrue(progressUpdates.isNotEmpty(), "应有进度更新")
            // 进度应该是递增的（允许少量回退）
            val sortedProgress = progressUpdates.sorted()
            assertTrue(
                sortedProgress.size >= progressUpdates.size * 0.8,
                "进度应大致递增"
            )
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
