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
            ModuleInfo("hcdmpro", "swc-hcdm-business", File("."), File("."), "path")
        )

        val input = """
            compile fileTree(dir: currentapp, include: 'swc-hcdm-business-1.0*.jar')
            compile fileTree(dir: currentapp, include: 'swc-hcdm-business-*.jar')
            compile fileTree(dir: currentapp, include: 'swc-hcdm-business-3.3333*.jar')
            compile fileTree(dir: currentapp, include: 'swc-hcdm-business3.5*.jar')
            compile fileTree(dir: currentapp, include: 'swc-hcdm-business*.jar')
            
            // Should NOT match
            compile fileTree(dir: bos, include: '*.jar')
            compile fileTree(dir: bos, include: 'swc-hcdm*.jar')
            compile fileTree(dir: bos, include: '123*.jar')
        """.trimIndent()

        val result = step.transformDependencies(input, allModules)
        val lines = result.lines()

        // 1. swc-hcdm-business-1.0*.jar -> Should match
        assertTrue(lines[1].contains("compile project(':hcdmpro.swc-hcdm-business')"), "Should match swc-hcdm-business-1.0*.jar")
        
        // 2. swc-hcdm-business-*.jar -> Should match
        assertTrue(lines[3].contains("compile project(':hcdmpro.swc-hcdm-business')"), "Should match swc-hcdm-business-*.jar")
        
        // 4. swc-hcdm-business-3.3333*.jar -> Should match
        assertTrue(lines[5].contains("compile project(':hcdmpro.swc-hcdm-business')"), "Should match swc-hcdm-business-3.3333*.jar")
        
        // 6. swc-hcdm-business3.5*.jar -> Should match
        assertTrue(lines[7].contains("compile project(':hcdmpro.swc-hcdm-business')"), "Should match swc-hcdm-business3.5*.jar")
        
        // 8. swc-hcdm-business*.jar -> Should match
        assertTrue(lines[9].contains("compile project(':hcdmpro.swc-hcdm-business')"), "Should match swc-hcdm-business*.jar")

        // Non-matching cases
        assertTrue(lines.any { it.trim() == "compile fileTree(dir: bos, include: '*.jar')" }, "Should NOT match *.jar")
        assertTrue(lines.any { it.trim() == "compile fileTree(dir: bos, include: 'swc-hcdm*.jar')" }, "Should NOT match swc-hcdm*.jar (partial module name)")
        assertTrue(lines.any { it.trim() == "compile fileTree(dir: bos, include: '123*.jar')" }, "Should NOT match 123*.jar")
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
}
