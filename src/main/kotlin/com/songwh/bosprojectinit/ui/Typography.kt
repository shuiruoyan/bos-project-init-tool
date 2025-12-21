package com.songwh.bosprojectinit.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * UI 字体样式配置
 * 统一管理全局的字体大小和粗细配置。
 */
object Typography {
    // 默认的正文字体大小 (IntelliJ 标准大小约为 13sp)
    val defaultFontSize = 13.sp
    
    // 日志信息字体大小，稍微缩小以展示更多内容
    val logFontSize = 12.sp
    
    // 标题标签的字体粗细
    val labelFontWeight = FontWeight.Bold
}
