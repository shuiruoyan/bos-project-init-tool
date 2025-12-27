package com.songwh.bosprojectinit.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.songwh.bosprojectinit.MessageBundle
import com.songwh.bosprojectinit.logic.ProjectInitializer
import com.songwh.bosprojectinit.model.ProjectInitState
import com.songwh.bosprojectinit.settings.LanguageChangeListener
import com.songwh.bosprojectinit.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val STORAGE_KEY_ROOT_PATH = "com.songwh.bosprojectinit.rootPath"
private const val STORAGE_KEY_GIT_URLS = "com.songwh.bosprojectinit.gitUrls"

/**
 * 项目初始化助手的主要视图组件 (Compose 实现)
 * 采用响应式布局，管理整个初始化界面的 UI 交互和业务逻辑调度。
 */
@Composable
fun ProjectInitView(
    project: Project,
    onCancelRequest: (() -> Unit)? = null,
    initializerRef: ((ProjectInitializer?) -> Unit)? = null
) {
    // 使用 IntelliJ 提供的 PropertiesComponent 进行数据持久化，保存上次输入的路径和地址
    val propertiesComponent = remember(project) { PropertiesComponent.getInstance(project) }
    
    // 语言变化触发器，用于强制重组界面
    var languageVersion by remember { mutableStateOf(0) }
    
    // 监听语言变化事件
    DisposableEffect(Unit) {
        val connection = ApplicationManager.getApplication().messageBus.connect()
        connection.subscribe(LanguageChangeListener.TOPIC, object : LanguageChangeListener {
            override fun languageChanged(newLanguage: String) {
                // 语言变化时，增加版本号触发重组
                languageVersion++
            }
        })
        
        onDispose {
            connection.disconnect()
        }
    }
    
    // UI 统一状态管理
    var state by remember { 
        mutableStateOf(
            ProjectInitState(
                rootPath = propertiesComponent.getValue(STORAGE_KEY_ROOT_PATH, ""),
                gitUrls = propertiesComponent.getValue(STORAGE_KEY_GIT_URLS, ""),
                statusText = MessageBundle.message("ui.status.waiting")
            )
        ) 
    }
    
    // 控制帮助说明图层是否显示
    var showHelp by remember { mutableStateOf(false) }
    
    // 异步任务的 Job 引用，用于生命周期管理
    var currentJob by remember { mutableStateOf<Job?>(null) }
    
    // 后台逻辑执行器的引用
    var initializer by remember { mutableStateOf<ProjectInitializer?>(null) }
    
    // 详细信息滚动状态
    val logScrollState = rememberScrollState()

    // 副作用：当日志内容更新时，自动平滑滚动到最底部，提升 UX 体验
    LaunchedEffect(state.logLines) {
        logScrollState.animateScrollTo(logScrollState.maxValue)
    }

    // 副作用：将当前的 initializer 引用同步给外部（例如 Dialog 容器）
    LaunchedEffect(initializer) {
        initializerRef?.invoke(initializer)
    }

    val scope = rememberCoroutineScope()

    // 从当前 Jewel 主题中提取全局颜色配置，确保与 IDE 主题色一致
    val contentColor = JewelTheme.contentColor
    val borderColor = JewelTheme.globalColors.borders.normal
    val backgroundColor = JewelTheme.globalColors.panelBackground

    // 根布局采用 Box 以便放置帮助 Overlay
    // 使用 key 参数，当语言变化时强制重新渲染
    key(languageVersion) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()), // 支持在小屏幕下滚动整个表单
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题行：工程路径标签 + 帮助按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = MessageBundle.message("ui.root.path"),
                    fontSize = Typography.defaultFontSize,
                    fontWeight = Typography.labelFontWeight
                )
                Text(
                    text = MessageBundle.message("ui.help"),
                    color = Color(0xFF3592FF),
                    fontSize = Typography.defaultFontSize,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { showHelp = true }
                )
            }
            
            // 工程路径选择区域
            RootPathSection.Content(
                project = project,
                rootPath = state.rootPath,
                onPathChange = { 
                    if (!state.isRunning) {
                        state = state.copy(
                            rootPath = it, 
                            progress = 0f, 
                            statusText = MessageBundle.message("ui.status.waiting")
                        )
                    }
                },
                enabled = !state.isRunning,
                borderColor = borderColor,
                backgroundColor = backgroundColor,
                textColor = contentColor,
                modifier = Modifier.fillMaxWidth()
            )

            // 超时时间配置区域
            TimeoutSection.Content(
                timeoutSeconds = state.timeoutSeconds,
                onTimeoutChange = { newValue -> 
                    if (!state.isRunning) {
                        state = state.copy(
                            timeoutSeconds = newValue,
                            progress = 0f,
                            statusText = MessageBundle.message("ui.status.waiting")
                        )
                    }
                },
                enabled = !state.isRunning,
                borderColor = borderColor,
                backgroundColor = backgroundColor,
                textColor = contentColor,
                modifier = Modifier.fillMaxWidth()
            )

            // Git 仓库列表配置区域 (多行文本框)
            GitRepoSection.Content(
                gitUrls = state.gitUrls,
                onGitUrlsChange = { 
                    if (!state.isRunning) {
                        state = state.copy(
                            gitUrls = it,
                            progress = 0f,
                            statusText = MessageBundle.message("ui.status.waiting")
                        )
                    }
                },
                enabled = !state.isRunning,
                borderColor = borderColor,
                backgroundColor = backgroundColor,
                textColor = contentColor,
                modifier = Modifier.fillMaxWidth()
            )

            // 日志信息标题头（含成功/失败计数统计）
            DetailSection.Header(
                successCount = state.successCount,
                failureCount = state.failureCount,
                isRunning = state.isRunning
            )

            // 滚动日志显示区域
            DetailSection.LogArea(
                logLines = state.logLines,
                logScrollState = logScrollState,
                borderColor = borderColor,
                backgroundColor = backgroundColor,
                contentColor = contentColor,
                modifier = Modifier.fillMaxWidth()
            )

            // 底部状态提示栏
            Text(
                text = MessageBundle.message(
                    "ui.status.prefix",
                    if (state.isRunning || state.progress > 0) state.statusText else MessageBundle.message("ui.status.waiting")
                ),
                fontSize = Typography.defaultFontSize,
                modifier = Modifier.fillMaxWidth()
            )

            // 清理选项：是否在 Clone 前清空目录
            CleanProjectsSection.Content(
                checked = state.cleanProjectsBeforeClone,
                onCheckedChange = { state = state.copy(cleanProjectsBeforeClone = it) },
                enabled = !state.isRunning
            )

            // 初始化启动按钮（带进度条背景）
            ProgressButtonSection.Content(
                text = if (state.isRunning) {
                    MessageBundle.message("ui.init.button.running", (state.progress * 100).toInt())
                } else {
                    MessageBundle.message("ui.init.button")
                },
                progress = state.progress,
                enabled = !state.isRunning && state.rootPath.isNotEmpty() && GitRepoSection.countValidGitUrls(state.gitUrls) > 0,
                isRunning = state.isRunning,
                onClick = {
                    // 点击初始化按钮的逻辑处理
                    handleStartClick(
                        project, 
                        propertiesComponent, 
                        state, 
                        scope, 
                        onStateUpdate = { state = it },
                        onInitializerUpdate = { initializer = it }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 帮助详情层 (Overlay)
        if (showHelp) {
            HelpOverlay(backgroundColor, onDismiss = { showHelp = false })
        }
    }
    }
}

