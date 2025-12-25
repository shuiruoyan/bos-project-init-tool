package com.songwh.bosprojectinit.utils

import com.intellij.util.io.awaitExit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap

data class GitCloneResult(
    val success: Boolean,
    val exitCode: Int,
    val errorMessage: String = "",
    val errorType: GitErrorType = GitErrorType.UNKNOWN
)

enum class GitErrorType {
    UNKNOWN,
    INVALID_URL,
    INVALID_REPO_NAME,
    PATH_TRAVERSAL,
    NETWORK_ERROR,
    PERMISSION_DENIED,
    TIMEOUT,
    CANCELLED
}

/**
 * 封装 Git 命令行操作的服务类
 * 支持进度解析、超时控制、任务取消以及彻底的进程树清理
 * 支持并发多个克隆任务的独立进程管理
 */
class GitUtils(private val isCancelled: AtomicBoolean) {

    // ✅ 改为 AtomicReference，确保线程安全的全局进程引用
    private val currentProcess = AtomicReference<Process?>(null)

    // ✅ 为每个克隆任务维护独立的进程引用，支持并发管理
    private val taskProcesses = ConcurrentHashMap<String, AtomicReference<Process?>>()

    /**
     * 外部请求取消操作，标记状态并停止当前进程
     */
    fun cancel() {
        isCancelled.set(true)
        stopCurrentProcess()
        // 停止所有并发任务的进程
        taskProcesses.forEach { (_, processRef) ->
            stopProcess(processRef)
        }
    }

    /**
     * 彻底停止进程树（线程安全）
     */
    private fun stopProcess(processRef: AtomicReference<Process?>) {
        val process = processRef.getAndSet(null) ?: return
        if (process.isAlive) {
            runCatching {
                // 销毁子进程（如 ssh.exe），防止其在父进程退出后继续运行导致文件占用
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
            }
        }
    }

    /**
     * 彻底停止当前运行的 Git 及其派生的所有子进程
     * 特别是针对 Windows 下 SSH 进程可能残留的问题
     */
    fun stopCurrentProcess() {
        stopProcess(currentProcess)
    }

    /**
     * 从 Git 远程地址中提取仓库名称
     * 例如: https://github.com/user/my-repo.git -> my-repo
     * 安全增强：过滤危险字符，防止路径穿越攻击
     */
    fun extractRepoName(url: String): String {
        val raw = url.substringAfterLast("/").substringBefore(".git")
        // 只保留字母、数字、下划线和连字符，防止路径穿越
        val sanitized = raw.replace(Regex("""[^\\w\\-]"""), "_")
        
        // 验证有效性
        if (sanitized.isEmpty() || sanitized.contains("..") || sanitized.startsWith(".")) {
            throw IllegalArgumentException("无效的仓库名称: $raw (提取自URL: ${sanitizeUrl(url)})")
        }
        
        return sanitized
    }
    
    /**
     * 验证 Git URL 格式，防止命令注入
     */
    private fun validateGitUrl(url: String): Boolean {
        // 支持 HTTPS、HTTP、Git 和 SSH 协议
        val gitUrlPattern = Regex(
            """^(https?://[\\w\\-\\.]+(?::\\d+)?/[\\w\\-\\./]+(?:\\.git)?|git@[\\w\\-\\.]+:[\\w\\-\\./]+(?:\\.git)?)$""",
            RegexOption.IGNORE_CASE
        )
        return gitUrlPattern.matches(url)
    }
    
    /**
     * 验证仓库名称，防止路径穿越攻击
     */
    private fun validateRepoName(repoName: String): Boolean {
        val safeNamePattern = Regex("""^[\\w\\-]+$""")
        return safeNamePattern.matches(repoName) && 
               !repoName.contains("..") && 
               !repoName.startsWith(".") &&
               repoName.length <= 255  // 防止过长的文件名
    }
    
    /**
     * 脱敏 URL 中的凭证信息
     */
    fun sanitizeUrl(url: String): String {
        return url.replace(Regex("""(https?://)([^@]+)@"""), "$1***@")
            .replace(Regex("""(git@[^:]+:)([^/]+)/"""), "$1***/")
    }

