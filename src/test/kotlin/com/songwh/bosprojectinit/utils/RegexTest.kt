package com.songwh.bosprojectinit.utils

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class RegexTest {
    @Test
    fun testRegex() {
        val httpsRegex = Regex("^https?://[a-zA-Z0-9][a-zA-Z0-9._-]*[a-zA-Z0-9.-]*[a-zA-Z0-9._-]*/[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+(\\.git)?$")
        val gitRegex = Regex("^git@[a-zA-Z0-9][a-zA-Z0-9._-]*[a-zA-Z0-9.-]+:[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+(\\.git)?$")
        
        println("Testing https://github.com/user/repo.git with httpsRegex: ${httpsRegex.matches("https://github.com/user/repo.git")}")
        println("Testing http://github.com/user/repo.git with httpsRegex: ${httpsRegex.matches("http://github.com/user/repo.git")}")
        println("Testing git@github.com:user/repo.git with gitRegex: ${gitRegex.matches("git@github.com:user/repo.git")}")
        
        // 测试正常的 URL
        assertTrue(httpsRegex.matches("https://github.com/user/repo.git"))
        assertTrue(httpsRegex.matches("http://github.com/user/repo.git"))
        assertTrue(gitRegex.matches("git@github.com:user/repo.git"))
        
        // 测试路径遍历攻击
        assertFalse(httpsRegex.matches("https://github.com/user/../../../etc/passwd"))
        assertFalse(gitRegex.matches("git@github.com:user/../../../secret.git"))
    }
}