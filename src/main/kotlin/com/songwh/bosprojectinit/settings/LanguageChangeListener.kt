package com.songwh.bosprojectinit.settings

import com.intellij.util.messages.Topic

/**
 * 语言变化监听器
 * 当用户在设置中切换语言时，会触发此监听器
 */
interface LanguageChangeListener {
    
    /**
     * 语言已变化
     * @param newLanguage 新的语言代码（如 "zh_CN" 或 "en_US"）
     */
    fun languageChanged(newLanguage: String)
    
    companion object {
        /**
         * 消息总线主题
         */
        @JvmField
        val TOPIC = Topic.create("Language Change", LanguageChangeListener::class.java)
    }
}