    /**
     * 执行 Git Clone 操作，并实时解析进度
     * 
     * @param url Git 仓库地址
     * @param repoName 本地文件夹名称
     * @param rootFile 克隆到的父目录
     * @param onProgress 进度回调 (阶段, 百分比)
     * @param taskId 任务唯一标识，支持并发克隆（可选）
     */
    suspend fun cloneRepository(
        url: String,
        repoName: String,
        rootFile: File,
        onProgress: suspend (phase: String, percent: Int) -> Unit,
        taskId: String? = null  // ✅ 新增：任务标识，支持并发
    ): GitCloneResult = withContext(Dispatchers.IO) {
        // 安全验证：防止命令注入和路径穿越
        if (!validateGitUrl(url)) {
            return@withContext GitCloneResult(
                success = false,
                exitCode = -1,
                errorMessage = "无效的 Git URL 格式: ${sanitizeUrl(url)}。URL 必须是有效的 HTTPS、HTTP、Git 或 SSH 协议地址。",
                errorType = GitErrorType.INVALID_URL
            )
        }
        
        if (!validateRepoName(repoName)) {
            return@withContext GitCloneResult(
                success = false,
                exitCode = -1,
                errorMessage = "无效的仓库名称: $repoName。仓库名称只能包含字母、数字、下划线和连字符，且不能包含路径穿越字符。",
                errorType = GitErrorType.INVALID_REPO_NAME
            )
        }
        
        // 验证目标目录的安全性
        val targetDir = File(rootFile, repoName)
        val canonicalTarget = targetDir.canonicalFile
        val canonicalRoot = rootFile.canonicalFile
        if (!canonicalTarget.path.startsWith(canonicalRoot.path)) {
            return@withContext GitCloneResult(
                success = false,
                exitCode = -1,
                errorMessage = "检测到路径穿越攻击: 目标路径 ${targetDir.absolutePath} 超出根目录 ${rootFile.absolutePath}",
                errorType = GitErrorType.PATH_TRAVERSAL
            )
        }
        val processBuilder = ProcessBuilder(
            "git", "clone",
            // "--depth", "1",// 浅克隆，不要
            "--config", "http.postBuffer=20971520",         // 设置缓冲区20M
            "--progress",          // 强制输出进度信息，即使是非交互模式
            // "--single-branch",     // 只拉取当前分支
            url,
            repoName
        )
        processBuilder.directory(rootFile)
        processBuilder.redirectErrorStream(true) // 合并标准输出和标准错误流
        
        // 关键设置：禁用 SSH 连接共享 (ControlMaster)
        // 确保 SSH 连接进程在 Git 退出时能被正确清理，避免 Windows 下的文件句柄占用
        processBuilder.environment()["GIT_SSH_COMMAND"] = "ssh -o ControlMaster=no"

        val process = processBuilder.start()
        
        // ✅ 支持两种进程管理模式：全局模式(旨在单个操作) + 任务模式(旨在并发)
        val processRef = if (taskId != null) {
            // 为并发任务创建独立的进程引用
            val ref = AtomicReference<Process?>(process)
            taskProcesses[taskId] = ref
            ref
        } else {
            // 对于非并发操作，使用全局进程引用
            currentProcess.set(process)
            currentProcess
        }

        try {
            coroutineScope {
                // 启动协程读取输出流
                val readerJob = launch(Dispatchers.IO) {
                    runCatching {
                        process.inputStream.bufferedReader().use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                if (isCancelled.get()) break // 响应取消信号

                                line?.let { outputLine ->
                                    // 解析类似 "Receiving objects: 50%" 的进度行
                                    val progressInfo = parseGitProgress(outputLine)
                                    progressInfo?.let {
                                        onProgress(progressInfo.first, progressInfo.second)
                                    }
                                }
                            }
                        }
                    }
                }

                // 等待进程结束
                val exitCode = process.awaitExit()
                readerJob.join() // 确保流读取协程也已结束
                
                if (isCancelled.get()) {
                    GitCloneResult(
                        success = false, 
                        exitCode = exitCode,
                        errorMessage = "克隆操作已被用户取消",
                        errorType = GitErrorType.CANCELLED
                    )
                } else if (exitCode == 0) {
                    GitCloneResult(success = true, exitCode = 0)
                } else {
                    // 根据退出码提供详细错误信息
                    val errorMsg = when (exitCode) {
                        128 -> "Git 仓库不存在或无权访问: ${sanitizeUrl(url)}"
                        129 -> "Git 命令使用错误"
                        in 1..127 -> "Git 克隆失败，退出码: $exitCode。请检查网络连接、仓库地址和访问权限。"
                        else -> "Git 进程异常终止，退出码: $exitCode"
                    }
                    GitCloneResult(
                        success = false, 
                        exitCode = exitCode,
                        errorMessage = errorMsg,
                        errorType = GitErrorType.NETWORK_ERROR
                    )
                }
            }
        } finally {
            // 无论何种退出情况，确保清理进程资源
            // ✅ 为了并发安全，直接使用 processRef 的 stopProcess
            stopProcess(processRef)
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
            
            // ✅ 并发模弋下也清理任务收及库
            if (taskId != null) {
                taskProcesses.remove(taskId)
            }
        }
    }

    /**
     * 使用正则表达式解析 Git 命令行输出中的进度百分比
     * 匹配示例: "Receiving objects:  45% (20/44)" -> (Receiving objects, 45)
     */
    private fun parseGitProgress(line: String): Pair<String, Int>? {
        val regex = Regex("""([\w\s]+):\s*(\d+)%""")
        val match = regex.find(line)
        return if (match != null) {
            val phase = match.groupValues[1].trim()
            val percent = match.groupValues[2].toIntOrNull() ?: 0
            Pair(phase, percent)
        } else {
            null
        }
    }
}
