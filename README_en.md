# BOS Project Initialization Assistant

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ_IDEA-2025.2.4+-orange)](https://www.jetbrains.com/idea/)

[(中文说明)](README.md) | [Architecture](ARCHITECTURE.md)

## Introduction

An IntelliJ IDEA plugin designed for Kingdee BOS platform developers. Simplify project initialization with one-click batch Git repository cloning and automatic configuration through a graphical interface.

![Plugin Interface Preview](src/main/resources/image/help.png)

## Features

- **Batch Cloning**: Support multiple repositories (HTTP/HTTPS/SSH)
- **Auto Configuration**: Auto-generate and sync config.gradle, settings.gradle, build_local.gradle
- **Visualization**: Real-time progress display, color-coded log output
- **Timeout Control**: Independent repository timeout settings
- **Cleanup Option**: Clear target directory before initialization
- **Interruptible**: Pause/cancel tasks at any time

## Quick Start

### Installation

1. `Settings` → `Plugins` → `⚙️` → `Install Plugin from Disk...`
2. Select the downloaded JAR file
3. Restart IDEA

### Usage

1. Menu: `Tools → BOS Project Initialization Assistant`
2. Configure parameters: project path, Git repository URLs, timeout
3. Click `Initialize` to execute

## Configuration

| Parameter | Description | Required |
|-----------|-------------|----------|
| Project Path | BOS project root directory (contains projects subdirectory) | ✅ |
| Timeout | Clone timeout per repository (seconds), default 60 | ❌ |
| Git Repository URLs | Repository URLs to clone, one per line | ✅ |
| Clear Option | Clear target directory before initialization | ❌ |

## Project Structure

```
bos-project-init/
├── src/main/kotlin/
│   ├── actions/           # Plugin entry
│   ├── logic/             # Core business logic
│   │   └── steps/         # Initialization steps
│   ├── model/             # Data models
│   ├── settings/          # Plugin settings
│   ├── ui/                # User interface
│   │   └── components/    # UI components
│   └── utils/             # Utilities
├── src/main/resources/
│   ├── messages/          # Multilingual resources
│   ├── image/             # Image resources
│   └── META-INF/          # Plugin configuration
├── src/test/kotlin/       # Test code
├── build.gradle.kts       # Gradle build script
└── README.md              # Project documentation
```

## Workflow

1. **Environment Check**: Verify Git installation and directory permissions
2. **Parameter Validation**: Validate Git URL format and paths
3. **Cleanup Directory**: Optionally clear target directory
4. **Clone Repositories**: Clone multiple repositories to projects directory
5. **Configuration Sync**: Auto-generate config.gradle, settings.gradle, build_local.gradle
6. **Results Summary**: Output execution report

## Common Issues

**Git Not Found**
- Confirm Git is installed and added to system PATH
- Restart IDEA

**Permission Denied**
- Check target directory permissions
- Ensure current user has read/write permissions

**Network Timeout**
- Increase timeout (120-300 seconds)
- Check network connection
- Confirm repository URL and access permissions

**URL Validation Failed**
- Ensure URL format is correct (supports https://, http://, git@)
- Check for illegal characters in URL

**SSH Authentication Failed**
- Ensure SSH key is added to Git server
- Or use HTTPS protocol

**Module Not Recognized**
- Manually refresh Gradle project
- Run `./gradlew projects` to check modules

## Development Environment

| Component | Requirement |
|-----------|-------------|
| IntelliJ IDEA | 2025.2.4+ |
| JDK | 21+ |
| Kotlin | 2.1.20 |
| Gradle | 8.x |

## Tech Stack

- Kotlin
- Compose UI for IntelliJ
- Kotlin Coroutines
- Git CLI
- IntelliJ Platform SDK

## License

This project is licensed under the [MIT License](LICENSE), Copyright (c) 2026 songwh.

