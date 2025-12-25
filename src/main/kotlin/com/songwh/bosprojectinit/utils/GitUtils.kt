package com.songwh.bosprojectinit.utils

import com.intellij.util.io.awaitExit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
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
    UNKNOWN
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
     */
    fun extractRepoName(url: String): String {
        return extractRepoNameFromUrl(url)
    }

    internal fun isSupportedGitRemote(url: String): Boolean = isSupportedGitRemoteUrl(url)

    internal fun explainUnsupportedGitRemote(url: String): String? = validateGitRemoteUrl(url)

    internal fun sanitizeGitUrlForDisplay(url: String): String {
        val normalized = url.trim().replace(Regex("[\r\n\u0000]"), "")

        val maskedUserInfo = runCatching {
            val uri = URI(normalized)
            if (uri.userInfo.isNullOrBlank()) return@runCatching normalized
            URI(
                uri.scheme,
                "***",
                uri.host,
                uri.port,
                uri.path,
                uri.query,
                uri.fragment
            ).toString()
        }.getOrDefault(normalized)

        return maskedUserInfo
            .replace(Regex("(?i)(access_token|token|password|passwd|pwd)=([^&]+)"), "$1=***")
    }

    internal fun buildCloneCommand(url: String, repoName: String): List<String> {
        return listOf(
            "git", "clone",
            "--config", "http.postBuffer=20971520",
            "--progress",
            "--",
            url,
            repoName
        )
    }

    companion object {
        internal fun validateGitRemoteUrl(url: String): String? {
            val normalized = url.trim()
            if (normalized.isEmpty()) return "empty"
            if (normalized.any { it == '\n' || it == '\r' || it == '\u0000' }) return "contains control characters"
            if (normalized.startsWith("-")) return "starts with '-'"
            if (normalized.startsWith("file:", ignoreCase = true)) return "file scheme is not allowed"
            if (normalized.contains("\\")) return "contains backslash"

            return when {
                normalized.startsWith("http://", ignoreCase = true) || normalized.startsWith("https://", ignoreCase = true) -> {
                    val ok = runCatching {
                        val uri = URI(normalized)
                        uri.host != null
                    }.getOrDefault(false)
                    if (ok) null else "invalid http(s) url"
                }

                normalized.startsWith("git@") -> {
                    val scpLike = Regex("""^git@[^\s:]+:[^\s]+$""")
                    if (scpLike.matches(normalized)) null else "invalid scp-like ssh url"
                }

                normalized.startsWith("ssh://", ignoreCase = true) || normalized.startsWith("git://", ignoreCase = true) -> {
                    val ok = runCatching {
                        val uri = URI(normalized)
                        uri.host != null
                    }.getOrDefault(false)
                    if (ok) null else "invalid ssh/git url"
                }

                else -> "unsupported scheme"
            }
        }

        internal fun isSupportedGitRemoteUrl(url: String): Boolean {
            return validateGitRemoteUrl(url) == null
        }

        internal fun extractGitErrorLine(line: String): String? {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return null
            val lowered = trimmed.lowercase()
            return when {
                lowered.startsWith("fatal:") -> trimmed
                lowered.startsWith("error:") -> trimmed
                lowered.contains("permission denied") -> trimmed
                lowered.contains("repository not found") -> trimmed
                else -> null
            }
        }

        internal fun extractRepoNameFromUrl(url: String): String {
            val normalized = url.trim()
            val rawName = extractRawRepoName(normalized)
            return sanitizeRepoName(rawName)
        }

        internal fun isSafeRepoName(repoName: String): Boolean {
            return runCatching {
                sanitizeRepoName(repoName)
                true
            }.getOrDefault(false)
        }

        private fun extractRawRepoName(url: String): String {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) throw IllegalArgumentException("Empty git url")

            val candidate = when {
                trimmed.startsWith("git@") && trimmed.contains(":") -> {
                    val afterColon = trimmed.substringAfterLast(":")
                    if (afterColon.contains("/")) afterColon.substringAfterLast("/") else afterColon
                }
                trimmed.contains("/") -> trimmed.substringAfterLast("/")
                else -> trimmed
            }

            return candidate.substringBefore(".git")
        }

        private fun sanitizeRepoName(repoName: String): String {
            val trimmed = repoName.trim().removeSuffix(".git")
            if (trimmed.isEmpty()) throw IllegalArgumentException("Invalid repository name")
            if (trimmed == "." || trimmed == "..") throw IllegalArgumentException("Invalid repository name")
            if (trimmed.contains("..")) throw IllegalArgumentException("Invalid repository name")
            if (trimmed.contains("/") || trimmed.contains("\\")) throw IllegalArgumentException("Invalid repository name")

            val sanitized = trimmed
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
                .take(100)

            if (sanitized.isEmpty()) throw IllegalArgumentException("Invalid repository name")
            if (sanitized.startsWith("-")) throw IllegalArgumentException("Invalid repository name")
            return sanitized
        }
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
        val invalidReason = validateGitRemoteUrl(url)
        if (invalidReason != null) {
            return@withContext GitCloneResult(
                success = false,
                exitCode = -1,
                errorMessage = "Unsafe git url: ${sanitizeGitUrlForDisplay(url)} ($invalidReason)"
            )
        }
        if (!isSafeRepoName(repoName)) {
            return@withContext GitCloneResult(
                success = false,
                exitCode = -1,
                errorMessage = "Invalid repository name: $repoName"
            )
        }

        val processBuilder = ProcessBuilder(buildCloneCommand(url, repoName))
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

        val lastErrorLine = AtomicReference<String?>(null)

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
                                    extractGitErrorLine(outputLine)?.let { lastErrorLine.set(it) }
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
                    GitCloneResult(false, exitCode)
                } else {
                    if (exitCode == 0) {
                        GitCloneResult(true, exitCode)
                    } else {
                        GitCloneResult(
                            success = false,
                            exitCode = exitCode,
                            errorMessage = lastErrorLine.get() ?: "Git clone failed with exit code $exitCode"
                        )
                    }
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
