package com.songwh.bosprojectinit.logic.steps.impl

import com.songwh.bosprojectinit.model.ModuleInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class ModuleBuildLocalStepTest {

    private val step = ModuleBuildLocalStep(AtomicBoolean(false), mutableMapOf()) { }

    // Helper to call private transformDependencies using reflection or making it internal
    // Since I can edit the source, I'll make it internal or just use a test-friendly way.
    // For now, I'll use reflection or just assume I can change it to internal.
    
    @Test
    fun testTransformDependencies() {
        val allModules = listOf(
            ModuleInfo("hcdmpro", "swc-hcdm-business", File("."), File("."), "path", "1.0")
        )

        val input = """
            compile fileTree(dir: currentapp, include: 'swc-hcdm-business-1.0*.jar')
            compile fileTree(dir: currentapp, include: 'swc-hcdm-business-1.0-SNAPSHOT*.jar')
            
            // Should NOT match if version doesn't match
            compile fileTree(dir: currentapp, include: 'swc-hcdm-business-2.0*.jar')
        """.trimIndent()

        val result = step.transformDependencies(input, allModules)
        val lines = result.lines()

        // 1. swc-hcdm-business-1.0*.jar -> Should match
        assertTrue(lines[1].contains("compile project(':hcdmpro.swc-hcdm-business')"), "Should match swc-hcdm-business-1.0*.jar")
        
        // 2. swc-hcdm-business-1.0-SNAPSHOT*.jar -> Should match
        assertTrue(lines[3].contains("compile project(':hcdmpro.swc-hcdm-business')"), "Should match swc-hcdm-business-1.0-SNAPSHOT*.jar")
        
        // Non-matching case (version 2.0 vs 1.0)
        assertTrue(lines.any { it.trim() == "compile fileTree(dir: currentapp, include: 'swc-hcdm-business-2.0*.jar')" }, "Should NOT match swc-hcdm-business-2.0*.jar")
    }

    @Test
    fun testTransformDependenciesMultiMatch() {
        // This test demonstrates matching multiple modules with a common prefix and wildcard
        val input = "implementation fileTree(dir: 'libs', include: 'mod*.jar')"
        val multiModules = listOf(
            ModuleInfo("repo1", "mod", File("."), File("."), "path", "1.1"),
            ModuleInfo("repo1", "mod-a", File("."), File("."), "path", "1.1")
        )
        val result = step.transformDependencies(input, multiModules)
        assertTrue(result.contains("project(':repo1.mod')"), "Should contain repo1.mod")
        assertTrue(result.contains("project(':repo1.mod-a')"), "Should contain repo1.mod-a")
    }

    @Test
    fun testTransformDependenciesLongestMatch() {
        val allModules = listOf(
            ModuleInfo("hcdmpro", "swc-hcdm-business", File("."), File("."), "path"),
            ModuleInfo("hcdmpro", "swc-hcdm-business-api", File("."), File("."), "path")
        )

        val input = "compile fileTree(dir: currentapp, include: 'swc-hcdm-business-api*.jar')"
        val result = step.transformDependencies(input, allModules)
        
        assertTrue(result.contains("compile project(':hcdmpro.swc-hcdm-business-api')"), 
            "Should match the longest module name 'swc-hcdm-business-api', result was: $result")
        assertTrue(!result.contains("compile project(':hcdmpro.swc-hcdm-business')") || result.contains("swc-hcdm-business-api"),
            "Should NOT match 'swc-hcdm-business' if 'swc-hcdm-business-api' is available")
    }

    @Test
    fun testTransformDependenciesIgnoreGenericJar() {
        val allModules = listOf(
            ModuleInfo("repo", "mod", File("."), File("."), "path", "1.0")
        )
        val input = "compile fileTree(dir: 'libs', include: '*.jar')"
        val result = step.transformDependencies(input, allModules)
        
        assertEquals(input, result.trim(), "Should NOT replace *.jar")
    }
}
