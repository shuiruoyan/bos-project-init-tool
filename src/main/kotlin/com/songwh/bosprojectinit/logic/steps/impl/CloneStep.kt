package com.songwh.bosprojectinit.logic.steps.impl

import com.songwh.bosprojectinit.MessageBundle
import com.songwh.bosprojectinit.logic.steps.IProjectInitStep
import com.songwh.bosprojectinit.model.LogEntry
import com.songwh.bosprojectinit.model.StepExecutionContext
import com.songwh.bosprojectinit.model.StepResult
import com.songwh.bosprojectinit.utils.GitCloneResult
import com.songwh.bosprojectinit.utils.GitUtils
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
    private val LOG = Logger.getInstance(CloneStep::class.java)
    override val nameKey: String = "log.step.clone"
    
    // ✅ 进度追踪：存储每个仓库的当前进度 (0-100)
    private val repoProgressMap = ConcurrentHashMap<Int, AtomicInteger>()
    // ✅ 全局最大进度，防止回弹
    private val globalMaxProgress = AtomicInteger(0)

    override suspend fun execute(context: StepExecutionContext, stepIndex: Int, totalSteps: Int): StepResult {
        // 检查是否已取消
        if (isCancelled.get()) {
            return StepResult(false)
        }
        
        // ✅ 重置进度追踪
        repoProgressMap.clear()
        globalMaxProgress.set(0)

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
     * 处理所有仓库克隆 - ✅ 并发实现
     * 支持同时克隆每个仓库
     */
    private suspend fun processRepositories(
        context: StepExecutionContext,
        projectsDir: File,
        stepIndex: Int,
        totalSteps: Int,
    ): StepResult = coroutineScope {
        // 事先检查是否已取消
        if (isCancelled.get()) {
            return@coroutineScope StepResult(false)
        }
        val totalRepos = context.urls.size
        val maxConcurrentRepos = minOf(3, totalRepos)  // ⚠️ 限制并发数为 3 个git仓库数
        val semaphore = Semaphore(maxConcurrentRepos)

        // ✅ 滑动窗口并发：任何一个 clone 任务结束都会立刻释放 permit，从而启动下一个仓库
        val tasks = context.urls.mapIndexed { index, url ->
            async {
                if (isCancelled.get()) {
                    updateLog(url, MessageBundle.message("log.cancelled"), 0)
                    return@async RepositoryResult.CANCELLED
                }

                semaphore.withPermit {
                    if (isCancelled.get()) {
                        updateLog(url, MessageBundle.message("log.cancelled"), 0)
                        return@withPermit RepositoryResult.CANCELLED
                    }

                    // 单仓库固定超时：严格使用用户输入的 timeoutSeconds
                    val perRepoTimeout = (context.timeoutSeconds.coerceAtLeast(1) * 1000)

                    processSingleRepository(
                        context = context,
                        url = url,
                        projectsDir = projectsDir,
                        index = index,
                        totalRepos = totalRepos,
                        stepIndex = stepIndex,
                        totalSteps = totalSteps,
                        perRepoTimeout = perRepoTimeout,
                        taskId = "clone_${index}_${url.hashCode()}"  // ✅ 每个任务一个唯一 ID
                    )
                }
            }
        }

        val results = try {
            tasks.awaitAll()
        } catch (e: CancellationException) {
            tasks.forEach { it.cancel() }
            throw e
        }

        val failureCount = results.count { it == RepositoryResult.FAILURE }
        StepResult(failureCount == 0 && !isCancelled.get())
    }

    /**
     * 处理单个仓库 - ✅ 添加了全局异常捕获
     */
    private suspend fun processSingleRepository(
        context: StepExecutionContext,
        url: String,
        projectsDir: File,
        index: Int,
        totalRepos: Int,
        stepIndex: Int,
        totalSteps: Int,
        perRepoTimeout: Long = 60_000L,  // ✅ 推示值：60s
        taskId: String? = null,  // ✅ 任务标识
    ): RepositoryResult {
        return try {
            // 初始化该仓库的进度为 0
            repoProgressMap.computeIfAbsent(index) { AtomicInteger(0) }.set(0)

            val displayUrl = gitUtils.sanitizeGitUrlForDisplay(url)
            if (!gitUtils.isSupportedGitRemote(url)) {
                val reason = gitUtils.explainUnsupportedGitRemote(url) ?: "unsupported"
                updateLog(displayUrl, MessageBundle.message("log.error", "Unsafe git url ($reason)"), 0)
                onStatsUpdate(0, 1)
                return RepositoryResult.FAILURE
            }

            val repoName = gitUtils.extractRepoName(url)
            val targetDir = File(projectsDir, repoName)

            // 更新进度：使用正确的总体进度计算
            updateGlobalProgress(context, stepIndex, totalSteps, totalRepos)
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
            performCloneOperation(
                context = context,
                url = url,
                repoName = repoName,
                targetDir = targetDir,
                projectsDir = projectsDir,
                index = index,
                totalRepos = totalRepos,
                stepIndex = stepIndex,
                totalSteps = totalSteps,
                perRepoTimeout = perRepoTimeout,
                taskId = taskId
            )
        } catch (e: Exception) {
            // 全局异常捕获：记录详细错误信息
            val errorMsg = e.message ?: e.javaClass.simpleName
            updateLog(
                gitUtils.sanitizeGitUrlForDisplay(url),
                "发生错误: $errorMsg",
                0
            )
            onStatsUpdate(0, 1)
            LOG.warn("Unexpected error while processing repository clone", e)  // 打印到控制台
            RepositoryResult.FAILURE
        }
    }

    /**
     * 检查是否是有效的 Git 仓库
     */
    private fun isValidGitRepository(targetDir: File): Boolean {
        return targetDir.exists() && targetDir.isDirectory && File(targetDir, ".git").exists()
    }

    /**
     * 清理无效目录 - ✅ 修复了 suspend 中的阻塞问题
     */
    private suspend fun cleanupInvalidDirectory(
        targetDir: File,
        maxRetries: Int = 3,
        useDelay: Boolean = true
    ): Boolean {
        if (!targetDir.exists()) return true

        repeat(maxRetries) { attempt ->
            try {
                if (targetDir.deleteRecursively()) {
                    return true
                }
            } catch (e: Exception) {
                // 继续重试
            }

            if (useDelay && attempt < maxRetries - 1) {
                // ✅ 修复：使用 delay() 而不是 Thread.sleep()
                val delayMs = 100L * (attempt + 1) * (attempt + 1)
                try {
                    delay(delayMs)  // suspend 安全的延迟
                } catch (e: Exception) {
                    // 忽略
                }
            }
        }

        return false
    }

    /**
     * 执行克隆操作 - ✅ 添加了參数
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
        perRepoTimeout: Long = 60_000L,  // ✅ 新增：动态超时
        taskId: String? = null,  // ✅ 新增：任务标识
    ): RepositoryResult {
        updateLog(url, MessageBundle.message("log.clone.prepare"), 0)

        return try {
            var maxRepoPercent = 0
            val cloneResult = withTimeout(perRepoTimeout) {  // ✅ 使用动态超时
                gitUtils.cloneRepository(
                    url,
                    repoName,
                    projectsDir,
                    onProgress = { phase, percent ->
                        // 确保单仓库进度不回跳（Git 输出有时会有波动）
                        if (percent > maxRepoPercent) {
                            maxRepoPercent = percent
                        }
                        
                        // ✅ 更新当前仓库的进度
                        repoProgressMap.computeIfAbsent(index) { AtomicInteger(0) }.set(maxRepoPercent)
                        
                        // ✅ 更新总体进度（线程安全）
                        updateGlobalProgress(context, stepIndex, totalSteps, totalRepos, repoName, phase, percent)
                        
                        try {
                            updateLog(
                                url,
                                MessageBundle.message("status.clone.progress", repoName, phase, percent),
                                percent
                            )
                        } catch (e: Exception) {
                            // 避免日志更新失败中断克隆
                            LOG.warn("Log update error", e)
                        }
                    },
                    taskId = taskId  // ✅ 传递任务 ID
                )
            }

            handleCloneResult(cloneResult, url, targetDir)
        } catch (timeOutEx: TimeoutCancellationException) {
            handleCloneTimeout(timeOutEx, url, targetDir, context.timeoutSeconds)
        } catch (cancellationException: CancellationException) {
            updateLog(url, MessageBundle.message("log.cancelled"), 0)
            gitUtils.stopCurrentProcess()
            throw cancellationException
        } catch (e: Exception) {
            // ✅ 捕获其他异常：记录详细错误信息
            val errorMsg = e.message ?: e.javaClass.simpleName
            val stackTrace = e.stackTraceToString().take(500)
            updateLog(
                url,
                "克隆操作失败: $errorMsg\n堆栈: ${stackTrace.take(200)}",
                0
            )
            onStatsUpdate(0, 1)
            LOG.warn("Clone operation failed", e)
            RepositoryResult.FAILURE
        }
    }

    /**
     * 处理克隆结果 - ✅ 展示详细错误信息
     */
    private suspend fun handleCloneResult(
        cloneResult: GitCloneResult,
        url: String,
        targetDir: File,
    ): RepositoryResult {
        return if (cloneResult.success) {
            updateLog(url, MessageBundle.message("log.clone.success"), 100)
            onStatsUpdate(1, 0)
            RepositoryResult.SUCCESS
        } else {
            // ✅ 展示详细的错误信息
            val errorMsg = cloneResult.errorMessage.ifEmpty {
                "克隆失败: 退出码 ${cloneResult.exitCode}"
            }
            cleanupAfterFailedClone(url, targetDir)
            updateLog(url, MessageBundle.message("log.clone.failed", cloneResult.exitCode, errorMsg), 0)
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
        val displayUrl = gitUtils.sanitizeGitUrlForDisplay(url)
        updateLog(displayUrl, MessageBundle.message("log.repo.timeout", displayUrl), 0)
        gitUtils.stopCurrentProcess()

        cleanupAfterFailedClone(
            url = url,
            targetDir = targetDir,
            successMessage = MessageBundle.message("log.clean.timeout", timeoutSeconds),
            maxRetries = 1,
            useDelay = false
        )

        onStatsUpdate(0, 1)
        return RepositoryResult.FAILURE
    }


    /**
     * 克隆失败后清理目录 - ✅ 更新了清理逻辑
     */
    private suspend fun cleanupAfterFailedClone(
        url: String,
        targetDir: File,
        successMessage: String = MessageBundle.message("log.clean.success"),
        maxRetries: Int = 3,
        useDelay: Boolean = true,
    ) {
        val cleanupResult = cleanupInvalidDirectory(targetDir, maxRetries = maxRetries, useDelay = useDelay)
        if (cleanupResult) {
            updateLog(url, successMessage, 0)
        } else {
            // ✅ 即使清理失败也只记录日志，不中断流程
            updateLog(url, MessageBundle.message("log.clean.failed", "目录吃水偶尔仍是存在"), 0)
        }
    }

    /**
     * 更新日志 - ✅ 线程安全
     */
    private suspend fun updateLog(url: String, step: String, progress: Int) {
        val repoName = runCatching { gitUtils.extractRepoName(url) }.getOrElse { url }
        synchronized(logEntries) {
            logEntries[url] = LogEntry(repoName, step, progress)
        }
        emitLogs()
    }
    
    /**
     * 更新全局进度 - ✅ 线程安全，防止进度回弹
     */
    private suspend fun updateGlobalProgress(
        context: StepExecutionContext,
        stepIndex: Int,
        totalSteps: Int,
        totalRepos: Int,
        repoName: String? = null,
        phase: String? = null,
        percent: Int? = null
    ) {
        // ✅ 使用 synchronized 确保读取和计算的原子性
        val (currentProgressInt, statusMessage) = synchronized(repoProgressMap) {
            // 计算总体进度：所有仓库进度的平均值
            val totalProgress = repoProgressMap.values.sumOf { it.get() }
            val avgProgress = if (totalRepos > 0) totalProgress.toFloat() / totalRepos else 0f
            val stepProgress = avgProgress / 100f  // 转换为 0-1 范围
            
            val progressInt = (context.calculateTotalProgress(stepIndex, totalSteps, stepProgress) * 1000).toInt()
            val message = if (repoName != null && phase != null && percent != null) {
                MessageBundle.message("status.clone.progress", repoName, phase, percent)
            } else {
                // ✅ 使用已存在的消息键
                MessageBundle.message("log.step.clone")
            }
            
            Pair(progressInt, message)
        }
        
        // ✅ 防止进度回弹：只有当进度增加时才更新
        val maxProgressInt = globalMaxProgress.get()
        if (currentProgressInt > maxProgressInt) {
            if (globalMaxProgress.compareAndSet(maxProgressInt, currentProgressInt)) {
                val finalProgress = (currentProgressInt / 1000.0).toFloat()
                
                try {
                    context.onProgress(finalProgress, statusMessage)
                } catch (e: Exception) {
                    // 避免进度回调失败中断克隆
                    LOG.warn("Progress callback error", e)
                }
            }
        }
    }

    /**
     * 发送日志更新 - ✅ 线程安全
     */
    private suspend fun emitLogs() {
        val logs = synchronized(logEntries) {
            logEntries.values.map { it.format() }
        }
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
