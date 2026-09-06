# 苍穹工程初始化助手

[![Version](https://img.shields.io/badge/Version-1.0.0-blue)](CHANGELOG.md)
[![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ_IDEA-2025.2.4+-orange)](https://www.jetbrains.com/idea/)

[English](README_en.md) | [架构文档](ARCHITECTURE.md)

## 简介

专为金蝶苍穹（Kingdee BOS）平台开发者设计的 IntelliJ IDEA 插件。通过图形化界面一键批量克隆 Git 仓库，自动完成项目配置，简化初始化流程。

![插件界面预览](src/main/resources/image/help.png)

## 功能特性

- **批量克隆**：支持多个仓库（HTTP/HTTPS/SSH）
- **自动配置**：自动生成和同步 config.gradle、settings.gradle、build_local.gradle
- **可视化**：实时进度显示、分色日志输出
- **超时控制**：独立仓库超时设置
- **清理选项**：初始化前清空目标目录
- **可中断**：随时暂停/取消任务

## 快速开始

### 安装

1. `设置` → `插件` → `⚙️` → `Install Plugin from Disk...`
2. 选择下载的 JAR 文件
3. 重启 IDEA

### 使用

1. 菜单：`Tools → 苍穹工程初始化助手`
2. 配置参数：工程路径、Git 仓库地址、超时时间
3. 点击 `初始化` 执行

## 配置说明

| 参数 | 说明 | 必填 |
|------|------|------|
| 工程路径 | 苍穹项目根目录（存放 projects 子目录） | ✅ |
| 超时时间 | 单个仓库克隆超时（秒），默认 60 | ❌ |
| Git 仓库地址 | 需要克隆的仓库地址，每行一个 | ✅ |
| 清空选项 | 初始化前清空目标目录 | ❌ |



## 项目结构

```
bos-project-init/
├── src/main/kotlin/
│   ├── actions/           # 插件入口
│   ├── logic/             # 核心业务逻辑
│   │   └── steps/         # 初始化步骤实现
│   ├── model/             # 数据模型
│   ├── settings/          # 插件设置
│   ├── ui/                # 用户界面
│   │   └── components/    # UI组件
│   └── utils/             # 工具类
├── src/main/resources/
│   ├── messages/          # 多语言资源
│   ├── image/             # 图片资源
│   └── META-INF/          # 插件配置
├── src/test/kotlin/       # 测试代码
├── build.gradle.kts       # Gradle构建脚本
└── README.md              # 项目说明
```

## 工作流程

1. **环境检查**：验证 Git 安装和目录权限
2. **参数校验**：校验 Git URL 格式和路径
3. **清理目录**：可选清空目标目录
4. **克隆仓库**：克隆多个仓库到 projects 目录
5. **配置同步**：自动生成 config.gradle、settings.gradle、build_local.gradle
6. **结果汇总**：输出执行报告

## 常见问题

**Git 未找到**
- 确认已安装 Git 并添加到系统 PATH
- 重启 IDEA

**权限被拒绝**
- 检查目标目录权限
- 确保当前用户有读写权限

**网络超时**
- 增加超时时间（120-300 秒）
- 检查网络连接
- 确认仓库地址和访问权限

**URL 校验失败**
- 确保 URL 格式正确（支持 https://, http://, git@）
- 检查 URL 是否包含非法字符

**SSH 认证失败**
- 确保 SSH 密钥已添加到 Git 服务器
- 或使用 HTTPS 协议

**模块未识别**
- 手动刷新 Gradle 项目
- 运行 `./gradlew projects` 查看模块

## 开发环境

| 组件 | 要求 |
|------|------|
| IntelliJ IDEA | 2025.2.4+ |
| JDK | 21+ |
| Kotlin | 2.1.20 |
| Gradle | 8.x |

## 技术栈

- Kotlin
- Compose UI for IntelliJ
- Kotlin Coroutines
- Git CLI
- IntelliJ Platform SDK
