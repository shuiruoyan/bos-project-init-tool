package com.songwh.bosprojectinit.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.songwh.bosprojectinit.MessageBundle
import com.songwh.bosprojectinit.logic.ProjectInitializer
import org.jetbrains.jewel.bridge.JewelComposePanel
import java.awt.Dimension
import javax.swing.JComponent

/**
 * 项目初始化助手的对话框容器
 * 继承自 DialogWrapper，是 IntelliJ 插件的标准对话框实现方式。
 */
class ProjectInitDialog(private val project: Project) : DialogWrapper(project) {

    // 内部持有的初始化逻辑引用，用于在关闭对话框时请求取消任务
    private var currentInitializer: ProjectInitializer? = null
    
    // 标记初始化任务是否正在异步执行中
    private var isTaskRunning: Boolean = false

    init {
        title = MessageBundle.message("dialog.title")
        isResizable = false  // 苍穹初始化助手界面采用固定大小，防止布局错乱
        init()
    }

    /**
     * 创建对话框的核心内容区域
     * 使用 JewelComposePanel 将 Compose UI 桥接到 Swing 界面中
     */
    override fun createCenterPanel(): JComponent {
        return JewelComposePanel(focusOnClickInside = true) {
            // 加载 Compose 编写的视图
            ProjectInitView(
                project = project,
                // 当 View 层创建或销毁初始化器时，通过此回调同步状态给 Dialog
                initializerRef = { initializer ->
                    currentInitializer = initializer
                    isTaskRunning = initializer != null
                }
            )
        }.apply {
            // 设置面板的初始推荐大小
            preferredSize = Dimension(480, 600)
        }
    }

    /**
     * 拦截对话框的取消/关闭操作
     * 如果任务正在运行，会弹出二次确认框，防止用户误操作导致初始化中断。
     */
    override fun doCancelAction() {
        if (isTaskRunning && currentInitializer != null) {
            // 任务正在执行，弹出确认对话框
            val result = Messages.showYesNoDialog(
                project,
                MessageBundle.message("dialog.cancel.message"),
                MessageBundle.message("dialog.cancel.title"),
                MessageBundle.message("dialog.cancel.ok"),
                MessageBundle.message("dialog.cancel.continue"),
                Messages.getWarningIcon()
            )
            
            if (result == Messages.YES) {
                // 用户确认取消，通知初始化器终止所有子进程和协程
                currentInitializer?.cancel()
                super.doCancelAction()
            }
            // 如果用户选择"继续执行"，则不执行 super.doCancelAction()，对话框保持开启
        } else {
            // 没有任务在执行，或者任务已结束，直接关闭窗口
            super.doCancelAction()
        }
    }

    /**
     * 拦截对话框的确定操作
     * 如果任务正在运行，提示任务未完成，阻止关闭。
     */
    override fun doOKAction() {
        if (isTaskRunning) {
            Messages.showWarningDialog(
                project,
                MessageBundle.message("dialog.running.message"),
                MessageBundle.message("dialog.running.title")
            )
        } else {
            super.doOKAction()
        }
    }
}
