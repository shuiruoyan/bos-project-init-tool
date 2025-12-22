package com.songwh.bosprojectinit.logic.steps.impl

import com.songwh.bosprojectinit.MessageBundle
import com.songwh.bosprojectinit.logic.steps.IProjectInitStep
import com.songwh.bosprojectinit.model.LogEntry
import com.songwh.bosprojectinit.model.StepExecutionContext
import com.songwh.bosprojectinit.model.StepResult
import com.songwh.bosprojectinit.utils.GitUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 第一步：代码拉取步骤 (CloneStep)
 * 负责清空旧目录（可选）以及从远程拉取指定的 Git 仓库。
 */
class CloneStep(
    private val gitUtils: GitUtils,
    private val isCancelled: AtomicBoolean,
    private val logEntries: MutableMap<String, LogEntry>,
    private val onLogUpdate: suspend (List<String>) -> Unit,
    private val onStatsUpdate: suspend (Int, Int) -> Unit,
) : IProjectInitStep {
    override val nameKey: String = "log.step.clone"

    override suspend fun execute(context: StepExecutionContext, stepIndex: Int, totalSteps: Int): StepResult {
        // 检查是否已取消
        if (isCancelled.get()) {
            return StepResult(false)
        }

        val projectsDir = File(context.rootPath, "projects")

        // 清理项目目录（如果需要）
        if (!cleanProjectsDirectory(context, projectsDir, stepIndex, totalSteps)) {
            return StepResult(false)
        }

        // 确保 projects 目录存在
        ensureProjectsDirectoryExists(projectsDir)

        // 处理所有仓库克隆
        return processRepositories(context, projectsDir, stepIndex, totalSteps)
    }

    /**
     * 清理项目目录
     */
    private suspend fun cleanProjectsDirectory(
        context: StepExecutionContext,
        projectsDir: File,
        stepIndex: Int,
        totalSteps: Int,
    ): Boolean {
        if (!context.cleanProjectsBeforeClone) {
            return true
        }

        context.onProgress(
            context.calculateTotalProgress(stepIndex, totalSteps, 0f),
            MessageBundle.message("status.cleaning")
        )
        logEntries["__clean__"] =
            LogEntry(MessageBundle.message("log.system"), MessageBundle.message("log.cleaning"), 0)
        emitLogs()

        return runCatching {
            if (projectsDir.exists()) {
                val deleted = projectsDir.deleteRecursively()
                if (!deleted) {
                    throw Exception("Failed to delete directory: ${projectsDir.absolutePath}. It might be used by another process.")
                }
            }
            logEntries["__clean__"] =
                LogEntry(MessageBundle.message("log.system"), MessageBundle.message("log.clean.success"), 100)
            emitLogs()
            true
        }.getOrElse { e ->
            logEntries["__clean__"] = LogEntry(
                MessageBundle.message("log.system"),
                MessageBundle.message("log.clean.failed", e.message ?: ""),
                0
            )
            emitLogs()
            false
        }
    }

    /**
     * 确保项目目录存在
     */
    private fun ensureProjectsDirectoryExists(projectsDir: File) {
        if (!projectsDir.exists()) {
            projectsDir.mkdirs()
        }
    }

    /**
     * 处理所有仓库克隆
     */
    private suspend fun processRepositories(
        context: StepExecutionContext,
        projectsDir: File,
        stepIndex: Int,
        totalSteps: Int,
    ): StepResult {
        val totalRepos = context.urls.size
        var currentSuccess = 0
        var currentFailure = 0

        context.urls.forEachIndexed { index, url ->
            // 检查是否已取消
            if (isCancelled.get()) {
                updateLog(url, MessageBundle.message("log.cancelled"), 0)
                return StepResult(false)
            }

            val result = processSingleRepository(
                context = context,
                url = url,
                projectsDir = projectsDir,
                index = index,
                totalRepos = totalRepos,
                stepIndex = stepIndex,
                totalSteps = totalSteps
            )

            when (result) {
                RepositoryResult.SUCCESS -> currentSuccess++
                RepositoryResult.FAILURE -> currentFailure++
                RepositoryResult.CANCELLED -> return StepResult(false)
            }
        }

        return StepResult(currentFailure == 0 && !isCancelled.get())
    }

    /**
     * 处理单个仓库
     */
    private suspend fun processSingleRepository(
        context: StepExecutionContext,
        url: String,
        projectsDir: File,
        index: Int,
        totalRepos: Int,
        stepIndex: Int,
        totalSteps: Int,
    ): RepositoryResult {
        val repoName = gitUtils.extractRepoName(url)
        val targetDir = File(projectsDir, repoName)

        // 更新进度和日志
        context.onProgress(
            context.calculateTotalProgress(stepIndex, totalSteps, index.toFloat() / totalRepos),
            MessageBundle.message("status.processing.repo", repoName)
        )
        updateLog(url, MessageBundle.message("log.repo.start"), 0)

        // 检查是否已存在有效的 Git 仓库
        if (isValidGitRepository(targetDir)) {
            updateLog(url, MessageBundle.message("log.repo.exists"), 100)
            onStatsUpdate(1, 0)
            return RepositoryResult.SUCCESS
        }

        // 清理可能存在的无效目录
        cleanupInvalidDirectory(targetDir)

        // 执行克隆操作
        return performCloneOperation(
            context = context,
            url = url,
            repoName = repoName,
            targetDir = targetDir,
            projectsDir = projectsDir,
            index = index,
            totalRepos = totalRepos,
            stepIndex = stepIndex,
            totalSteps = totalSteps
        )
    }

    /**
     * 检查是否是有效的 Git 仓库
     */
    private fun isValidGitRepository(targetDir: File): Boolean {
        return targetDir.exists() && targetDir.isDirectory && File(targetDir, ".git").exists()
    }

    /**
     * 清理无效目录
     */
    private suspend fun cleanupInvalidDirectory(targetDir: File) {
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
    }

    /**
     * 执行克隆操作
     */
    private suspend fun performCloneOperation(
        context: StepExecutionContext,
        url: String,
        repoName: String,
        targetDir: File,
        projectsDir: File,
        index: Int,
        totalRepos: Int,
        stepIndex: Int,
        totalSteps: Int,
    ): RepositoryResult {
        updateLog(url, MessageBundle.message("log.clone.prepare"), 0)

        return try {
            var maxRepoPercent = 0
            val cloneResult = withTimeout(context.timeoutSeconds * 1000) {
                gitUtils.cloneRepository(
                    url,
                    repoName,
                    projectsDir,
                    onProgress = { phase, percent ->
                        // 确保单仓库进度不回跳（Git 输出有时会有波动）
                        if (percent > maxRepoPercent) {
                            maxRepoPercent = percent
                        }
                        // 计算 Clone 步骤内的子进度
                        val stepProgress = (index.toFloat() + maxRepoPercent / 100f) / totalRepos
                        context.onProgress(
                            context.calculateTotalProgress(stepIndex, totalSteps, stepProgress),
                            MessageBundle.message("status.clone.progress", repoName, phase, percent)
                        )
                        updateLog(
                            url,
                            MessageBundle.message("status.clone.progress", repoName, phase, percent),
                            percent
                        )
                    }
                )
            }

            handleCloneResult(cloneResult, url, targetDir)
        } catch (timeOutEx: TimeoutCancellationException) {
            handleCloneTimeout(timeOutEx, url, targetDir, context.timeoutSeconds)
        } catch (cancellationException: CancellationException) {
            updateLog(url, MessageBundle.message("log.cancelled"), 0)
            gitUtils.stopCurrentProcess()
            throw cancellationException
        }
    }

    /**
     * 处理克隆结果
     */
    private suspend fun handleCloneResult(
        cloneResult: com.songwh.bosprojectinit.utils.GitCloneResult,
        url: String,
        targetDir: File,
    ): RepositoryResult {
        return if (cloneResult.success) {
            updateLog(url, MessageBundle.message("log.clone.success"), 100)
            onStatsUpdate(1, 0)
            RepositoryResult.SUCCESS
        } else {
            cleanupAfterFailedClone(url, targetDir)
            updateLog(url, MessageBundle.message("log.clone.failed", cloneResult.exitCode, url), 0)
            onStatsUpdate(0, 1)
            RepositoryResult.FAILURE
        }
    }

    /**
     * 处理克隆超时
     */
    private suspend fun handleCloneTimeout(
        ex: TimeoutCancellationException,
        url: String,
        targetDir: File,
        timeoutSeconds: Long,
    ): RepositoryResult {
        updateLog(url, MessageBundle.message("log.repo.timeout", url), 0)
        gitUtils.stopCurrentProcess()

        cleanupAfterFailedClone(url, targetDir, MessageBundle.message("log.clean.timeout", timeoutSeconds))

        onStatsUpdate(0, 1)
        return RepositoryResult.FAILURE
    }


    /**
     * 克隆失败后清理目录
     */
    private suspend fun cleanupAfterFailedClone(
        url: String,
        targetDir: File,
        successMessage: String = MessageBundle.message("log.clean.success"),
    ) {
        runCatching {
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
                updateLog(url, successMessage, 0)
            }
        }.onFailure { e ->
            updateLog(url, MessageBundle.message("log.clean.failed", e.message ?: ""), 0)
        }
    }

    /**
     * 更新日志
     */
    private suspend fun updateLog(url: String, step: String, progress: Int) {
        val repoName = gitUtils.extractRepoName(url)
        logEntries[url] = LogEntry(repoName, step, progress)
        emitLogs()
    }

    /**
     * 发送日志更新
     */
    private suspend fun emitLogs() {
        val logs = logEntries.values.map { it.format() }
        onLogUpdate(logs)
    }
}

/**
 * 仓库处理结果枚举
 */
private enum class RepositoryResult {
    SUCCESS,
    FAILURE,
    CANCELLED
}
