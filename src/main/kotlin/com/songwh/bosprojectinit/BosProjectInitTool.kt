package com.songwh.bosprojectinit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.openapi.project.Project
import com.songwh.bosprojectinit.ui.Typography
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import kotlin.random.Random

/**
 * 苍穹工程初始化助手的侧边栏工具窗口工厂
 * 注册在 plugin.xml 中，用于在 IDE 侧边栏展示简单的快捷功能（目前主要作为示例或占位）。
 */
class BosProjectInitToolFactory : ToolWindowFactory {
    // 设置工具窗口在何种情况下可用，这里默认为始终可用
    override fun shouldBeAvailable(project: Project) = true

    /**
     * 创建工具窗口的内容区域
     * 使用 Jewel 提供的 addComposeTab 方法将 Compose 内容嵌入到 ToolWindow 中。
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.addComposeTab(MessageBundle.message("toolwindow.stripe.BosProjectInitTool"), focusOnClickInside = true) {
            BosProjectInitToolContent()
        }
    }
}

/**
 * 侧边栏工具窗口的 Compose 内容组件
 * 目前仅展示一个随机数生成示例，实际的核心功能在 ProjectInitAction 弹出的对话框中。
 */
@Composable
private fun BosProjectInitToolContent() {
    val labelText = remember { mutableStateOf(MessageBundle.message("ui.random.number", "?")) }

    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(labelText.value, fontSize = Typography.defaultFontSize)

        // 点击按钮更新随机数，演示基本的 Compose 状态管理
        OutlinedButton(onClick = {
            labelText.value = MessageBundle.message("ui.random.number", Random(System.currentTimeMillis()).nextInt(1000))
        }) { 
            Text(MessageBundle.message("ui.shuffle"), fontSize = Typography.defaultFontSize) 
        }
    }
}