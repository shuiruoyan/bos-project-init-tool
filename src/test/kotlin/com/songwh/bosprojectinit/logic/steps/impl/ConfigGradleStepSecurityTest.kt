package com.songwh.bosprojectinit.logic.steps.impl

import com.songwh.bosprojectinit.model.LogEntry
import com.songwh.bosprojectinit.model.StepExecutionContext
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ConfigGradleStep 和 BuildLocalStep 安全功能单元测试
 * 测试文件大小限制、路径验证等功能
 */
class ConfigGradleStepSecurityTest {

    private fun createConfigGradleStep(): ConfigGradleStep {
        return ConfigGradleStep(
            isCancelled = AtomicBoolean(false),
            logEntries = mutableMapOf(),
            onLogUpdate = {}
        )
    }

    private fun createBuildLocalStep(): BuildLocalStep {
        return BuildLocalStep(
            isCancelled = AtomicBoolean(false),
            logEntries = mutableMapOf(),
            onLogUpdate = {}
        )
    }

    // ============ 文件大小限制测试 ============

    @Test
    fun testShouldRejectOversizedConfigGradleFiles() = runBlocking {
        val tempRoot = Files.createTempDirectory("root").toFile()
        val parentDir = tempRoot.parentFile

        try {
            // 创建一个 11MB 的文件（超过 10MB 限制）
            val largeConfigFile = File(parentDir, "config.gradle")
            largeConfigFile.writeText("a".repeat(11 * 1024 * 1024))

            val step = createConfigGradleStep()
            val context = StepExecutionContext(
                rootPath = tempRoot.absolutePath,
                urls = emptyList(),
                timeoutSeconds = 60,
                cleanProjectsBeforeClone = false,
                onProgress = { _, _ -> },
                onLogUpdate = {},
                onStatsUpdate = { _, _ -> }
            )

            val result = step.execute(context, 0, 1)

            // 应该失败
            assertFalse(result.success, "应拒绝超大文件")
        } finally {
            File(parentDir, "config.gradle").delete()
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testShouldAcceptNormalSizedConfigGradleFiles() = runBlocking {
        val tempRoot = Files.createTempDirectory("root").toFile()
        val parentDir = tempRoot.parentFile
        val projectsDir = File(tempRoot, "projects")
        projectsDir.mkdirs()

        try {
            // 创建正常大小的配置文件
            val configFile = File(parentDir, "config.gradle")
            configFile.writeText("version = '1.0'")

            // 创建目标文件
            val targetDir = File(projectsDir, "repo1")
            targetDir.mkdirs()
            val targetConfig = File(targetDir, "config.gradle")
            targetConfig.writeText("old content")

            val step = createConfigGradleStep()
            val context = StepExecutionContext(
                rootPath = tempRoot.absolutePath,
                urls = emptyList(),
                timeoutSeconds = 60,
                cleanProjectsBeforeClone = false,
                onProgress = { _, _ -> },
                onLogUpdate = {},
                onStatsUpdate = { _, _ -> }
            )

            val result = step.execute(context, 0, 1)

            // 应该成功
            assertTrue(result.success, "应接受正常大小文件")
            // 验证内容被替换
            assertEquals("version = '1.0'", targetConfig.readText())
        } finally {
            File(parentDir, "config.gradle").delete()
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testBuildLocalStepShouldRejectOversizedBuildGradleFiles() = runBlocking {
        val tempRoot = Files.createTempDirectory("root").toFile()

        try {
            // 创建超大的 build.gradle
            val largeBuildFile = File(tempRoot, "build.gradle")
            largeBuildFile.writeText("a".repeat(11 * 1024 * 1024))

            val step = createBuildLocalStep()
            val context = StepExecutionContext(
                rootPath = tempRoot.absolutePath,
                urls = emptyList(),
                timeoutSeconds = 60,
                cleanProjectsBeforeClone = false,
                onProgress = { _, _ -> },
                onLogUpdate = {},
                onStatsUpdate = { _, _ -> },
                moduleInfos = emptyList()
            )

            val result = step.execute(context, 0, 1)

            // 应该失败
            assertFalse(result.success, "应拒绝超大 build.gradle")
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    // ============ 错误处理测试 ============

    @Test
    fun testShouldHandleFileReadFailuresGracefully() = runBlocking {
        val tempRoot = Files.createTempDirectory("root").toFile()

        try {
            // 不创建 config.gradle 文件
            val step = createConfigGradleStep()
            val context = StepExecutionContext(
                rootPath = tempRoot.absolutePath,
                urls = emptyList(),
                timeoutSeconds = 60,
                cleanProjectsBeforeClone = false,
                onProgress = { _, _ -> },
                onLogUpdate = {},
                onStatsUpdate = { _, _ -> }
            )

            val result = step.execute(context, 0, 1)

            // 缺少源文件应返回成功（跳过同步）
            assertTrue(result.success, "缺少源文件应优雅处理")
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    // ============ 边界情况测试 ============

    @Test
    fun testShouldHandleEmptyProjectsDirectory() = runBlocking {
        val tempRoot = Files.createTempDirectory("root").toFile()
        val parentDir = tempRoot.parentFile
        val projectsDir = File(tempRoot, "projects")
        projectsDir.mkdirs()

        try {
            val configFile = File(parentDir, "config.gradle")
            configFile.writeText("version = '1.0'")

            val step = createConfigGradleStep()
            val context = StepExecutionContext(
                rootPath = tempRoot.absolutePath,
                urls = emptyList(),
                timeoutSeconds = 60,
                cleanProjectsBeforeClone = false,
                onProgress = { _, _ -> },
                onLogUpdate = {},
                onStatsUpdate = { _, _ -> }
            )

            val result = step.execute(context, 0, 1)

            // 空目录应成功处理
            assertTrue(result.success, "空目录应能正常处理")
        } finally {
            File(parentDir, "config.gradle").delete()
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testShouldHandleNonExistentProjectsDirectory() = runBlocking {
        val tempRoot = Files.createTempDirectory("root").toFile()
        val parentDir = tempRoot.parentFile

        try {
            val configFile = File(parentDir, "config.gradle")
            configFile.writeText("version = '1.0'")

            val step = createConfigGradleStep()
            val context = StepExecutionContext(
                rootPath = tempRoot.absolutePath,
                urls = emptyList(),
                timeoutSeconds = 60,
                cleanProjectsBeforeClone = false,
                onProgress = { _, _ -> },
                onLogUpdate = {},
                onStatsUpdate = { _, _ -> }
            )

            val result = step.execute(context, 0, 1)

            // 不存在的目录应成功处理
            assertTrue(result.success, "不存在的目录应能正常处理")
        } finally {
            File(parentDir, "config.gradle").delete()
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testBuildLocalStepShouldHandleOversizedSuffixTemplateFiles() = runBlocking {
        val tempRoot = Files.createTempDirectory("root").toFile()

        try {
            // 创建正常的 build.gradle
            val buildFile = File(tempRoot, "build.gradle")
            buildFile.writeText("plugins { }")

            // 创建超大的 suffix 模板
            val suffixFile = File(tempRoot, "build-suffix.gradle.template")
            suffixFile.writeText("a".repeat(11 * 1024 * 1024))

            val step = createBuildLocalStep()
            val context = StepExecutionContext(
                rootPath = tempRoot.absolutePath,
                urls = emptyList(),
                timeoutSeconds = 60,
                cleanProjectsBeforeClone = false,
                onProgress = { _, _ -> },
                onLogUpdate = {},
                onStatsUpdate = { _, _ -> },
                moduleInfos = emptyList()
            )

            val result = step.execute(context, 0, 1)

            // 应该失败
            assertFalse(result.success, "应拒绝超大 suffix 模板")
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
