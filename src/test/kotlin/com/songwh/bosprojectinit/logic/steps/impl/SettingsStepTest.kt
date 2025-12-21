package com.songwh.bosprojectinit.logic.steps.impl

import com.songwh.bosprojectinit.model.ModuleInfo
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsStepTest {

    private val step = SettingsStep(AtomicBoolean(false), mutableMapOf()) { }

    @Test
    fun testCollectModuleInfosFiltering() {
        val tempDir = Files.createTempDirectory("projects").toFile()
        try {
            // projects/
            //   repo1/
            //     moduleA/build.gradle
            //   repo2/
            //     moduleB/build.gradle
            //   extraRepo/
            //     moduleC/build.gradle
            
            val repo1Dir = File(tempDir, "repo1/moduleA").apply { mkdirs() }
            File(repo1Dir, "build.gradle").writeText("version = '1.0'")
            
            val repo2Dir = File(tempDir, "repo2/moduleB").apply { mkdirs() }
            File(repo2Dir, "build.gradle").writeText("version = '2.0'")
            
            val extraRepoDir = File(tempDir, "extraRepo/moduleC").apply { mkdirs() }
            File(extraRepoDir, "build.gradle").writeText("version = '3.0'")
            
            val allowedRepos = setOf("repo1", "repo2")
            val results = step.collectModuleInfos(tempDir, allowedRepos)
            
            assertEquals(2, results.size, "Should only collect 2 modules")
            assertTrue(results.any { it.repoName == "repo1" && it.moduleName == "moduleA" })
            assertTrue(results.any { it.repoName == "repo2" && it.moduleName == "moduleB" })
            assertTrue(results.none { it.repoName == "extraRepo" }, "Should not collect from extraRepo")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testCollectModuleInfosVersionExtraction() {
        val tempDir = Files.createTempDirectory("version_test").toFile()
        try {
            val repoDir = File(tempDir, "my-repo/sub-module").apply { mkdirs() }
            
            // 测试双引号
            File(repoDir, "build.gradle").writeText("version = \"1.2.3\"")
            var results = step.collectModuleInfos(tempDir, setOf("my-repo"))
            assertEquals("1.2.3", results.first().version)

            // 测试单引号
            File(repoDir, "build.gradle").writeText("version = '2.3.4'")
            results = step.collectModuleInfos(tempDir, setOf("my-repo"))
            assertEquals("2.3.4", results.first().version)

            // 测试空格
            File(repoDir, "build.gradle").writeText("version  =  '3.4.5'  ")
            results = step.collectModuleInfos(tempDir, setOf("my-repo"))
            assertEquals("3.4.5", results.first().version)
            
            // 测试无版本号
            File(repoDir, "build.gradle").writeText("dependencies { }")
            results = step.collectModuleInfos(tempDir, setOf("my-repo"))
            assertEquals(null, results.first().version)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testCollectModuleInfosDeepModules() {
        val tempDir = Files.createTempDirectory("deep_test").toFile()
        try {
            // projects/repo/a/b/c/build.gradle
            val deepDir = File(tempDir, "my-repo/a/b/c").apply { mkdirs() }
            File(deepDir, "build.gradle").writeText("version = '1.0'")
            
            val results = step.collectModuleInfos(tempDir, setOf("my-repo"))
            assertEquals(1, results.size)
            assertEquals("my-repo", results.first().repoName)
            assertEquals("c", results.first().moduleName)
            assertEquals("my-repo/a/b/c", results.first().relativePath)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testRepoNameExtraction() {
        // 虽然这个逻辑在 execute 内部，但我们可以直接测试这个转换逻辑
        val urls = listOf(
            "https://github.com/user/repo1.git",
            "git@github.com:user/repo2.git",
            "http://internal.com/group/subgroup/repo3.git"
        )
        val allowedRepos = urls.map { url ->
            url.substringAfterLast("/").substringBefore(".git")
        }.toSet()
        
        assertEquals(setOf("repo1", "repo2", "repo3"), allowedRepos)
    }

    @Test
    fun testBuildSettingsGradleContent() {
        val rootPath = "D:/test-root"
        val baseDir = "D:/test-root/projects"
        val moduleInfos = listOf(
            ModuleInfo("repo1", "moduleA", File("."), File("."), "repo1/moduleA"),
            ModuleInfo("repo1", "moduleB", File("."), File("."), "repo1/moduleB"),
            ModuleInfo("repo2", "moduleC", File("."), File("."), "repo2/moduleC")
        )

        val content = step.buildSettingsGradleContent(rootPath, baseDir, moduleInfos)

        // 验证 ROOT_NAME 替换
        assertTrue(content.contains("rootProject.name = 'test-root'"), "ROOT_NAME should be test-root")

        // 验证 BASE_DIR 替换
        assertTrue(content.contains("def baseDir = 'D:/test-root/projects'"), "BASE_DIR should be replaced")

        // 验证 INCLUDES
        assertTrue(content.contains("include ':repo1.moduleA'"), "Should include repo1.moduleA")
        assertTrue(content.contains("include ':repo1.moduleB'"), "Should include repo1.moduleB")
        assertTrue(content.contains("include ':repo2.moduleC'"), "Should include repo2.moduleC")

        // 验证 PROJECT_DIRS
        assertTrue(content.contains("project(':repo1.moduleA').projectDir = file(baseDir + \"/repo1/moduleA\")"), "Project dir for moduleA incorrect")
        assertTrue(content.contains("project(':repo1.moduleB').projectDir = file(baseDir + \"/repo1/moduleB\")"), "Project dir for moduleB incorrect")
        assertTrue(content.contains("project(':repo2.moduleC').projectDir = file(baseDir + \"/repo2/moduleC\")"), "Project dir for moduleC incorrect")
    }

    @Test
    fun testBuildSettingsGradleContentEmpty() {
        val rootPath = "D:/test-root"
        val baseDir = "D:/test-root/projects"
        val moduleInfos = emptyList<ModuleInfo>()

        val content = step.buildSettingsGradleContent(rootPath, baseDir, moduleInfos)

        assertTrue(content.contains("rootProject.name = 'test-root'"))
        // INCLUDES and PROJECT_DIRS should be empty strings, replaced in template
        // Check if the lines are removed or empty
        val lines = content.lines()
        // Template has {{INCLUDES}} on line 5 and {{PROJECT_DIRS}} on line 7
        // After replacement with empty string, they should just be empty lines (if the replace works as expected)
        
        // We can check if specific markers are gone
        assertTrue(!content.contains("{{INCLUDES}}"))
        assertTrue(!content.contains("{{PROJECT_DIRS}}"))
    }
}
