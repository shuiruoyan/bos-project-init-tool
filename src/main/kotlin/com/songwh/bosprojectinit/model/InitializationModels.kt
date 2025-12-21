package com.songwh.bosprojectinit.model

import com.songwh.bosprojectinit.MessageBundle
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 日志条目模型，用于在 UI 列表或文本框中展示单条处理状态
 */
data class LogEntry(
    val repoName: String,   // 仓库名或系统标签
    var currentStep: String, // 当前正在执行的操作描述
    var progress: Int = 0    // 该条目对应的子进度 (0-100)
) {
    // 记录条目创建时的时分秒
    private val timestamp: String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))

    /**
     * 格式化输出，例如: [20:15:36]-[my-repo]-[50%]-[正在拉取代码]
     */
    fun format(): String = MessageBundle.message("log.entry.format", timestamp, repoName, progress, currentStep)
}

/**
 * 模块信息元数据，记录扫描到的 build.gradle 文件及其所属关系
 */
data class ModuleInfo(
    val repoName: String,     // 所属 Git 仓库名
    val moduleName: String,   // 模块目录名
    val moduleDir: File,      // 模块文件夹对象
    val buildGradle: File,    // build.gradle 文件对象
    val relativePath: String, // 相对于 projects 目录的路径 (如 repo/sub/module)
    val version: String? = null // 模块版本号
)

/**
 * 步骤执行结果包装类
 */
data class StepResult(val success: Boolean)

/**
 * 步骤执行上下文
 * 贯穿整个初始化流程，携带用户输入参数并作为各步骤间共享数据的媒介
 */
data class StepExecutionContext(
    val rootPath: String,           // 启动工程根路径
    val urls: List<String>,         // Git 仓库列表
    val timeoutSeconds: Long,       // 超时时间
    val cleanProjectsBeforeClone: Boolean, // 是否预清理
    val onProgress: suspend (Float, String) -> Unit, // 全局进度回调
    val onLogUpdate: suspend (List<String>) -> Unit, // 日志刷新回调
    val onStatsUpdate: suspend (Int, Int) -> Unit,    // 统计刷新回调
    var moduleInfos: List<ModuleInfo> = emptyList()   // 步骤间传递的扫描结果
) {
    /**
     * 计算全局综合进度
     * 将整个初始化过程划分为四个加权阶段：Clone(70%)、Settings(10%)、BuildLocal(10%)、ModuleBuildLocal(10%)
     * 
     * @param stepIndex 当前步骤索引 (0 到 3)
     * @param totalSteps 总步骤数
     * @param stepInternalProgress 当前步骤内部的微观进度 (0.0 到 1.0)
     */
    fun calculateTotalProgress(stepIndex: Int, totalSteps: Int, stepInternalProgress: Float): Float {
        val weights = when (totalSteps) {
            5 -> listOf(0.6f, 0.1f, 0.1f, 0.1f, 0.1f) // Clone, ConfigGradle, Settings, BuildLocal, ModuleBuildLocal
            4 -> listOf(0.7f, 0.1f, 0.1f, 0.1f)
            else -> List(totalSteps) { 1f / totalSteps }
        }
        
        // 累加之前步骤的固定权重
        var progressBefore = 0f
        for (i in 0 until stepIndex) {
            if (i < weights.size) {
                progressBefore += weights[i]
            }
        }
        
        // 加上当前步骤按比例分配的权重
        val currentWeight = if (stepIndex < weights.size) weights[stepIndex] else 0f
        
        return (progressBefore + stepInternalProgress.coerceIn(0f, 1f) * currentWeight).coerceIn(0f, 1f)
    }
}

/**
 * UI 层状态模型 (MVI/MVVM 风格)
 * 包含界面展示所需的所有可变状态
 */
data class ProjectInitState(
    val rootPath: String = "",
    val gitUrls: String = "",
    val timeoutSeconds: String = "60",
    val progress: Float = 0f,
    val statusText: String = "",
    val logLines: List<String> = emptyList(),
    val isRunning: Boolean = false,
    val cleanProjectsBeforeClone: Boolean = false,
    val successCount: Int = 0,
    val failureCount: Int = 0
)