/**
 * 启动初始化任务的具体逻辑处理
 */
private fun handleStartClick(
    project: Project,
    propertiesComponent: PropertiesComponent,
    state: ProjectInitState,
    scope: kotlinx.coroutines.CoroutineScope,
    onStateUpdate: (ProjectInitState) -> Unit,
    onInitializerUpdate: (ProjectInitializer?) -> Unit
) {
    // 检查是否存在不合格的Git地址
    val invalidUrls = GitRepoSection.getInvalidGitUrls(state.gitUrls)
    
    // 用于后续使用的实际状态（可能是清理后的）
    var actualState = state
    
    if (invalidUrls.isNotEmpty()) {
        // 构建不合格地址的列表显示
        val invalidUrlsList = invalidUrls.joinToString("\n") { "  • $it" }
        
        val result = Messages.showDialog(
            project,
            MessageBundle.message("dialog.invalid.urls.message", invalidUrlsList),
            MessageBundle.message("dialog.invalid.urls.title"),
            arrayOf(
                MessageBundle.message("dialog.invalid.urls.delete.continue"),
                MessageBundle.message("dialog.invalid.urls.cancel")
            ),
            0, // 默认选中第一个按钮
            Messages.getWarningIcon()
        )
        
        if (result == 0) {
            // 用户选择"删除并继续"，移除无效地址
            val cleanedUrls = GitRepoSection.removeInvalidGitUrls(state.gitUrls)
            actualState = state.copy(gitUrls = cleanedUrls)
            onStateUpdate(actualState)
            // 持久化清理后的URL
            propertiesComponent.setValue(STORAGE_KEY_GIT_URLS, cleanedUrls)
        } else {
            // 用户选择"取消"，不执行初始化
            return
        }
    }
    
    // 如果开启了清理选项，先弹窗提示用户确认
    val shouldStart = if (actualState.cleanProjectsBeforeClone) {
        Messages.showYesNoDialog(
            project,
            MessageBundle.message("dialog.clean.confirm.message", actualState.rootPath),
            MessageBundle.message("dialog.clean.confirm.title"),
            MessageBundle.message("dialog.clean.confirm.ok"),
            MessageBundle.message("dialog.clean.confirm.cancel"),
            Messages.getWarningIcon()
        ) == Messages.YES
    } else {
        true
    }

    if (shouldStart) {
        // 1. 持久化当前输入的有效参数，方便下次开启
        propertiesComponent.setValue(STORAGE_KEY_ROOT_PATH, actualState.rootPath)
        propertiesComponent.setValue(STORAGE_KEY_GIT_URLS, actualState.gitUrls)

        // 2. 初始化本地 UI 状态
        var updatedState = actualState.copy(
            isRunning = true,
            progress = 0f,
            statusText = MessageBundle.message("log.init.start"),
            logLines = listOf(
                MessageBundle.message("log.entry.format", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), MessageBundle.message("log.system"), 0, MessageBundle.message("log.init.start"))
            ),
            successCount = 0,
            failureCount = 0
        )
        onStateUpdate(updatedState)

        // 3. 执行启动前的合法性预检查
        val rootFile = File(actualState.rootPath)
        if (actualState.rootPath.isBlank() || !rootFile.exists() || !rootFile.isDirectory) {
            Messages.showErrorDialog(project, MessageBundle.message("error.invalid.path"), MessageBundle.message("error.title"))
            onStateUpdate(updatedState.copy(isRunning = false))
            return
        }
        if (!rootFile.canWrite()) {
            Messages.showErrorDialog(project, MessageBundle.message("error.path.no.write"), MessageBundle.message("error.title"))
            onStateUpdate(updatedState.copy(isRunning = false))
            return
        }
        
        // 归一化 URL 列表，过滤掉空行和重复项
        val uniqueUrls = GitRepoSection.normalizeGitUrls(actualState.gitUrls)
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            
        if (uniqueUrls.isEmpty()) {
            Messages.showErrorDialog(project, MessageBundle.message("error.no.urls"), MessageBundle.message("error.title"))
            onStateUpdate(updatedState.copy(isRunning = false))
            return
        }

        // 4. 创建逻辑执行器
        val newInitializer = ProjectInitializer(project)
        onInitializerUpdate(newInitializer)

        val timeout = state.timeoutSeconds.toLongOrNull() ?: 300L
        
        // 5. 启动后台协程开始执行任务流
        scope.launch(Dispatchers.Default) {
            try {
                newInitializer.initialize(
                    actualState.rootPath,
                    uniqueUrls,
                    timeout,
                    actualState.cleanProjectsBeforeClone,
                    onProgress = { p, status ->
                        withContext(Dispatchers.Main) {
                            updatedState = updatedState.copy(progress = p, statusText = status)
                            onStateUpdate(updatedState)
                        }
                    },
                    onLogUpdate = { logs ->
                        withContext(Dispatchers.Main) {
                            updatedState = updatedState.copy(logLines = logs)
                            onStateUpdate(updatedState)
                        }
                    },
                    onStatsUpdate = { success, failure ->
                        withContext(Dispatchers.Main) {
                            updatedState = updatedState.copy(successCount = success, failureCount = failure)
                            onStateUpdate(updatedState)
                        }
                    }
                )
            } catch (e: Exception) {
                // 捕获任务执行中的异常并记录日志
                withContext(Dispatchers.Main) {
                    updatedState = updatedState.copy(
                        logLines = updatedState.logLines + MessageBundle.message(
                            "log.entry.format",
                            java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")),
                            MessageBundle.message("log.system"),
                            0,
                            MessageBundle.message("log.error", e.message ?: "")
                        )
                    )
                    onStateUpdate(updatedState)
                }
            } finally {
                // 无论成功失败，重置运行状态
                withContext(Dispatchers.Main) {
                    val isCancelled = newInitializer.isCancelled()
                    updatedState = updatedState.copy(
                        isRunning = false,
                        statusText = if (isCancelled) MessageBundle.message("status.cancelled") else MessageBundle.message("status.done"),
                        progress = if (isCancelled) updatedState.progress else 1f
                    )
                    onStateUpdate(updatedState)
                    onInitializerUpdate(null)
                }
            }
        }
    }
}

