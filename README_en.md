# BOS Project Initialization Assistant

This is an IntelliJ IDEA plugin designed for Kingdee BOS platform developers to simplify the BOS project initialization process. Through a graphical interface configuration, it can batch pull multiple Git repositories with one click and automatically complete various configuration tasks required by BOS projects.

![](src/main/resources/image/help.png)

## Features

- 🚀 **Batch Git Clone**: Support pulling multiple Git repositories at once
- ⚙️ **Automated Configuration**: Automatically generate or modify configuration files required by the BOS platform
- 📊 **Visual Interface**: Intuitive graphical interface that displays progress and logs in real-time
- ⏱️ **Timeout Control**: Set maximum wait time for individual repository pulls
- 🧹 **Cleanup Option**: Support clearing existing project directories before initialization
- 🛑 **Interruptible Operations**: Support canceling ongoing tasks at any time

## How to Use

1. Open the plugin in IntelliJ IDEA
2. Launch the plugin via menu `Tools -> BOS Project Initialization Assistant`
3. Configure the following parameters:
   - Select the BOS start project root directory
   - Enter the list of Git repository URLs to pull (one per line)
   - Set timeout (optional, default 60 seconds)
   - Choose whether to clear existing projects (optional)
4. Click the "Initialize" button to start execution

## Project Structure

```
.
├── src/main/kotlin/com/songwh/bosprojectinit/
│   ├── actions/                 # IDEA action entry
│   │   └── ProjectInitAction.kt # Plugin main entry action
│   ├── logic/                   # Core business logic
│   │   ├── steps/               # Initialization step definitions
│   │   │   ├── impl/            # Specific step implementations
│   │   │   │   ├── CloneStep.kt           # Repository cloning step
│   │   │   │   ├── ConfigGradleStep.kt    # config.gradle synchronization step
│   │   │   │   ├── SettingsStep.kt        # settings.gradle configuration step
│   │   │   │   ├── BuildLocalStep.kt      # Root build_local.gradle generation step
│   │   │   │   └── ModuleBuildLocalStep.kt # Module build_local.gradle generation step
│   │   │   └── IProjectInitStep.kt        # Step interface definition
│   │   └── ProjectInitializer.kt          # Project initializer core class
│   ├── model/                             # Data models
│   │   └── InitializationModels.kt        # Various data model definitions
│   ├── ui/                                # User interface
│   │   ├── components/                    # UI components
│   │   ├── ProjectInitDialog.kt           # Initialization dialog container
│   │   ├── ProjectInitView.kt             # Initialization main view
│   │   └── Typography.kt                  # Font style definitions
│   ├── utils/                             # Utility classes
│   │   └── GitUtils.kt                    # Git operation utility class
│   ├── BosProjectInitTool.kt              # Tool window implementation
│   └── MessageBundle.kt                   # Internationalization message bundle
│
├── src/main/resources/
│   ├── messages/                          # Multilingual resource files
│   │   ├── MessageBundle.properties       # Default language pack (Chinese)
│   │   ├── MessageBundle_en_US.properties # English language pack
│   │   └── MessageBundle_zh_CN.properties # Chinese language pack
│   ├── image/                             # Image resources
│   │   └── help.png                       # Help image
│   └── META-INF/
│       └── plugin.xml                     # Plugin configuration file
│
├── build.gradle.kts                       # Gradle build script
├── settings.gradle.kts                    # Gradle settings file
└── gradle.properties                      # Gradle property configuration
```

## Core Workflow

1. **Code Cloning**: Batch clone to the specified projects directory based on Git URLs provided by the user
2. **Configuration Sync**: Synchronize config.gradle configuration file
3. **Settings Configuration**: Generate or update settings.gradle file to include all modules
4. **Build Configuration**: Generate build_local.gradle file for the root project
5. **Module Configuration**: Generate corresponding build_local.gradle files for each submodule

## Development Environment

- IntelliJ IDEA 2025.2.4 or higher
- JDK 21
- Kotlin 2.1.20
- Gradle 8.x

## Build and Run

```bash
# Build plugin
./gradlew buildPlugin

# Run plugin for testing
./gradlew runIde
```

## Tech Stack

- Kotlin
- Compose UI for IntelliJ
- Coroutines
- Git command-line tools

## Notes

- Ensure Git command-line tools are installed and added to the PATH environment variable
- The plugin needs network access to clone remote repositories
- The plugin needs read/write permissions to the target directory