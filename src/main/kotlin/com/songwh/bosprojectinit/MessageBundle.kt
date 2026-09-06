package com.songwh.bosprojectinit

import com.songwh.bosprojectinit.settings.PluginSettings
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.text.MessageFormat
import java.util.*
import java.util.function.Supplier

private const val BUNDLE = "messages.MessageBundle"

/**
 * 国际化资源管理对象
 * 基于标准 ResourceBundle 加载和获取多语言文案（通过 Utf8Control 以 UTF-8 读取资源文件）。
 * 资源文件位于 resources/messages/MessageBundle.properties (及对应的 _zh_CN, _en_US 版本)
 */
internal object MessageBundle {
    // 资源束名称前缀
    @Volatile
    private var currentLocale: Locale? = null
    
    @Volatile
    private var bundle: ResourceBundle? = null

    /**
     * 获取当前语言的 Locale
     */
    private fun getCurrentLocale(): Locale {
        val settings = try {
            PluginSettings.getInstance()
        } catch (e: Exception) {
            return Locale.getDefault()
        }
        
        return when (settings.language) {
            "zh_CN" -> Locale.SIMPLIFIED_CHINESE
            "en_US" -> Locale.US
            else -> Locale.getDefault()
        }
    }
    
    /**
     * 获取资源束，如果语言变化则重新加载
     */
    private fun getBundle(): ResourceBundle {
        val locale = getCurrentLocale()
        
        // 如果语言变化了，重新加载资源束
        if (bundle == null || currentLocale != locale) {
            currentLocale = locale
            bundle = ResourceBundle.getBundle(BUNDLE, locale, MessageBundle::class.java.classLoader, Utf8Control)
        }
        
        return bundle!!
    }

    /**
     * 以 UTF-8 编码读取 properties 资源文件。
     * 标准 ResourceBundle 默认按 ISO-8859-1 解码，无法读取直接存储的 UTF-8 中文；
     * 这里通过自定义 Control 改用 UTF-8 读取，使资源文件可以直接书写中文字符。
     */
    private object Utf8Control : ResourceBundle.Control() {
        override fun newBundle(
            baseName: String,
            locale: Locale,
            format: String,
            loader: ClassLoader,
            reload: Boolean
        ): ResourceBundle? {
            if (format != "java.properties") {
                return super.newBundle(baseName, locale, format, loader, reload)
            }
            val bundleName = toBundleName(baseName, locale)
            val resourceName = toResourceName(bundleName, "properties")
            val stream = loader.getResourceAsStream(resourceName) ?: return null
            return try {
                PropertyResourceBundle(InputStreamReader(stream, StandardCharsets.UTF_8))
            } finally {
                stream.close()
            }
        }
    }

    /**
     * 获取指定 key 对应的国际化文案
     * @param key 资源文件中的键
     * @param params 动态替换参数
     */
    @JvmStatic
    fun message(key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any?): @Nls String {
        return try {
            val bundle = getBundle()
            val message = bundle.getString(key)
            if (params.isEmpty()) {
                message
            } else {
                MessageFormat.format(message, *params)
            }
        } catch (e: Exception) {
            key
        }
    }

    /**
     * 延迟获取文案，常用于声明式 UI 或常量定义中
     */
    @JvmStatic
    fun lazyMessage(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any?): Supplier<@Nls String> {
        return Supplier { message(key, *params) }
    }
    
    /**
     * 强制重新加载资源束（用于语言切换后）
     */
    @JvmStatic
    fun reload() {
        bundle = null
        currentLocale = null
    }
}
