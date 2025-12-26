package com.songwh.bosprojectinit.utils

import com.songwh.bosprojectinit.settings.PluginSettings
import java.util.*

/**
 * 语言工具类
 * 用于获取和管理插件的语言设置
 */
object LanguageUtils {
    
    /**
     * 获取当前设置的语言
     * @return 语言代码，如 "zh_CN" 或 "en_US"
     */
    fun getCurrentLanguage(): String {
        return PluginSettings.getInstance().language
    }
    
    /**
     * 获取当前语言的Locale对象
     */
    fun getCurrentLocale(): Locale {
        val language = getCurrentLanguage()
        return when (language) {
            "zh_CN" -> Locale.SIMPLIFIED_CHINESE
            "en_US" -> Locale.US
            else -> Locale.getDefault()
        }
    }
    
    /**
     * 判断当前是否为中文环境
     */
    fun isChineseLanguage(): Boolean {
        return getCurrentLanguage() == "zh_CN"
    }
    
    /**
     * 判断当前是否为英文环境
     */
    fun isEnglishLanguage(): Boolean {
        return getCurrentLanguage() == "en_US"
    }
}
