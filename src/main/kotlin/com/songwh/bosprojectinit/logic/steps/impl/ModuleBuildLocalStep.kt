package com.songwh.bosprojectinit.logic.steps.impl

import com.songwh.bosprojectinit.MessageBundle
import com.songwh.bosprojectinit.logic.steps.IProjectInitStep
import com.songwh.bosprojectinit.model.LogEntry
import com.songwh.bosprojectinit.model.ModuleInfo
import com.songwh.bosprojectinit.model.StepExecutionContext
import com.songwh.bosprojectinit.model.StepResult
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 第四步：生成子模块级 build_local.gradle
 * 负责扫描各子模块的 build.gradle，将其中的远程 JAR 依赖尝试转换为对本地已下载工程的 project 依赖。
 */
class ModuleBuildLocalStep(
    private val isCancelled: AtomicBoolean,
    private val logEntries: MutableMap<String, LogEntry>,
    private val onLogUpdate: suspend (List<String>) -> Unit
) : IProjectInitStep {
    override val nameKey: String = "log.step.moduleBuildLocal"

    override suspend fun execute(context: StepExecutionContext, stepIndex: Int, totalSteps: Int): StepResult {
        if (isCancelled.get()) return StepResult(false)

        return runCatching {
            context.onProgress(context.calculateTotalProgress(stepIndex, totalSteps, 0f), MessageBundle.message("status.modulebuildlocal"))
            
            // 执行批量转换逻辑
            createModuleBuildLocalFiles(context.rootPath, context.moduleInfos, context, stepIndex, totalSteps)
            
            context.onProgress(context.calculateTotalProgress(stepIndex, totalSteps, 1f), MessageBundle.message("status.modulebuildlocal"))
            StepResult(true)
        }.getOrElse { e ->
            updateCustomLog(
                "__module_build_local__",
                MessageBundle.message("log.modulebuildlocal.label"),
                MessageBundle.message("log.error", e.message ?: ""),
                0
            )
            StepResult(false)
        }
    }

    /**
     * 为列表中的每个模块生成 build_local.gradle
     */
    private suspend fun createModuleBuildLocalFiles(
        rootPath: String,
        moduleInfos: List<ModuleInfo>,
        context: StepExecutionContext,
        stepIndex: Int,
        totalSteps: Int
    ) {
        updateCustomLog(
            "__module_build_local__",
            MessageBundle.message("log.modulebuildlocal.label"),
            MessageBundle.message("log.modulebuildlocal.start"),
            0
        )

        val totalModules = moduleInfos.size
        moduleInfos.forEachIndexed { index, info ->
            if (isCancelled.get()) return@forEachIndexed

            val progress = (index.toFloat() / totalModules)
            context.onProgress(
                context.calculateTotalProgress(stepIndex, totalSteps, progress),
                MessageBundle.message("log.modulebuildlocal.progress", index + 1, totalModules)
            )

            processModule(info, moduleInfos)
        }

        updateCustomLog(
            "__module_build_local__",
            MessageBundle.message("log.modulebuildlocal.label"),
            MessageBundle.message("log.modulebuildlocal.finish"),
            100
        )
    }

    /**
     * 处理单个模块的依赖转换
     */
    private fun processModule(info: ModuleInfo, allModules: List<ModuleInfo>) {
        val buildGradle = info.buildGradle
        val buildLocalGradle = File(buildGradle.parentFile, "build_local.gradle")

        if (!buildGradle.exists()) return

        val originalContent = buildGradle.readText()
        // 核心逻辑：分析内容并替换
        val modifiedContent = transformDependencies(originalContent, allModules)

        buildLocalGradle.writeText(modifiedContent)
    }

    /**
     * 依赖转换算法
     * 逻辑：匹配 fileTree 引用中的 JAR 包名，如果在已下载的模块列表中找到同名模块，则替换为 project 引用。
     */
    internal fun transformDependencies(content: String, allModules: List<ModuleInfo>): String {
        val lines = content.lines()
        val result = mutableListOf<String>()
        
        // 按模块名长度降序排序，优先匹配长的
        val sortedModules = allModules.sortedByDescending { it.moduleName.length }
        
        // 正则表达式：支持多种配置前缀 (compile, implementation等)
        // 专门匹配 fileTree 格式，例如: implementation fileTree(dir: 'libs', include: ['swc-hcdm-business*.jar'])
        // 排除掉已经注释掉的行 (以 // 开头)
        // 改进正则：捕获 include 中的内容，支持更复杂的匹配
        val jarPattern = """^\s*(compile|implementation|api|runtimeOnly|testImplementation|testApi)\s+fileTree\s*\(.*include\s*:\s*['"\[]\s*([^'"\]]+\.jar)\s*['"\]].*\)""".toRegex()
        
        for (line in lines) {
            // 如果该行已经被注释掉，则跳过替换
            if (line.trim().startsWith("//")) {
                result.add(line)
                continue
            }

            var transformedLine = line
            val matchResult = jarPattern.find(line)
            
            if (matchResult != null) {
                val config = matchResult.groupValues[1] // 配置关键字
                val jarInclude = matchResult.groupValues[2] // JAR包含模式，如 swc-hcdm-business*.jar
                
                // 查找匹配的模块
                // 规则：jarInclude 必须以模块名开头，且后面紧跟着 '-' 或 数字 或 '*'
                val matchingModule = sortedModules.find { module ->
                    val moduleName = module.moduleName
                    if (jarInclude.startsWith(moduleName)) {
                        val remainder = jarInclude.substring(moduleName.length)
                        // 剩余部分必须以 '-' 或 数字 或 '*' 开头
                        remainder.startsWith("-") || remainder.startsWith("*") || (remainder.isNotEmpty() && remainder[0].isDigit())
                    } else {
                        false
                    }
                }
                
                if (matchingModule != null) {
                    val projectPath = ":${matchingModule.repoName}.${matchingModule.moduleName}"
                    val indent = line.takeWhile { it.isWhitespace() }
                    // 注释掉原行，追加新的 project 引用，保持原始缩进
                    transformedLine = "$indent//@Tool ${line.trim()}\n$indent$config project('$projectPath')"
                }
            }
            
            result.add(transformedLine)
        }
        
        return result.joinToString("\n")
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
