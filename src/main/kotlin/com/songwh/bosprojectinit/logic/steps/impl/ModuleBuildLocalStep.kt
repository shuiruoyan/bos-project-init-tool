package com.songwh.bosprojectinit.logic.steps.impl

import com.songwh.bosprojectinit.MessageBundle
import com.songwh.bosprojectinit.logic.steps.IProjectInitStep
import com.songwh.bosprojectinit.model.LogEntry
import com.songwh.bosprojectinit.model.ModuleInfo
import com.songwh.bosprojectinit.model.StepExecutionContext
import com.songwh.bosprojectinit.model.StepResult
import com.songwh.bosprojectinit.utils.GitUtils
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
            
            // 从 URL 列表中提取所有有效的仓库名称，用于过滤需要处理的子模块
            val allowedRepos = context.urls.map { url ->
                GitUtils.extractRepoNameFromUrl(url)
            }.toSet()

            // 过滤出属于配置仓库的模块进行 build_local.gradle 构建
            val filteredModules = context.moduleInfos.filter { allowedRepos.contains(it.repoName) }

            // 执行批量转换逻辑，传入过滤后的模块列表作为处理对象，但替换依赖时仍可参考所有模块
            createModuleBuildLocalFiles(context.rootPath, filteredModules, context.moduleInfos, context, stepIndex, totalSteps)
            
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
        targetModules: List<ModuleInfo>,
        allModules: List<ModuleInfo>,
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

        val totalModules = targetModules.size
        targetModules.forEachIndexed { index, info ->
            if (isCancelled.get()) return@forEachIndexed

            val progress = (index.toFloat() / totalModules)
            context.onProgress(
                context.calculateTotalProgress(stepIndex, totalSteps, progress),
                MessageBundle.message("log.modulebuildlocal.progress", index + 1, totalModules)
            )

            processModule(info, allModules, rootPath)
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
    private fun processModule(info: ModuleInfo, allModules: List<ModuleInfo>, rootPath: String) {
        val buildGradle = info.buildGradle
        val buildLocalGradle = File(buildGradle.parentFile, "build_local.gradle")

        if (!buildGradle.exists()) return

        val originalContent = buildGradle.readText()
        // 核心逻辑：分析内容并替换
        var modifiedContent = transformDependencies(originalContent, allModules)

        // 追加 build-suffix.gradle.template 内容
        val suffixTemplate = File(rootPath, "build-suffix.gradle.template")
        if (suffixTemplate.exists()) {
            val suffixContent = suffixTemplate.readText()
            if (suffixContent.isNotBlank()) {
                if (!modifiedContent.endsWith("\n")) {
                    modifiedContent += "\n"
                }
                modifiedContent += "\n// --- Append from build-suffix.gradle.template ---\n"
                modifiedContent += suffixContent
            }
        }

        buildLocalGradle.writeText(modifiedContent)
    }

    /**
     * 依赖转换算法
     * 逻辑：匹配 fileTree 引用中的 JAR 包名，如果在已下载的模块列表中找到同名模块，则替换为 project 引用。
     */
    internal fun transformDependencies(content: String, allModules: List<ModuleInfo>): String {
        val lines = content.lines()
        val result = mutableListOf<String>()

        // 预处理模块匹配列表，包含 模块名-版本
        val moduleMatchers = allModules.map { module ->
            val fullName = if (module.version != null) "${module.moduleName}-${module.version}" else module.moduleName
            val projectPath = ":${module.repoName}.${module.moduleName}"
            fullName to projectPath
        }.sortedByDescending { it.first.length }

        // 正则表达式：匹配 fileTree 格式，支持多种配置前缀
        // 例如: implementation fileTree(dir: 'libs', include: ['swc-hcdm-business-1.0*.jar'])
        val jarPattern = """^\s*(compile|implementation|api|runtimeOnly|testImplementation|testApi)\s+fileTree\s*\(.*include\s*:\s*['"\[]\s*([^'"\]]+)\s*['"\]].*\)""".toRegex()

        for (line in lines) {
            // 如果该行已经被注释掉，则跳过替换
            if (line.trim().startsWith("//")) {
                result.add(line)
                continue
            }

            val matchResult = jarPattern.find(line)
            if (matchResult != null) {
                val config = matchResult.groupValues[1] // 配置关键字
                val jarInclude = matchResult.groupValues[2] // JAR包含模式，如 swc-hcdm-business-1.0*.jar

                if (!jarInclude.endsWith(".jar") && !jarInclude.contains("*")) {
                    result.add(line)
                    continue
                }

                // 查找所有匹配 of the module
                val matchedProjectPaths = mutableListOf<String>()
                
                // Remove .jar suffix if present, then remove trailing *
                var baseMatchName = jarInclude
                if (baseMatchName.endsWith(".jar")) {
                    baseMatchName = baseMatchName.substring(0, baseMatchName.length - 4)
                }
                baseMatchName = baseMatchName.removeSuffix("*")

                if (baseMatchName.isEmpty() || baseMatchName == "*") {
                    result.add(line)
                    continue
                }

                for ((fullName, projectPath) in moduleMatchers) {
                    // Case 1: Exact match or include pattern is longer (e.g., contains -SNAPSHOT)
                    if (baseMatchName.startsWith(fullName)) {
                        matchedProjectPaths.add(projectPath)
                    }
                    // Case 2: Wildcard match where module name starts with the prefix
                    // We only do this if it's a prefix-style wildcard (e.g., mod-*)
                    if (jarInclude.contains("*") && fullName.startsWith(baseMatchName)) {
                        matchedProjectPaths.add(projectPath)
                    }
                }

                if (matchedProjectPaths.isNotEmpty()) {
                    val indent = line.takeWhile { it.isWhitespace() }
                    result.add("$indent//@Replaced ${line.trim()}")
                    matchedProjectPaths.distinct().forEach { path ->
                        result.add("$indent$config project('$path')")
                    }
                    continue
                }
            }

            result.add(line)
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
