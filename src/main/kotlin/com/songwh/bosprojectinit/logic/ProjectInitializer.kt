package com.songwh.bosprojectinit.logic

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.songwh.bosprojectinit.MessageBundle
import com.songwh.bosprojectinit.logic.steps.IProjectInitStep
import com.songwh.bosprojectinit.logic.steps.impl.BuildLocalStep
import com.songwh.bosprojectinit.logic.steps.impl.CloneStep
import com.songwh.bosprojectinit.logic.steps.impl.ConfigGradleStep
import com.songwh.bosprojectinit.logic.steps.impl.ModuleBuildLocalStep
import com.songwh.bosprojectinit.logic.steps.impl.SettingsStep
import com.songwh.bosprojectinit.model.LogEntry
import com.songwh.bosprojectinit.model.ModuleInfo
import com.songwh.bosprojectinit.model.StepExecutionContext
import com.songwh.bosprojectinit.utils.GitUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 项目初始化器核心类
 * 负责编排整个工程初始化的各个步骤，并维护全局执行状态（进度、日志、成功/失败统计）
 */
class ProjectInitializer(private val project: Project) {

    // 用于原子化管理任务取消状态的标志
    private val isCancelled = AtomicBoolean(false)

    // Git 操作工具类，传入取消标志以便在操作中途响应中断
    private val gitUtils = GitUtils(isCancelled)

    // 有序映射，存储各步骤和各仓库的日志条目，确保日志按添加顺序展示
    private val logEntries = LinkedHashMap<String, LogEntry>()
    
    // 线程安全的计数器，用于统计仓库处理结果
    private var successCount = AtomicInteger(0)
    private var failureCount = AtomicInteger(0)

    /**
     * 请求取消当前正在执行的所有初始化任务
     * 会同时通知 GitUtils 终止底层的子进程
     */
    fun cancel() {
        gitUtils.cancel()
    }

    /**
     * 判断当前任务是否已被用户或系统取消
     */
    fun isCancelled(): Boolean = isCancelled.get()

    /**
     * 执行项目初始化主流程
     * 
     * @param rootPath 启动工程的根目录路径
     * @param urls 需要 Clone 的 Git 仓库地址列表
     * @param timeoutSeconds 单个仓库拉取的最长超时时间（秒）
     * @param cleanProjectsBeforeClone 是否在开始前清空已有的 projects 目录
     * @param onProgress 进度回调 (0.0~1.0, 状态文本)
     * @param onLogUpdate 全量日志列表回调，用于 UI 刷新
     * @param onStatsUpdate 成功与失败数量统计回调
     */
    suspend fun initialize(
        rootPath: String,
        urls: List<String>,
        timeoutSeconds: Long,
        cleanProjectsBeforeClone: Boolean = false,
        onProgress: suspend (Float, String) -> Unit,
        onLogUpdate: suspend (List<String>) -> Unit,
        onStatsUpdate: suspend (Int, Int) -> Unit = { _, _ -> }
    ) {
        try {
            // 在 IO 调度器中执行密集的文件/网络操作
            withContext(Dispatchers.IO) {
                // 重置统计数据
                successCount.set(0)
                failureCount.set(0)

                // 定义初始化流程的所有步骤
                val steps = listOf<IProjectInitStep>(
                    // 1. 代码拉取阶段
                    CloneStep(gitUtils, isCancelled, logEntries, onLogUpdate) { s, f ->
                        successCount.addAndGet(s)
                        failureCount.addAndGet(f)
                        onStatsUpdate(successCount.get(), failureCount.get())
                    },
                    // 2. 同步 config.gradle
                    ConfigGradleStep(isCancelled, logEntries, onLogUpdate),
                    // 3. 配置工程 settings.gradle
                    SettingsStep(isCancelled, logEntries, onLogUpdate),
                    // 4. 配置根目录 build_local.gradle
                    BuildLocalStep(isCancelled, logEntries, onLogUpdate),
                    // 5. 为每个子模块生成 build_local.gradle (处理依赖转换)
                    ModuleBuildLocalStep(isCancelled, logEntries, onLogUpdate)
                )

                // 构造步骤执行上下文
                val context = StepExecutionContext(
                    rootPath = rootPath,
                    urls = urls,
                    timeoutSeconds = timeoutSeconds,
                    cleanProjectsBeforeClone = cleanProjectsBeforeClone,
                    onProgress = onProgress,
                    onLogUpdate = onLogUpdate,
                    onStatsUpdate = onStatsUpdate
                )

                // 顺序执行各个步骤
                steps.forEachIndexed { index, step ->
                    if (isCancelled.get()) return@withContext

                    val stepName = MessageBundle.message(step.nameKey)
                    // 在日志中记录步骤开始
                    updateCustomLog("__step__${index}", stepName, MessageBundle.message("log.step.start", stepName), 0, onLogUpdate)

                    // 执行具体步骤逻辑
                    val result = step.execute(context, index, steps.size)

                    // 处理执行结果
                    if (result.success && !isCancelled.get()) {
                        updateCustomLog("__step__${index}", stepName, MessageBundle.message("log.step.success", stepName), 100, onLogUpdate)
                    } else {
                        updateCustomLog("__step__${index}", stepName, MessageBundle.message("log.step.failed", stepName), 0, onLogUpdate)
                        return@withContext // 关键步骤失败，中断后续流程
                    }
                }

                // 流程正常结束
                if (!isCancelled.get()) {
                    onProgress(1f, MessageBundle.message("status.complete"))
                    logEntries["__complete__"] = LogEntry(MessageBundle.message("log.system"), MessageBundle.message("log.all.finished"), 100)
                    emitLogs(onLogUpdate)
                }
            }
        } finally {
            // 在后台线程刷新文件系统，让 IDE 感知到文件变动（如新生成的 gradle 文件）
            val virtualFile = LocalFileSystem.getInstance().findFileByPath(rootPath)
            if (virtualFile != null) {
                virtualFile.refresh(true, true)
            } else {
                LocalFileSystem.getInstance().refresh(true)
            }
        }
    }

    /**
     * 更新特定仓库的日志状态并推送更新
     */
    private suspend fun updateLog(
        url: String,
        step: String,
        progress: Int,
        onLogUpdate: suspend (List<String>) -> Unit
    ) {
        val repoName = gitUtils.extractRepoName(url)
        logEntries[url] = LogEntry(repoName, step, progress)
        emitLogs(onLogUpdate)
    }

    /**
     * 收集所有日志条目并格式化为字符串列表发送给 UI
     */
    private suspend fun emitLogs(onLogUpdate: suspend (List<String>) -> Unit) {
        val logs = logEntries.values.map { it.format() }
        onLogUpdate(logs)
    }

    /**
     * 更新自定义键名的日志条目（如系统通知、总体步骤状态）
     */
    private suspend fun updateCustomLog(
        key: String,
        repoLabel: String,
        step: String,
        progress: Int,
        onLogUpdate: suspend (List<String>) -> Unit
    ) {
        logEntries[key] = LogEntry(repoLabel, step, progress)
        emitLogs(onLogUpdate)
    }
}
