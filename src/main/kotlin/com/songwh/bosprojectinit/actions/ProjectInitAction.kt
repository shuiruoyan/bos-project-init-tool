package com.songwh.bosprojectinit.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.songwh.bosprojectinit.ui.ProjectInitDialog

/**
 * 插件主入口 Action
 * 当用户在 IDE 菜单或快捷键中触发“苍穹工程初始化助手”时，会执行此类的逻辑。
 */
class ProjectInitAction : AnAction() {
    /**
     * 执行 Action 逻辑：弹出初始化对话框
     */
    override fun actionPerformed(e: AnActionEvent) {
        // 获取当前的 Project 上下文，如果不存在则直接返回
        val project = e.project ?: return
        
        // 创建并显示对话框
        ProjectInitDialog(project).show()
    }
}
