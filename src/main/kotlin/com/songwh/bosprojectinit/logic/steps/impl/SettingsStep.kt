package com.songwh.bosprojectinit.logic.steps.impl

import com.songwh.bosprojectinit.MessageBundle
import com.songwh.bosprojectinit.model.*
import com.songwh.bosprojectinit.logic.steps.IProjectInitStep
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 第二步：生成工程级 settings.gradle
 * 负责扫描 projects 目录下所有的子模块，并按特定格式（子目录即为模块名后缀）生成引用配置。
 */
class SettingsStep(
    private val isCancelled: AtomicBoolean,
    private val logEntries: MutableMap<String, LogEntry>,
    private val onLogUpdate: suspend (List<String>) -> Unit
) : IProjectInitStep {
    override val nameKey: String = "log.step.settings"

    override suspend fun execute(context: StepExecutionContext, stepIndex: Int, totalSteps: Int): StepResult {
        if (isCancelled.get()) return StepResult(false)

        return runCatching {
            val projectsDir = File(context.rootPath, "projects")

            // 扫描所有带有 build.gradle 的子目录，收集模块元数据
            context.moduleInfos = collectModuleInfos(projectsDir)

            context.onProgress(context.calculateTotalProgress(stepIndex, totalSteps, 0f), MessageBundle.message("status.settings"))
            
            // 根据模板和收集到的模块信息生成最终的 settings.gradle 文件
            generateSettingsGradle(context.rootPath, context.moduleInfos)
            
            context.onProgress(context.calculateTotalProgress(stepIndex, totalSteps, 1f), MessageBundle.message("status.settings"))
            StepResult(true)
        }.getOrElse { e ->
            updateCustomLog(
                "__settings__",
                MessageBundle.message("log.settings.label"),
                MessageBundle.message("log.error", e.message ?: ""),
                0
            )
            StepResult(false)
        }
    }

    /**
     * 递归扫描指定目录下的所有子模块
     * 识别标准：包含 build.gradle 文件的目录
     */
    internal fun collectModuleInfos(projectsDir: File): List<ModuleInfo> {
        if (!projectsDir.exists()) return emptyList()

        return projectsDir.walkTopDown()
            .filter { it.isFile && it.name == "build.gradle" }
            .mapNotNull { file ->
                // 计算相对于 projects 目录的路径
                val relativePath = projectsDir.toPath().relativize(file.parentFile.toPath()).toString().replace("\\", "/")
                val parts = relativePath.split("/").filter { it.isNotBlank() }
                if (parts.isEmpty()) return@mapNotNull null

                val repoName = parts.first() // 第一级目录通常是仓库名
                
                val moduleName = file.parentFile.name // 文件夹名作为模块名
                
                // 尝试从 build.gradle 中提取 version
                var version: String? = null
                try {
                    val content = file.readText()
                    val versionMatch = """version\s*=\s*['"]([^'"]+)['"]""".toRegex().find(content)
                    if (versionMatch != null) {
                        version = versionMatch.groupValues[1]
                    }
                } catch (e: Exception) {
                    // 忽略读取错误
                }

                ModuleInfo(repoName, moduleName, file.parentFile, file, relativePath, version)
            }
            .toList()
    }

    /**
     * 从 resources 中加载 settings.gradle 模板
     */
    internal fun loadSettingsTemplate(): String {
        val stream = javaClass.classLoader.getResourceAsStream("templates/settings.gradle.template")
            ?: error("settings.gradle template not found in resources")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /**
     * 组装模板内容，替换占位符
     */
    internal fun buildSettingsGradleContent(rootPath: String, baseDir: String, moduleInfos: List<ModuleInfo>): String {
        val template = loadSettingsTemplate()
        val rootName = File(rootPath).name

        val modulesByRepo = moduleInfos.groupBy { it.repoName }
        val includeBuilder = StringBuilder()
        val projectDirBuilder = StringBuilder()

        if (moduleInfos.isNotEmpty()) {
            // 生成 include ':repo.module' 语句
            modulesByRepo.forEach { (repo, modules) ->
                modules.sortedBy { it.moduleName }.forEach { info ->
                    includeBuilder.append("include ':${repo}.${info.moduleName}'\n")
                }
            }

            // 生成 project(':repo.module').projectDir = file(...) 语句
            modulesByRepo.forEach { (repo, modules) ->
                modules.sortedBy { it.moduleName }.forEach { info ->
                    projectDirBuilder.append("project(':${repo}.${info.moduleName}').projectDir = file(baseDir + \"/${info.relativePath}\")\n")
                }
            }
        }

        val includes = includeBuilder.toString().trimEnd()
        val projectDirs = projectDirBuilder.toString().trimEnd()

        return template
            .replace("{{ROOT_NAME}}", rootName)
            .replace("{{BASE_DIR}}", baseDir)
            .replace("{{INCLUDES}}", includes)
            .replace("{{PROJECT_DIRS}}", projectDirs)
    }

    /**
     * 将生成的内容写入到 rootPath/settings.gradle
     */
    private suspend fun generateSettingsGradle(
        rootPath: String,
        moduleInfos: List<ModuleInfo>
    ) {
        val settingsFile = File(rootPath, "settings.gradle")
        val projectsDir = File(rootPath, "projects")

        updateCustomLog(
            "__settings__",
            MessageBundle.message("log.settings.label"),
            MessageBundle.message("log.settings.updating"),
            0
        )

        val baseDir = projectsDir.absolutePath.replace("\\", "/")
        val content = buildSettingsGradleContent(rootPath, baseDir, moduleInfos)

        settingsFile.writeText(content)

        updateCustomLog(
            "__settings__",
            MessageBundle.message("log.settings.label"),
            MessageBundle.message("log.settings.updated"),
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
