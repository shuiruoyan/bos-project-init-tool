package com.songwh.bosprojectinit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.songwh.bosprojectinit.ui.Typography
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * 自定义主题化输入框
 * 解决 BasicTextField 在 IDE 主题切换（深色/浅色）时的对比度显示问题，并统一样式。
 */
object TextFields {
    @Composable
    fun ThemedTextField(
        value: String,
        onValueChange: (String) -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        readOnly: Boolean = false,
        singleLine: Boolean = true,
        borderColor: Color,
        backgroundColor: Color,
        textColor: Color
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                .background(backgroundColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                .padding(8.dp),
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            textStyle = TextStyle(color = textColor, fontSize = Typography.defaultFontSize)
        )
    }
}