/**
 * 帮助图层组件
 */
@Composable
private fun HelpOverlay(backgroundColor: Color, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 半透明背景遮罩，点击任意位置可退出帮助
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor.copy(alpha = 0.95f))
                .clickable { onDismiss() }
        )

        val helpImagePainter = remember { ImageLoader.loadHelpPainter() }

        // 使用 Box 容器来包装图片和位于右上角的关闭按钮
        Box(
            modifier = Modifier
                .padding(32.dp) // 预留一些边距
                .clickable(enabled = false) { } // 阻止点击穿透到遮罩层
        ) {
            // 图片展示区域
            Image(
                painter = helpImagePainter,
                contentDescription = "Help Image",
                modifier = Modifier
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(4.dp))
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .padding(4.dp) // 给图片加一点边框感
            )

            // 圆形关闭按钮，定位在右上角
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 12.dp, y = (-12).dp) // 稍微偏移，使其跨在边缘上
                    .size(28.dp)
                    .shadow(elevation = 8.dp, shape = CircleShape)
                    .background(JewelTheme.globalColors.borders.normal, CircleShape) // 使用标准边框颜色作为背景
                    .clip(CircleShape)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 图片资源加载助手
 */
object ImageLoader {
    /**
     * 加载位于 resources/image/help.png 的本地帮助图片
     */
    fun loadHelpPainter(): Painter {
        return try {
            val resourcePath = "image/help.png"
            val stream = ImageLoader::class.java.classLoader.getResourceAsStream(resourcePath)
            if (stream != null) {
                val bytes = stream.readAllBytes()
                val skiaImage = Image.makeFromEncoded(bytes)
                val bitmap = Bitmap.makeFromImage(skiaImage).asComposeImageBitmap()
                BitmapPainter(bitmap)
            } else {
                ColorPainter(Color.Red)
            }
        } catch (e: Exception) {
            ColorPainter(Color.Red) // 加载失败时显示红色块
        }
    }
}
