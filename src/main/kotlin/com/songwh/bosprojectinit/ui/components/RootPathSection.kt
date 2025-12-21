package com.songwh.bosprojectinit.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.songwh.bosprojectinit.MessageBundle
import com.songwh.bosprojectinit.ui.Typography
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

/**
 * 启动工程路径配置组件
 * 提供一个文本框展示路径，以及一个“浏览”按钮调用 IDE 的文件选择器。
 */
object RootPathSection {
    @Composable
    fun Content(
        project: Project,
        rootPath: String,
        onPathChange: (String) -> Unit,
        enabled: Boolean,
        borderColor: Color,
        backgroundColor: Color,
        textColor: Color,
        modifier: Modifier = Modifier
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 路径输入框
            PathField(
                rootPath = rootPath,
                onPathChange = onPathChange,
                enabled = enabled,
                borderColor = borderColor,
                backgroundColor = backgroundColor,
                textColor = textColor
            )
            // 浏览按钮，点击弹出 IDE 标准文件夹选择器
            OutlinedButton(onClick = {
                val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                val file = FileChooser.chooseFile(descriptor, project, null)
                if (file != null) {
                    onPathChange(file.path)
                }
            }, enabled = enabled) {
                Text(MessageBundle.message("ui.browse"), fontSize = Typography.defaultFontSize)
            }
        }
    }
}

@Composable
private fun RowScope.PathField(
    rootPath: String,
    onPathChange: (String) -> Unit,
    enabled: Boolean,
    borderColor: Color,
    backgroundColor: Color,
    textColor: Color
) {
    TextFields.ThemedTextField(
        value = rootPath,
        onValueChange = onPathChange,
        modifier = Modifier.weight(1f),
        enabled = enabled,
        borderColor = borderColor,
        backgroundColor = backgroundColor,
        textColor = textColor
    )
}
