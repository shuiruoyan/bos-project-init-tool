package com.songwh.bosprojectinit.utils

import com.songwh.bosprojectinit.MessageBundle
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Pattern

/**
 * 安全工具类，提供输入验证和消毒功能
 */
object SecurityUtils {
    
    /**
     * 最大URL长度限制
     */
    private const val MAX_URL_LENGTH = 2048
    
    /**
     * 最大仓库名称长度限制
     */
    private const val MAX_REPO_NAME_LENGTH = 255
    
    /**
     * 允许的Git协议白名单
     */
    private val ALLOWED_PROTOCOLS = setOf("http", "https", "git", "ssh", "file")
    
    /**
     * Git URL正则表达式模式
     * 匹配常见的Git URL格式：
     * - HTTPS/HTTP: https://github.com/user/repo.git
     * - SSH: git@github.com:user/repo.git
     * - Git: git://github.com/user/repo.git
     * - SCP风格: user@host:path/to/repo.git
     */
    private val GIT_URL_PATTERN = Pattern.compile(
        "^(?:" +
        // HTTP/HTTPS协议
        "(?:https?://[\\w\\-\\.]+(?:\\:\\d+)?/[\\w\\-\\./~]+(?:\\.git)?)" +
        "|" +
        // Git协议
        "(?:git://[\\w\\-\\.]+(?:\\:\\d+)?/[\\w\\-\\./~]+(?:\\.git)?)" +
        "|" +
        // SSH协议 (git@host:path)
        "(?:git@[\\w\\-\\.]+:[\\w\\-\\./~]+(?:\\.git)?)" +
        "|" +
        // SSH协议 (ssh://)
        "(?:ssh://(?:[\\w\\-\\.]+@)?[\\w\\-\\.]+(?:\\:\\d+)?/[\\w\\-\\./~]+(?:\\.git)?)" +
        "|" +
        // 文件协议 (本地仓库)
        "(?:file:///[\\w\\-\\./~]+(?:\\.git)?)" +
        ")\$"
    )
    
    /**
     * 危险字符模式，用于防止命令注入
     */
    private val DANGEROUS_CHARS_PATTERN = Pattern.compile("[;&|`\$\\n\\r\\t]")
    
    /**
     * 路径遍历模式
     */
    private val PATH_TRAVERSAL_PATTERN = Pattern.compile("\\.\\.(/|\\\\)")
    
    /**
     * 验证Git URL是否安全
     * 
     * @param url 要验证的Git URL
     * @return 验证结果，包含是否有效和错误信息
     */
    fun validateGitUrl(url: String): ValidationResult {
        // 1. 检查空值
        if (url.isBlank()) {
            return ValidationResult(false, MessageBundle.message("validation.giturl.empty"))
        }
        
        // 2. 检查长度限制
        if (url.length > MAX_URL_LENGTH) {
            return ValidationResult(false, MessageBundle.message("validation.giturl.too.long", MAX_URL_LENGTH))
        }
        
        // 3. 检查危险字符（防止命令注入）
        if (containsDangerousCharacters(url)) {
            return ValidationResult(false, MessageBundle.message("validation.giturl.dangerous.chars"))
        }
        
        // 4. 检查路径遍历
        if (containsPathTraversal(url)) {
            return ValidationResult(false, MessageBundle.message("validation.giturl.path.traversal"))
        }
        
        // 5. 验证URL格式
        return if (isValidGitUrlFormat(url)) {
            ValidationResult(true, MessageBundle.message("validation.giturl.format.valid"))
        } else {
            ValidationResult(false, MessageBundle.message("validation.giturl.format.invalid"))
        }
    }
    
    /**
     * 验证仓库名称是否安全
     */
    fun validateRepoName(repoName: String): ValidationResult {
        // 1. 检查空值
        if (repoName.isBlank()) {
            return ValidationResult(false, MessageBundle.message("validation.reponame.empty"))
        }
        
        // 2. 检查长度限制
        if (repoName.length > MAX_REPO_NAME_LENGTH) {
            return ValidationResult(false, MessageBundle.message("validation.reponame.too.long", MAX_REPO_NAME_LENGTH))
        }
        
        // 3. 检查危险字符
        if (containsDangerousCharacters(repoName)) {
            return ValidationResult(false, MessageBundle.message("validation.reponame.dangerous.chars"))
        }
        
        // 4. 检查路径遍历
        if (containsPathTraversal(repoName)) {
            return ValidationResult(false, MessageBundle.message("validation.reponame.path.traversal"))
        }
        
        // 5. 检查是否为有效的目录名
        if (!isValidDirectoryName(repoName)) {
            return ValidationResult(false, MessageBundle.message("validation.reponame.invalid.chars"))
        }
        
        return ValidationResult(true, MessageBundle.message("validation.reponame.valid"))
    }
    
