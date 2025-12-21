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
    private val onStatsUpdate: suspend (Int, Int) -> Unit
) : IProjectInitStep {
    override val nameKey: String = "log.step.clone"

    override suspend fun execute(context: StepExecutionContext, stepIndex: Int, totalSteps: Int): StepResult {
        val projectsDir = File(context.rootPath, "projects")

        // 如果用户勾选了“清空 projects”，则先递归删除该目录
        if (context.cleanProjectsBeforeClone) {
            context.onProgress(context.calculateTotalProgress(stepIndex, totalSteps, 0f), MessageBundle.message("status.cleaning"))
            logEntries["__clean__"] = LogEntry(MessageBundle.message("log.system"), MessageBundle.message("log.cleaning"), 0)
            emitLogs(onLogUpdate)

            runCatching {
                if (projectsDir.exists()) {
                    val deleted = projectsDir.deleteRecursively()
                    if (!deleted) {
                        // 如果删除失败，通常是因为文件被 IDE 索引或其他进程占用
                        throw Exception("Failed to delete directory: ${projectsDir.absolutePath}. It might be used by another process.")
                    }
                }
            }.onFailure { e ->
                logEntries["__clean__"] = LogEntry(MessageBundle.message("log.system"), MessageBundle.message("log.clean.failed", e.message ?: ""), 0)
                emitLogs(onLogUpdate)
                return StepResult(false) // 清理失败视为步骤失败
            }
        }

        // 确保 projects 目录存在
        if (!projectsDir.exists()) {
            projectsDir.mkdirs()
        }

        if (context.cleanProjectsBeforeClone) {
            logEntries["__clean__"] = LogEntry(MessageBundle.message("log.system"), MessageBundle.message("log.clean.success"), 100)
            emitLogs(onLogUpdate)
        }

        val totalRepos = context.urls.size
        var currentSuccess = 0
        var currentFailure = 0

        // 遍历处理每个仓库地址
        context.urls.forEachIndexed { index, url ->
            if (isCancelled.get()) {
                updateLog(url, MessageBundle.message("log.cancelled"), 0, onLogUpdate)
                return StepResult(false)
            }

            val repoName = gitUtils.extractRepoName(url)
            val targetDir = File(projectsDir, repoName)

            context.onProgress(
                context.calculateTotalProgress(stepIndex, totalSteps, index.toFloat() / totalRepos),
                MessageBundle.message("status.processing.repo", repoName)
            )
            updateLog(url, MessageBundle.message("log.repo.start"), 0, onLogUpdate)

            // 检查目录是否已存在且是一个有效的 Git 仓库
            if (targetDir.exists() && targetDir.isDirectory && File(targetDir, ".git").exists()) {
                updateLog(url, MessageBundle.message("log.repo.exists"), 100, onLogUpdate)
                currentSuccess++
                onStatsUpdate(1, 0) // 通知外部成功数 +1
            } else {
                try {
                    // 如果文件夹存在但不是合法的 git 仓库（可能是上次中断残留），先强制删除
                    if (targetDir.exists()) {
                        targetDir.deleteRecursively()
                    }

                    updateLog(url, MessageBundle.message("log.clone.prepare"), 0, onLogUpdate)
                    
                    var maxRepoPercent = 0
                    // 在指定的超时时间内执行 Clone 操作
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
                                updateLog(url, MessageBundle.message("status.clone.progress", repoName, phase, percent), percent, onLogUpdate)
                            }
                        )
                    }

                    if (cloneResult.success) {
                        currentSuccess++
                        updateLog(url, MessageBundle.message("log.clone.success"), 100, onLogUpdate)
                        onStatsUpdate(1, 0)
                    } else {
                        currentFailure++
                        updateLog(url, MessageBundle.message("log.clone.failed", cloneResult.exitCode, url), 0, onLogUpdate)
                        onStatsUpdate(0, 1)
                    }
                } catch (ex: TimeoutCancellationException) {
                    // 处理克隆超时
                    updateLog(url, MessageBundle.message("log.repo.timeout", url), 0, onLogUpdate)
                    gitUtils.stopCurrentProcess() // 必须停掉底层的子进程
                    currentFailure++
                    onStatsUpdate(0, 1)
                } catch (ex: CancellationException) {
                    // 用户取消
                    updateLog(url, MessageBundle.message("log.cancelled"), 0, onLogUpdate)
                    gitUtils.stopCurrentProcess()
                    throw ex
                }
            }
        }

        // 只要没有仓库彻底失败（或是全部取消），则认为此步骤完成
        return StepResult(currentFailure == 0 && !isCancelled.get())
    }

    private suspend fun updateLog(url: String, step: String, progress: Int, onLogUpdate: suspend (List<String>) -> Unit) {
        val repoName = gitUtils.extractRepoName(url)
        logEntries[url] = LogEntry(repoName, step, progress)
        emitLogs(onLogUpdate)
    }

    private suspend fun emitLogs(onLogUpdate: suspend (List<String>) -> Unit) {
        val logs = logEntries.values.map { it.format() }
        onLogUpdate(logs)
    }
}
