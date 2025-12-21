package com.songwh.bosprojectinit.logic.steps

import com.songwh.bosprojectinit.model.StepExecutionContext
import com.songwh.bosprojectinit.model.StepResult

/**
 * 项目初始化步骤的基础接口
 * 采用策略模式定义每个独立步骤的执行逻辑
 */
interface IProjectInitStep {
    /**
     * 步骤名称对应的国际化资源 Key
     */
    val nameKey: String

    /**
     * 执行具体步骤逻辑
     * 
     * @param context 步骤执行上下文，包含配置信息和共享数据（如收集到的模块信息）
     * @param stepIndex 当前步骤在总流程中的索引（用于进度计算）
     * @param totalSteps 总步骤数（用于进度计算）
     * @return 步骤执行结果
     */
    suspend fun execute(context: StepExecutionContext, stepIndex: Int, totalSteps: Int): StepResult
}
