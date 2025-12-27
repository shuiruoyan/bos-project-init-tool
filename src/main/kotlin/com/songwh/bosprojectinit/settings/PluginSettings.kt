package com.songwh.bosprojectinit.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * 插件设置状态类
 */
@State(
    name = "com.songwh.bosprojectinit.settings.PluginSettings",
    storages = [Storage("BosProjectInitSettings.xml")]
)
class PluginSettings : PersistentStateComponent<PluginSettings> {
    
    /**
     * 语言设置，默认为系统语言
     * zh_CN: 简体中文
     * en_US: English
     */
    var language: String = "zh_CN"
    
    override fun getState(): PluginSettings {
        return this
    }
    
    override fun loadState(state: PluginSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
    
    companion object {
        /**
         * 获取插件设置实例
         */
        fun getInstance(): PluginSettings {
            return ApplicationManager.getApplication().getService(PluginSettings::class.java)
        }
    }
}
