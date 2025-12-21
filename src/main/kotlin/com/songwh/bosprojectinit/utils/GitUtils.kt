package com.songwh.bosprojectinit.utils

import com.intellij.util.io.awaitExit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class GitCloneResult(val success: Boolean, val exitCode: Int)

/**
 * 封装 Git 命令行操作的服务类
 * 支持进度解析、超时控制、任务取消以及彻底的进程树清理
 */
class GitUtils(private val isCancelled: AtomicBoolean) {

    // 记录当前正在运行的 Git 进程引用，以便随时强制终止
    private var currentProcess: Process? = null

    /**
     * 外部请求取消操作，标记状态并停止当前进程
     */
    fun cancel() {
        isCancelled.set(true)
        stopCurrentProcess()
    }

    /**
     * 彻底停止当前运行的 Git 及其派生的所有子进程
     * 特别是针对 Windows 下 SSH 进程可能残留的问题
     */
    fun stopCurrentProcess() {
        val process = currentProcess ?: return
        if (process.isAlive) {
            runCatching {
                // 销毁子进程（如 ssh.exe），防止其在父进程退出后继续运行导致文件占用
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
            }
        }
    }

    /**
     * 从 Git 远程地址中提取仓库名称
     * 例如: https://github.com/user/my-repo.git -> my-repo
     */
    fun extractRepoName(url: String): String {
        return url.substringAfterLast("/").substringBefore(".git")
    }

    /**
     * 执行 Git Clone 操作，并实时解析进度
     * 
     * @param url Git 仓库地址
     * @param repoName 本地文件夹名称
     * @param rootFile 克隆到的父目录
     * @param onProgress 进度回调 (阶段, 百分比)
     */
    suspend fun cloneRepository(
        url: String,
        repoName: String,
        rootFile: File,
        onProgress: suspend (phase: String, percent: Int) -> Unit
    ): GitCloneResult = withContext(Dispatchers.IO) {
        val processBuilder = ProcessBuilder(
            "git", "clone",
            "--depth", "1",         // 浅克隆，减少下载量
            "--progress",          // 强制输出进度信息，即使是非交互模式
            "--single-branch",     // 只拉取当前分支
            url,
            repoName
        )
        processBuilder.directory(rootFile)
        processBuilder.redirectErrorStream(true) // 合并标准输出和标准错误流
        
        // 关键设置：禁用 SSH 连接共享 (ControlMaster)
        // 确保 SSH 连接进程在 Git 退出时能被正确清理，避免 Windows 下的文件句柄占用
        processBuilder.environment()["GIT_SSH_COMMAND"] = "ssh -o ControlMaster=no"

        val process = processBuilder.start()
        currentProcess = process

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
                                    if (progressInfo != null) {
                                        val (phase, percent) = progressInfo
                                        onProgress(phase, percent)
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
                    GitCloneResult(exitCode == 0, exitCode)
                }
            }
        } finally {
            // 无论何种退出情况，确保清理进程资源
            stopCurrentProcess()
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
            currentProcess = null
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