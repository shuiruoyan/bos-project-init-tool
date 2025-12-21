package com.songwh.bosprojectinit.logic.steps.impl

import com.songwh.bosprojectinit.MessageBundle
import com.songwh.bosprojectinit.logic.steps.IProjectInitStep
import com.songwh.bosprojectinit.model.LogEntry
import com.songwh.bosprojectinit.model.StepExecutionContext
import com.songwh.bosprojectinit.model.StepResult
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 同步 config.gradle 文件步骤
 * 1. 读取工程根目录上一层的 config.gradle 文件
 * 2. 搜索 projects 目录下所有工程中的 config.gradle 文件，并将其内容替换为上述文件的内容
 */
class ConfigGradleStep(
    private val isCancelled: AtomicBoolean,
    private val logEntries: MutableMap<String, LogEntry>,
    private val onLogUpdate: suspend (List<String>) -> Unit
) : IProjectInitStep {
    override val nameKey: String = "log.step.configGradle"

    override suspend fun execute(context: StepExecutionContext, stepIndex: Int, totalSteps: Int): StepResult {
        context.onProgress(context.calculateTotalProgress(stepIndex, totalSteps, 0f), MessageBundle.message("status.configgradle"))

        val rootDir = File(context.rootPath)
        val sourceConfig = File(rootDir.parentFile, "config.gradle")

        if (!sourceConfig.exists()) {
            updateLog("__config_gradle__", MessageBundle.message("log.configgradle.label"), 
                MessageBundle.message("log.configgradle.notfound", sourceConfig.absolutePath), 100, onLogUpdate)
            // 按照要求，如果找不到源文件，虽然报错，但这里我们认为步骤完成（或者根据业务逻辑决定是否失败）
            // 考虑到这是一个同步操作，如果源文件不存在，可能意味着不需要同步，或者是一个配置错误。
            // 暂且认为成功，但记录日志。
            return StepResult(true)
        }

        updateLog("__config_gradle__", MessageBundle.message("log.configgradle.label"), 
            MessageBundle.message("log.configgradle.start"), 10, onLogUpdate)

        val configContent = sourceConfig.readText()
        val projectsDir = File(rootDir, "projects")
        
        if (!projectsDir.exists() || !projectsDir.isDirectory) {
            return StepResult(true)
        }

        // 使用 walkTopDown 递归搜索所有 config.gradle 文件
        val allConfigFiles = projectsDir.walkTopDown()
            .filter { it.isFile && it.name == "config.gradle" }
            .toList()

        val total = allConfigFiles.size
        if (total == 0) {
            updateLog("__config_gradle__", MessageBundle.message("log.configgradle.label"),
                MessageBundle.message("log.configgradle.notfound", "projects/**/config.gradle"), 100, onLogUpdate)
            return StepResult(true)
        }

        allConfigFiles.forEachIndexed { index, targetConfig ->
            if (isCancelled.get()) return StepResult(false)

            targetConfig.writeText(configContent)

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
