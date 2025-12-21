package com.songwh.bosprojectinit.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.songwh.bosprojectinit.MessageBundle
import com.songwh.bosprojectinit.ui.Typography
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.Text

/**
 * 清理选项组件
 * 提供一个复选框，允许用户选择在 Clone 之前是否清空本地 projects 目录。
 */
object CleanProjectsSection {
    @Composable
    fun Content(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        enabled: Boolean,
        modifier: Modifier = Modifier
    ) {
        Row(
            modifier = modifier
                .clickable(enabled = enabled) {
                    onCheckedChange(!checked)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { if (enabled) onCheckedChange(it) },
                enabled = enabled
            )
            Text(
                text = MessageBundle.message("ui.clean.projects"),
                fontSize = Typography.defaultFontSize
            )
        }
    }
}
