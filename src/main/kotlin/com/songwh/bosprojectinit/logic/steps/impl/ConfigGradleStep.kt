package com.songwh.bosprojectinit.logic.steps.impl

import com.songwh.bosprojectinit.MessageBundle
import com.songwh.bosprojectinit.logic.steps.IProjectInitStep
import com.songwh.bosprojectinit.model.LogEntry
import com.songwh.bosprojectinit.model.StepExecutionContext
import com.songwh.bosprojectinit.model.StepResult
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 同步 config.gradle 文件步骤
 * 1. 读取工程根目录上一层的 config.gradle 文件
 * 2. 搜索 projects 目录下所有工程中的 config.gradle 文件，并将其内容替换为上述文件的内容
 * 
 * 安全增强：
 * - 限制文件大小，防止 OOM
 * - 验证文件路径，防止路径穿越
 * - 检查符号链接，防止符号链接攻击
 */
class ConfigGradleStep(
    private val isCancelled: AtomicBoolean,
    private val logEntries: MutableMap<String, LogEntry>,
    private val onLogUpdate: suspend (List<String>) -> Unit
) : IProjectInitStep {
    override val nameKey: String = "log.step.configGradle"
    
    companion object {
        private const val MAX_FILE_SIZE = 10 * 1024 * 1024  // 10MB
    }

    override suspend fun execute(context: StepExecutionContext, stepIndex: Int, totalSteps: Int): StepResult {
        context.onProgress(context.calculateTotalProgress(stepIndex, totalSteps, 0f), MessageBundle.message("status.configgradle"))

        val rootDir = File(context.rootPath)
        val sourceConfig = File(rootDir.parentFile, "config.gradle")

        if (!sourceConfig.exists()) {
            updateLog("__config_gradle__", MessageBundle.message("log.configgradle.label"), 
                MessageBundle.message("log.configgradle.notfound", sourceConfig.absolutePath), 100, onLogUpdate)
            return StepResult(true)
        }
        
        // 安全检查：验证文件大小
        if (sourceConfig.length() > MAX_FILE_SIZE) {
            val errorMsg = "【文件过大】config.gradle 文件大小 (${sourceConfig.length()} 字节) 超过限制 ($MAX_FILE_SIZE 字节)，可能存在安全风险"
            updateLog("__config_gradle__", MessageBundle.message("log.configgradle.label"), errorMsg, 0, onLogUpdate)
            return StepResult(false)
        }
        
        // 安全检查：防止符号链接攻击
        if (Files.isSymbolicLink(sourceConfig.toPath())) {
            val errorMsg = "【安全错误】config.gradle 是符号链接，出于安全考虑不允许读取符号链接文件"
            updateLog("__config_gradle__", MessageBundle.message("log.configgradle.label"), errorMsg, 0, onLogUpdate)
            return StepResult(false)
        }

        updateLog("__config_gradle__", MessageBundle.message("log.configgradle.label"), 
            MessageBundle.message("log.configgradle.start"), 10, onLogUpdate)

        val configContent = try {
            sourceConfig.readText()
        } catch (e: Exception) {
            val errorMsg = "【读取失败】无法读取源 config.gradle 文件: ${e.message}"
            updateLog("__config_gradle__", MessageBundle.message("log.configgradle.label"), errorMsg, 0, onLogUpdate)
            return StepResult(false)
        }
        
        val projectsDir = File(rootDir, "projects")
        
        if (!projectsDir.exists() || !projectsDir.isDirectory) {
            return StepResult(true)
        }
        
        // 安全检查：验证 projects 目录不是符号链接
        if (Files.isSymbolicLink(projectsDir.toPath())) {
            val errorMsg = "【安全错误】projects 目录是符号链接，出于安全考虑不允许遍历符号链接目录"
            updateLog("__config_gradle__", MessageBundle.message("log.configgradle.label"), errorMsg, 0, onLogUpdate)
            return StepResult(false)
        }

        // 使用 walkTopDown 递归搜索所有 config.gradle 文件，同时过滤符号链接
        val allConfigFiles = projectsDir.walkTopDown()
            .onEnter { dir -> !Files.isSymbolicLink(dir.toPath()) }  // 不进入符号链接目录
            .filter { it.isFile && it.name == "config.gradle" && !Files.isSymbolicLink(it.toPath()) }
            .toList()

        val total = allConfigFiles.size
        if (total == 0) {
            updateLog("__config_gradle__", MessageBundle.message("log.configgradle.label"),
                MessageBundle.message("log.configgradle.notfound", "projects/**/config.gradle"), 100, onLogUpdate)
            return StepResult(true)
        }

        allConfigFiles.forEachIndexed { index, targetConfig ->
            if (isCancelled.get()) return StepResult(false)
            
            try {
                // 安全检查：验证目标文件路径在 projects 目录内
                val canonicalTarget = targetConfig.canonicalFile
                val canonicalProjects = projectsDir.canonicalFile
                if (!canonicalTarget.path.startsWith(canonicalProjects.path)) {
                    val errorMsg = "【安全警告】检测到路径穿越，跳过文件: ${targetConfig.absolutePath}"
                    updateLog("__config_gradle__", MessageBundle.message("log.configgradle.label"), errorMsg, 0, onLogUpdate)
                    return@forEachIndexed
                }
                
                targetConfig.writeText(configContent)
            } catch (e: Exception) {
                val errorMsg = "【写入失败】无法写入文件 ${targetConfig.absolutePath}: ${e.message}"
                updateLog("__config_gradle__", MessageBundle.message("log.configgradle.label"), errorMsg, 0, onLogUpdate)
                // 继续处理其他文件，不中断整个流程
            }

            val progress = (index + 1).toFloat() / total
            context.onProgress(context.calculateTotalProgress(stepIndex, totalSteps, progress), MessageBundle.message("status.configgradle"))
        }

        updateLog("__config_gradle__", MessageBundle.message("log.configgradle.label"), 
            MessageBundle.message("log.configgradle.success", sourceConfig.absolutePath), 100, onLogUpdate)

        return StepResult(true)
    }

    private suspend fun updateLog(key: String, repoLabel: String, step: String, progress: Int, onLogUpdate: suspend (List<String>) -> Unit) {
        logEntries[key] = LogEntry(repoLabel, step, progress)
        val logs = logEntries.values.map { it.format() }
        onLogUpdate(logs)
    }
}
