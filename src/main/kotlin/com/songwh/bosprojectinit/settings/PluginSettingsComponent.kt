package com.songwh.bosprojectinit.settings

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.songwh.bosprojectinit.MessageBundle
import javax.swing.JComboBox
import javax.swing.JPanel

/**
 * 插件设置界面组件
 */
class PluginSettingsComponent {
    
    private val mainPanel: JPanel
    private val languageComboBox: JComboBox<LanguageOption>
    
    init {
        // 创建语言选择下拉框
        languageComboBox = JComboBox(
            arrayOf(
                LanguageOption(MessageBundle.message("settings.language.zh_cn"), "zh_CN"),
                LanguageOption(MessageBundle.message("settings.language.en_us"), "en_US")
            )
        )
        
        // 构建表单
        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(
                JBLabel(MessageBundle.message("settings.language.label") + " / Language:"), 
                languageComboBox, 
                1, 
                false
            )
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }
    
    fun getPanel(): JPanel {
        return mainPanel
    }
    
    fun getLanguage(): String {
        return (languageComboBox.selectedItem as LanguageOption).code
    }
    
    fun setLanguage(language: String) {
        for (i in 0 until languageComboBox.itemCount) {
            val item = languageComboBox.getItemAt(i)
            if (item.code == language) {
                languageComboBox.selectedIndex = i
                break
            }
        }
    }
    
    /**
     * 语言选项数据类
     */
    private data class LanguageOption(
        val displayName: String,
        val code: String
    ) {
        override fun toString(): String = displayName
    }
}
