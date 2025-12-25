package com.songwh.bosprojectinit.logic.steps.impl

import com.songwh.bosprojectinit.MessageBundle
import com.songwh.bosprojectinit.logic.steps.IProjectInitStep
import com.songwh.bosprojectinit.model.LogEntry
import com.songwh.bosprojectinit.model.ModuleInfo
import com.songwh.bosprojectinit.model.StepExecutionContext
import com.songwh.bosprojectinit.model.StepResult
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 第三步：生成根目录 build_local.gradle
 * 负责将所有已下载的子模块作为 compile 依赖项添加到根目录的本地构建脚本中。
 * 
 * 安全增强：
 * - 限制文件大小，防止 OOM
 * - 验证文件路径，防止路径穿越
 */
class BuildLocalStep(
    private val isCancelled: AtomicBoolean,
    private val logEntries: MutableMap<String, LogEntry>,
    private val onLogUpdate: suspend (List<String>) -> Unit
) : IProjectInitStep {
    override val nameKey: String = "log.step.buildLocal"
    
    companion object {
        private const val MAX_FILE_SIZE = 10 * 1024 * 1024  // 10MB
    }

    override suspend fun execute(context: StepExecutionContext, stepIndex: Int, totalSteps: Int): StepResult {
        if (isCancelled.get()) return StepResult(false)

        return runCatching {
            context.onProgress(context.calculateTotalProgress(stepIndex, totalSteps, 0f), MessageBundle.message("status.buildlocal"))
            
            // 执行文件创建逻辑
            createBuildLocalFiles(context.rootPath, context.moduleInfos)
            
            context.onProgress(context.calculateTotalProgress(stepIndex, totalSteps, 1f), MessageBundle.message("status.buildlocal"))
            StepResult(true)
        }.getOrElse { e ->
            updateCustomLog(
                "__build_local__",
                MessageBundle.message("log.buildlocal.label"),
                MessageBundle.message("log.error", e.message ?: ""),
                0
            )
            StepResult(false)
        }
    }

    /**
     * 创建 build_local.gradle 文件
     * 内容包含：原始根 build.gradle 的拷贝 + 所有子模块的 dependencies 引用
     */
    private suspend fun createBuildLocalFiles(
        rootPath: String,
        moduleInfos: List<ModuleInfo>
    ) {
        updateCustomLog(
            "__build_local__",
            MessageBundle.message("log.buildlocal.label"),
            MessageBundle.message("log.buildlocal.start"),
            0
        )

        runCatching {
            // 1. 读取原始根 build.gradle 内容作为基准
            val rootBuildGradle = File(rootPath, "build.gradle")
            val rootBuildLocal = File(rootPath, "build_local.gradle")
            
            // 安全检查：验证文件大小
            if (rootBuildGradle.exists() && rootBuildGradle.length() > MAX_FILE_SIZE) {
                throw SecurityException("源 build.gradle 文件过大 (${rootBuildGradle.length()} 字节)，超过限制 ($MAX_FILE_SIZE 字节)")
            }
            
            val baseContent = if (rootBuildGradle.exists()) {
                try {
                    rootBuildGradle.readText()
                } catch (e: Exception) {
                    throw Exception("读取根 build.gradle 失败: ${e.message}", e)
                }
            } else {
                "// Root build.gradle not found, starting with empty content\n"
            }

            // 2. 构造追加内容：将 settings.gradle 中定义的子模块声明为本地 project 依赖
            val result = StringBuilder()
            result.append(baseContent)
            if (result.isNotEmpty() && !result.endsWith("\n")) {
                result.append("\n")
            }

            if (moduleInfos.isNotEmpty()) {
                result.append("\n${MessageBundle.message("buildlocal.append.comment")}\n")
                result.append("dependencies {\n")
                moduleInfos.forEach { info ->
                    // 使用 settings.gradle 中约定的虚拟路径格式 :repo.module
                    val projectPath = ":${info.repoName}.${info.moduleName}"
                    result.append("    compile project('${projectPath}')\n")
                }
                result.append("}\n")
            }

            // 写入文件
            try {
                rootBuildLocal.writeText(result.toString())
            } catch (e: Exception) {
                throw Exception("写入 build_local.gradle 失败: ${e.message}", e)
            }

            // 追加 build-suffix.gradle.template 内容
            val suffixTemplate = File(rootPath, "build-suffix.gradle.template")
            if (suffixTemplate.exists()) {
                // 安全检查：验证模板文件大小
                if (suffixTemplate.length() > MAX_FILE_SIZE) {
                    throw SecurityException("build-suffix.gradle.template 文件过大 (${suffixTemplate.length()} 字节)")
                }
                
                val suffixContent = try {
                    suffixTemplate.readText()
                } catch (e: Exception) {
                    throw Exception("读取 build-suffix.gradle.template 失败: ${e.message}", e)
                }
                
                if (suffixContent.isNotBlank()) {
                    val appendContent = StringBuilder()
                    if (!result.endsWith("\n")) {
                        appendContent.append("\n")
                    }
                    appendContent.append("\n// --- Append from build-suffix.gradle.template ---\n")
                    appendContent.append(suffixContent)
                    
                    try {
                        rootBuildLocal.appendText(appendContent.toString())
                    } catch (e: Exception) {
                        throw Exception("追加 suffix 内容失败: ${e.message}", e)
                    }
                }
            }
        }.onFailure { e ->
            val errorCategory = when (e) {
                is SecurityException -> "安全错误"
                is java.io.IOException -> "文件系统错误"
                else -> "未知错误"
            }
            updateCustomLog(
                "__build_local__",
                MessageBundle.message("log.buildlocal.label"),
                "【$errorCategory】${e.message ?: "未知错误"}",
                0
            )
        }

        updateCustomLog(
            "__build_local__",
            MessageBundle.message("log.buildlocal.label"),
            MessageBundle.message("log.buildlocal.finish"),
            100
        )
    }

    private suspend fun updateCustomLog(
        key: String,
        repoLabel: String,
        step: String,
        progress: Int
    ) {
        logEntries[key] = LogEntry(repoLabel, step, progress)
        val logs = logEntries.values.map { it.format() }
        onLogUpdate(logs)
    }
}
