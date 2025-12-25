package com.songwh.bosprojectinit.ui.components

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.songwh.bosprojectinit.MessageBundle
import com.songwh.bosprojectinit.ui.Typography
import com.songwh.bosprojectinit.utils.GitUtils
import org.jetbrains.jewel.ui.component.Text

/**
 * Git 仓库列表配置区域
 * 包含 URL 归一化逻辑以及带滚动条的多行文本输入框。
 */
object GitRepoSection {

    /**
     * 对输入的 URL 文本进行规范化处理：去除空行、首尾空格，并保留合法的 Git 地址格式。
     */
    fun normalizeGitUrls(text: String): String {
        return text.split("\n")
            .map { it.trim() }
            .filter { isValidGitUrl(it) }
            .joinToString("\n")
    }

    /**
     * 统计文本中有效且不重复的 Git 仓库地址数量。
     */
    fun countValidGitUrls(text: String): Int {
        return text.split("\n")
            .map { it.trim() }
            .filter { isValidGitUrl(it) }
            .distinct()
            .size
    }

    @Composable
    fun Content(
        gitUrls: String,
        onGitUrlsChange: (String) -> Unit,
        enabled: Boolean,
        borderColor: Color,
        backgroundColor: Color,
        textColor: Color,
        modifier: Modifier = Modifier
    ) {
        // 计算当前输入的有效仓库数，并在界面实时反馈
        val validRepoCount = remember(gitUrls) { countValidGitUrls(gitUrls) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = MessageBundle.message("ui.git.list.label"),
                fontSize = Typography.defaultFontSize,
                fontWeight = Typography.labelFontWeight
            )
            // 数量角标显示，只有大于 0 时才显示绿色背景
            Text(
                text = MessageBundle.message("ui.valid.repo", validRepoCount),
                fontSize = Typography.defaultFontSize,
                modifier = Modifier
                    .background(
                        if (validRepoCount > 0) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }

        val gitUrlsScrollState = rememberScrollState()
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(150.dp)
                .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                .background(backgroundColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
        ) {
            // 使用 BasicTextField 以便获得更灵活的定制外观
            BasicTextField(
                value = gitUrls,
                onValueChange = {
                    val normalized = normalizeGitUrls(it)
                    if (normalized != gitUrls) {
                        onGitUrlsChange(normalized)
                    } else {
                        onGitUrlsChange(it)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(8.dp)
                    .padding(end = 12.dp) // 为右侧滚动条预留空间
                    .verticalScroll(gitUrlsScrollState),
                enabled = enabled,
                textStyle = TextStyle(color = textColor, fontSize = Typography.defaultFontSize)
            )
            // 手动添加垂直滚动条，因为 BasicTextField 默认不带滚动条 UI
            VerticalScrollbar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                adapter = rememberScrollbarAdapter(gitUrlsScrollState)
            )
        }
    }

    /**
     * 判断一行文本是否为潜在的 Git 地址
     */
    private fun isValidGitUrl(line: String): Boolean {
        return GitUtils.isSupportedGitRemoteUrl(line)
    }
}
