package com.songwh.bosprojectinit.logic.steps.impl

import com.songwh.bosprojectinit.model.ModuleInfo
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertTrue

class SettingsStepTest {

    private val step = SettingsStep(AtomicBoolean(false), mutableMapOf()) { }

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