    /**
     * 消毒Git URL，移除危险字符
     */
    fun sanitizeGitUrl(url: String): String {
        return DANGEROUS_CHARS_PATTERN.matcher(url).replaceAll("")
    }
    
    /**
     * 消毒仓库名称，移除危险字符并确保是有效的目录名
     */
    fun sanitizeRepoName(repoName: String): String {
        // 先替换危险字符为空格（除了管道字符，它应该被替换为下划线）
        var sanitized = repoName.replace("|", "_")
        // 将危险字符替换为空格
        sanitized = DANGEROUS_CHARS_PATTERN.matcher(sanitized).replaceAll(" ")
        sanitized = PATH_TRAVERSAL_PATTERN.matcher(sanitized).replaceAll("")
        sanitized = sanitized.replace(Regex("[<>:\"?*]"), "_")
        // 合并多个空格为单个空格
        sanitized = sanitized.replace(Regex("\\s+"), " ")
        return sanitized.trim()
    }
    
    /**
     * 验证路径是否安全，防止路径遍历攻击
     */
    fun validatePathSafety(basePath: String, userPath: String): ValidationResult {
        try {
            val normalizedBase = File(basePath).canonicalPath
            val userFile = File(userPath)
            
            // 如果用户路径是绝对路径，检查它是否在基础路径内
            val normalizedUser = if (userFile.isAbsolute) {
                userFile.canonicalPath
            } else {
                File(basePath, userPath).canonicalPath
            }
            
            // 检查用户路径是否在基础路径内
            // 需要确保 normalizedUser 以 normalizedBase + 文件分隔符开头
            // 这样可以防止类似 "security-test" 和 "security-test-outside" 的误判
            if (!normalizedUser.startsWith(normalizedBase + File.separator) && normalizedUser != normalizedBase) {
                return ValidationResult(false, MessageBundle.message("validation.path.out.of.bounds"))
            }
            
            return ValidationResult(true, MessageBundle.message("validation.path.safe"))
        } catch (e: SecurityException) {
            return ValidationResult(false, MessageBundle.message("validation.path.security.failed", e.message ?: ""))
        } catch (e: Exception) {
            return ValidationResult(false, MessageBundle.message("validation.path.exception", e.message ?: ""))
        }
    }
    
    /**
     * 验证超时时间是否在合理范围内
     */
    fun validateTimeout(timeoutSeconds: Long): ValidationResult {
        return when {
            timeoutSeconds <= 0 -> ValidationResult(false, MessageBundle.message("validation.timeout.must.positive"))
            timeoutSeconds > 3600 -> ValidationResult(false, MessageBundle.message("validation.timeout.exceeds.one.hour"))
            else -> ValidationResult(true, MessageBundle.message("validation.timeout.valid"))
        }
    }
    
    /**
     * 检查字符串是否包含危险字符
     */
    private fun containsDangerousCharacters(input: String): Boolean {
        return DANGEROUS_CHARS_PATTERN.matcher(input).find()
    }
    
    /**
     * 检查字符串是否包含路径遍历序列
     */
    private fun containsPathTraversal(input: String): Boolean {
        return PATH_TRAVERSAL_PATTERN.matcher(input).find()
    }
    
    /**
     * 验证Git URL格式
     */
    private fun isValidGitUrlFormat(url: String): Boolean {
        // 使用正则表达式匹配
        if (GIT_URL_PATTERN.matcher(url).matches()) {
            return true
        }
        
        // 尝试解析URI进行额外验证
        return try {
            val uri = URI(url)
            val scheme = uri.scheme
            
            // 检查协议是否在白名单中
            if (scheme != null && !ALLOWED_PROTOCOLS.contains(scheme.lowercase())) {
                return false
            }
            
            // 对于SSH格式 (git@host:path)
            if (scheme == null && url.contains('@') && url.contains(':')) {
                return true
            }
            
            scheme != null && ALLOWED_PROTOCOLS.contains(scheme.lowercase())
        } catch (e: URISyntaxException) {
            // 对于非标准URI格式（如git@host:path），返回true（因为正则已经匹配）
            url.contains('@') && url.contains(':')
        }
    }
    
    /**
     * 检查是否为有效的目录名
     */
    private fun isValidDirectoryName(name: String): Boolean {
        // Windows和Linux都禁止的字符
        val invalidChars = Regex("[<>:\"|?*\\n\\r\\t]")
        return !invalidChars.containsMatchIn(name) && name != "." && name != ".."
    }
    
    /**
     * 验证结果数据类
     */
    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )
}
