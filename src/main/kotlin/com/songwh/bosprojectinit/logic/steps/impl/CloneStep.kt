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
import java.nio.file.Files
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
                // 安全检查：防止删除符号链接目录
                if (Files.isSymbolicLink(projectsDir.toPath())) {
                    throw SecurityException("【安全错误】projects 目录是符号链接，为了安全不允许删除符号链接目录")
                }
                
                val deleted = deleteRecursivelySafe(projectsDir)
                if (!deleted) {
                    throw Exception("删除目录失败: ${projectsDir.absolutePath}。可能被其他进程占用或权限不足。")
                }
            }
            logEntries["__clean__"] =
                LogEntry(MessageBundle.message("log.system"), MessageBundle.message("log.clean.success"), 100)
            emitLogs()
            true
        }.getOrElse { e ->
            val errorMsg = when (e) {
                is SecurityException -> e.message ?: "安全错误"
                else -> MessageBundle.message("log.clean.failed", e.message ?: "")
            }
            logEntries["__clean__"] = LogEntry(
                MessageBundle.message("log.system"),
                errorMsg,
                0
            )
            emitLogs()
            false
        }
    }
    
    /**
     * 安全地递归删除目录，防止符号链接攻击
     */
    private fun deleteRecursivelySafe(dir: File, maxDepth: Int = 20): Boolean {
        return try {
            deleteRecursivelyWithDepth(dir, 0, maxDepth)
        } catch (e: Exception) {
            LOG.warn("Failed to delete directory recursively: ${dir.absolutePath}", e)
            false
        }
    }
    
    /**
     * 限制深度的递归删除，防止无限递归
     */
    private fun deleteRecursivelyWithDepth(file: File, currentDepth: Int, maxDepth: Int): Boolean {
        if (currentDepth > maxDepth) {
            LOG.warn("Exceeded max depth ($maxDepth) while deleting: ${file.absolutePath}")
            return false
        }
        
        // 防止符号链接攻击：不跟随符号链接
        if (Files.isSymbolicLink(file.toPath())) {
            return try {
                Files.delete(file.toPath())
                true
            } catch (e: Exception) {
                false
            }
        }
        
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteRecursivelyWithDepth(child, currentDepth + 1, maxDepth)
            }
        }
        
        return try {
            file.delete()
        } catch (e: Exception) {
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
            val sanitizedUrl = gitUtils.sanitizeUrl(url)
            val errorCategory = categorizeException(e)
            val errorMsg = "【${errorCategory.first}】${errorCategory.second}\n" +
                          "仓库: $sanitizedUrl\n" +
                          "异常类型: ${e.javaClass.simpleName}\n" +
                          "错误详情: ${e.message ?: "未知错误"}"
            updateLog(url, errorMsg, 0)
            onStatsUpdate(0, 1)
            LOG.warn("Unexpected error while processing repository $sanitizedUrl", e)
            RepositoryResult.FAILURE
        }
    }
    
    /**
     * 对异常进行分类，提供更友好的错误说明
     */
    private fun categorizeException(e: Exception): Pair<String, String> {
        return when (e) {
            is java.io.IOException -> Pair("文件系统错误", "无法访问或写入文件系统，可能原因：磁盘空间不足、权限不足或目录被占用")
            is java.nio.file.AccessDeniedException -> Pair("权限错误", "没有足够的权限访问目标目录，请检查文件夹权限")
            is java.nio.file.FileAlreadyExistsException -> Pair("文件冲突", "目标目录已存在且无法覆盖")
            is java.nio.file.NoSuchFileException -> Pair("路径错误", "指定的路径不存在")
            is SecurityException -> Pair("安全限制", "操作被安全策略阻止")
            is IllegalArgumentException -> Pair("参数错误", e.message ?: "提供的参数不符合要求")
            is OutOfMemoryError -> Pair("内存不足", "系统内存不足，无法完成操作")
            else -> Pair("未知错误", "发生了预料之外的错误: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * 检查是否是有效的 Git 仓库
     */
    private fun isValidGitRepository(targetDir: File): Boolean {
        return targetDir.exists() && targetDir.isDirectory && File(targetDir, ".git").exists()
    }

    /**
     * 清理无效目录 - ✅ 修复了 suspend 中的阻塞问题，增强安全性
     */
    private suspend fun cleanupInvalidDirectory(
        targetDir: File,
        maxRetries: Int = 3,
        useDelay: Boolean = true
    ): Boolean {
        if (!targetDir.exists()) return true
        
        // 安全检查：防止删除符号链接目录
        if (Files.isSymbolicLink(targetDir.toPath())) {
            LOG.warn("Refusing to delete symbolic link directory: ${targetDir.absolutePath}")
            return try {
                Files.delete(targetDir.toPath())
                true
            } catch (e: Exception) {
                LOG.warn("Failed to delete symbolic link", e)
                false
            }
        }

        repeat(maxRetries) { attempt ->
            try {
                if (deleteRecursivelySafe(targetDir)) {
                    return true
                }
            } catch (e: Exception) {
                LOG.warn("Delete attempt ${attempt + 1} failed for ${targetDir.absolutePath}", e)
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
            val sanitizedUrl = gitUtils.sanitizeUrl(url)
            val errorCategory = categorizeException(e)
            val errorMsg = "【${errorCategory.first}】${errorCategory.second}\n" +
                          "仓库: $sanitizedUrl\n" +
                          "异常: ${e.javaClass.simpleName}: ${e.message ?: "未知"}"
            updateLog(url, errorMsg, 0)
            onStatsUpdate(0, 1)
            LOG.warn("Clone operation failed for $sanitizedUrl", e)
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
            val detailedError = buildDetailedErrorMessage(cloneResult, url)
            cleanupAfterFailedClone(url, targetDir)
            updateLog(url, detailedError, 0)
            onStatsUpdate(0, 1)
            LOG.warn("Clone failed for ${gitUtils.sanitizeUrl(url)}: ${cloneResult.errorMessage}")
            RepositoryResult.FAILURE
        }
    }
    
    /**
     * 构建详细的错误信息
     */
    private fun buildDetailedErrorMessage(result: GitCloneResult, url: String): String {
        val sanitizedUrl = gitUtils.sanitizeUrl(url)
        return when (result.errorType) {
            com.songwh.bosprojectinit.utils.GitErrorType.INVALID_URL -> 
                "【安全错误】${result.errorMessage}"
            com.songwh.bosprojectinit.utils.GitErrorType.INVALID_REPO_NAME -> 
                "【安全错误】${result.errorMessage}"
            com.songwh.bosprojectinit.utils.GitErrorType.PATH_TRAVERSAL -> 
                "【安全错误】${result.errorMessage}"
            com.songwh.bosprojectinit.utils.GitErrorType.NETWORK_ERROR -> 
                "【网络错误】${result.errorMessage} (仓库: $sanitizedUrl)"
            com.songwh.bosprojectinit.utils.GitErrorType.TIMEOUT -> 
                "【超时错误】克隆超时: $sanitizedUrl"
            com.songwh.bosprojectinit.utils.GitErrorType.CANCELLED -> 
                "【已取消】克隆操作已被用户取消: $sanitizedUrl"
            com.songwh.bosprojectinit.utils.GitErrorType.PERMISSION_DENIED -> 
                "【权限错误】无权访问仓库: $sanitizedUrl"
            else -> {
                val errorMsg = result.errorMessage.ifEmpty {
                    "克隆失败: 退出码 ${result.exitCode}"
                }
                "【未知错误】$errorMsg (仓库: $sanitizedUrl)"
            }
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
        val sanitizedUrl = gitUtils.sanitizeUrl(url)
        val errorMsg = "【超时错误】仓库 $sanitizedUrl 克隆超时 (超过 ${timeoutSeconds}秒)。可能原因：\n" +
                      "  1. 网络连接速度过慢\n" +
                      "  2. 仓库体积过大\n" +
                      "  3. 防火墙或代理设置问题\n" +
                      "建议：增加超时时间或检查网络配置"
        updateLog(url, errorMsg, 0)
        gitUtils.stopCurrentProcess()

        cleanupAfterFailedClone(
            url = url,
            targetDir = targetDir,
            successMessage = MessageBundle.message("log.clean.timeout", timeoutSeconds),
            maxRetries = 1,
            useDelay = false
        )

        onStatsUpdate(0, 1)
        LOG.warn("Clone timeout for $sanitizedUrl after ${timeoutSeconds}s")
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
        val repoName = gitUtils.extractRepoName(url)
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
