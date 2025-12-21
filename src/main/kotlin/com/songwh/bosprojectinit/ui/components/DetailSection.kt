package com.songwh.bosprojectinit.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.songwh.bosprojectinit.MessageBundle
import com.songwh.bosprojectinit.ui.Typography
import org.jetbrains.jewel.ui.component.Text

/**
 * 详情展示区域（日志和统计）
 */
object DetailSection {
    /**
     * 日志头部：包含标题以及动态更新的成功/失败统计角标
     */
    @Composable
    fun Header(
        successCount: Int,
        failureCount: Int,
        isRunning: Boolean,
        modifier: Modifier = Modifier
    ) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = MessageBundle.message("ui.detail"),
                fontSize = Typography.defaultFontSize,
                fontWeight = Typography.labelFontWeight
            )
            // 只有当任务开始运行或有统计数据时，才展示统计标签
            if (successCount > 0 || failureCount > 0 || isRunning) {
                Text(
                    text = MessageBundle.message("ui.success.count", successCount),
                    color = Color(0xFF4CAF50),
                    fontSize = Typography.defaultFontSize,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .background(Color(0xFF4CAF50).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Text(
                    text = MessageBundle.message("ui.failure.count", failureCount),
                    color = Color(0xFFF44336),
                    fontSize = Typography.defaultFontSize,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .background(Color(0xFFF44336).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }

    /**
     * 日志内容展示区
     * 采用只读文本框配合自定义滚动条实现。
     */
    @Composable
    fun LogArea(
        logLines: List<String>,
        logScrollState: androidx.compose.foundation.ScrollState,
        borderColor: Color,
        backgroundColor: Color,
        contentColor: Color,
        modifier: Modifier = Modifier
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(100.dp)
                .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                .background(backgroundColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
        ) {
            BasicTextField(
                value = logLines.joinToString("\n"),
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(8.dp)
                    .padding(end = 12.dp)
                    .verticalScroll(logScrollState),
                readOnly = true, // 设置为只读，不允许用户修改日志内容
                textStyle = TextStyle(color = contentColor, fontSize = Typography.logFontSize)
            )
            // 垂直滚动条适配器
            VerticalScrollbar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                adapter = rememberScrollbarAdapter(logScrollState)
            )
        }
    }
}
