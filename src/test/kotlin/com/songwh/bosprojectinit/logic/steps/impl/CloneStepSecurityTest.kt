package com.songwh.bosprojectinit.logic.steps.impl

import com.songwh.bosprojectinit.model.StepExecutionContext
import com.songwh.bosprojectinit.utils.GitUtils
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals

class CloneStepSecurityTest {

    private val isCancelled = AtomicBoolean(false)
    private val logEntries = mutableMapOf<String, com.songwh.bosprojectinit.model.LogEntry>()
    
    // 手动模拟一个恶意的 GitUtils
    private class MaliciousGitUtils(isCancelled: AtomicBoolean) : GitUtils(isCancelled) {
        override fun extractRepoName(url: String): String {
            return "../../malicious"
        }
    }

    @Test
    fun testProcessSingleRepositoryPathTraversal() = runBlocking {
        val tempDir = Files.createTempDirectory("clone_security_test").toFile()
        val projectsDir = File(tempDir, "projects").apply { mkdirs() }
        
        try {
            val context = StepExecutionContext(
                rootPath = tempDir.absolutePath,
                urls = listOf("https://github.com/user/../../malicious.git"),
                timeoutSeconds = 60,
                cleanProjectsBeforeClone = false,
                onProgress = { _, _ -> },
                onLogUpdate = {},
                onStatsUpdate = { _, _ -> }
            )

            val maliciousCloneStep = CloneStep(
                gitUtils = MaliciousGitUtils(isCancelled),
                isCancelled = isCancelled,
                logEntries = logEntries,
                onLogUpdate = {},
                onStatsUpdate = { _, _ -> }
            )

            val result = maliciousCloneStep.processSingleRepository(
                context = context,
                url = "https://github.com/user/../../malicious.git",
                projectsDir = projectsDir,
                index = 0,
                totalRepos = 1,
                stepIndex = 0,
                totalSteps = 1
            )

            assertEquals(RepositoryResult.FAILURE, result, "Should fail due to path traversal detection")
            
            // 验证没有在 projects 目录之外创建文件
            val maliciousFile = File(tempDir, "malicious")
            assert(!maliciousFile.exists()) { "Malicious file should not be created outside projects directory" }

        } finally {
            tempDir.deleteRecursively()
        }
    }
}
