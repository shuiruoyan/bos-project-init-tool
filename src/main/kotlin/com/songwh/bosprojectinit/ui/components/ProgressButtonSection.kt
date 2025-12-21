package com.songwh.bosprojectinit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.songwh.bosprojectinit.ui.Typography
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

/**
 * 进度按钮组件
 * 外观是一个按钮，当任务运行时，内部会根据进度填充背景色，模拟进度条效果。
 */
object ProgressButtonSection {
    @Composable
    fun Content(
        text: String,
        progress: Float,
        enabled: Boolean,
        isRunning: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val shape = RoundedCornerShape(6.dp)
        // 获取主题相关的边框和背景色
        val borderColor = JewelTheme.globalColors.borders.normal
        val backgroundColor = JewelTheme.globalColors.panelBackground
        val disabledBackgroundColor = backgroundColor.copy(alpha = 0.5f)
        val progressColor = Color(0xFF4CAF50).copy(alpha = 0.6f) // 进度条颜色（半透明绿）
        val disabledColor = JewelTheme.globalColors.borders.disabled

        Box(
            modifier = modifier
                .height(40.dp)
                .border(1.dp, if (enabled || isRunning) borderColor else disabledColor, shape)
                .background(if (enabled || isRunning) backgroundColor else disabledBackgroundColor, shape),
            contentAlignment = Alignment.Center
        ) {
            // 进度背景层：根据 progress 比例计算宽度
            if (isRunning || (enabled && progress > 0f)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .height(40.dp)
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(progressColor, shape)
                )
            }

            // 按钮点击层：覆盖在最上方，透明背景
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxSize()
            ) {
                // 根据进度背景的存在与否，动态调整文字颜色
                // 当正在运行且进度覆盖到中间区域时，使用白色以保证在绿色背景上的可辨识度
                val textColor = if (isRunning && progress > 0.4f) {
                    Color.Gray
                } else {
                    JewelTheme.contentColor
                }
                
                Text(
                    text,
                    fontSize = Typography.defaultFontSize,
                    color = textColor
                )
            }
        }
    }
}
