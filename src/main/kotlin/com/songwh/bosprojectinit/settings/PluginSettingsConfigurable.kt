package com.songwh.bosprojectinit.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.NlsContexts
import com.songwh.bosprojectinit.MessageBundle
import javax.swing.JComponent

/**
 * 插件设置配置界面
 */
class PluginSettingsConfigurable : Configurable {
    
    private var settingsComponent: PluginSettingsComponent? = null
    
    @NlsContexts.ConfigurableName
    override fun getDisplayName(): String {
        return "BOS Project Initialization"
    }
    
    override fun createComponent(): JComponent {
        settingsComponent = PluginSettingsComponent()
        return settingsComponent!!.getPanel()
    }
    
    override fun isModified(): Boolean {
        val settings = PluginSettings.getInstance()
        return settingsComponent?.getLanguage() != settings.language
    }
    
    override fun apply() {
        val settings = PluginSettings.getInstance()
        val oldLanguage = settings.language
        
        settingsComponent?.let {
            val newLanguage = it.getLanguage()
            settings.language = newLanguage
            
            // 如果语言变化了，重新加载资源束
            if (oldLanguage != newLanguage) {
                MessageBundle.reload()
                // 触发应用级别的事件，通知所有组件语言已变化
                ApplicationManager.getApplication().messageBus
                    .syncPublisher(LanguageChangeListener.TOPIC)
                    .languageChanged(newLanguage)
            }
        }
    }
    
    override fun reset() {
        val settings = PluginSettings.getInstance()
        settingsComponent?.setLanguage(settings.language)
    }
    
    override fun disposeUIResources() {
        settingsComponent = null
    }
}
