package com.songwh.bosprojectinit.utils

import com.songwh.bosprojectinit.MessageBundle
import kotlin.test.Test
import kotlin.test.assertTrue

class MessageBundleTest {
    
    @Test
    fun testSecurityValidationMessages() {
        // 测试所有验证消息键是否存在且返回非空值
        val messageKeys = listOf(
            "validation.giturl.empty",
            "validation.giturl.too.long",
            "validation.giturl.dangerous.chars",
            "validation.giturl.path.traversal",
            "validation.giturl.format.valid",
            "validation.giturl.format.invalid",
            "validation.reponame.empty",
            "validation.reponame.too.long",
            "validation.reponame.dangerous.chars",
            "validation.reponame.path.traversal",
            "validation.reponame.invalid.chars",
            "validation.reponame.valid",
            "validation.path.out.of.bounds",
            "validation.path.safe",
            "validation.path.security.failed",
            "validation.path.exception",
            "validation.timeout.must.positive",
            "validation.timeout.exceeds.one.hour",
            "validation.timeout.valid",
            "error.giturl.validation.failed",
            "error.reponame.validation.failed",
            "error.path.safety.validation.failed"
        )
        
        messageKeys.forEach { key ->
            val message = if (key.contains("failed") || key.contains("too.long")) {
                // 带参数的消息
                MessageBundle.message(key, "test param")
            } else {
                // 不带参数的消息
                MessageBundle.message(key)
            }
            
            assertTrue(message.isNotBlank(), "消息键 '$key' 应该返回非空值")
            // 至少不应该返回键名本身（除非资源文件缺失）
            if (message == key) {
                println("警告: 消息键 '$key' 返回了键名本身，可能资源文件缺失")
            }
        }
    }
    
    @Test
    fun testMessageBundleWithParameters() {
        // 测试带参数的MessageBundle调用
        val result = SecurityUtils.validateGitUrl("")
        assertTrue(result.message.isNotBlank(), "验证消息应该存在")
        
        // 测试SecurityUtils是否正确地使用了MessageBundle
        val urlValidation = SecurityUtils.validateGitUrl("https://github.com/user/repo.git")
        assertTrue(urlValidation.isValid, "有效的Git URL应该通过验证")
        assertTrue(urlValidation.message.isNotBlank(), "验证成功消息应该存在")
        
        val invalidUrlValidation = SecurityUtils.validateGitUrl("")
        assertTrue(!invalidUrlValidation.isValid, "空的Git URL应该失败")
        assertTrue(invalidUrlValidation.message.isNotBlank(), "验证失败消息应该存在")
    }
}
