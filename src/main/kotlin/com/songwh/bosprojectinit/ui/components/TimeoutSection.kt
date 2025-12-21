package com.songwh.bosprojectinit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.songwh.bosprojectinit.MessageBundle
import com.songwh.bosprojectinit.ui.Typography
import org.jetbrains.jewel.ui.component.Text

/**
 * 超时时间设置组件
 * 包含一个标签和一个仅允许输入数字的短文本框。
 */
object TimeoutSection {
    @Composable
    fun Content(
        timeoutSeconds: String,
        onTimeoutChange: (String) -> Unit,
        enabled: Boolean,
        borderColor: Color,
        backgroundColor: Color,
        textColor: Color,
        modifier: Modifier = Modifier
    ) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = MessageBundle.message("ui.timeout.label"),
                fontSize = Typography.defaultFontSize,
                fontWeight = Typography.labelFontWeight
            )
            // 数字输入框，限制只能输入数字字符
            TextFields.ThemedTextField(
                value = timeoutSeconds,
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                        onTimeoutChange(newValue)
                    }
                },
                modifier = Modifier.width(100.dp),
                enabled = enabled,
                borderColor = borderColor,
                backgroundColor = backgroundColor,
                textColor = textColor
            )
        }
    }
}
