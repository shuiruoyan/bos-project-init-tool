# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Is

An **IntelliJ IDEA plugin** for Kingdee BOS (Business Operation System) platform developers. It automates initializing multi-repository Gradle workspaces by batch-cloning Git repos and generating Gradle configuration files (`settings.gradle`, `build_local.gradle`, per-module `build_local.gradle`, `config.gradle`).

Requires: IntelliJ IDEA 2025.2.4+, JDK 21, Git CLI in PATH.

## Commands

```bash
# Build distributable plugin ZIP
./gradlew buildPlugin

# Launch sandboxed IDE for manual testing
./gradlew runIde

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.songwh.bosprojectinit.utils.SecurityUtilsTest"

# Run a single test method
./gradlew test --tests "com.songwh.bosprojectinit.utils.SecurityUtilsTest.testValidateGitUrlValidUrls"

# Clean
./gradlew clean
```

No separate lint/format task — use IntelliJ's built-in Kotlin formatter.

## Architecture

Entry point: `Tools -> BOS Project Initialization Assistant` triggers `ProjectInitAction`, which opens `ProjectInitDialog` (wrapping `ProjectInitView`).

**Layer flow:**
```
ProjectInitAction (action entry)
  └─ ProjectInitDialog / ProjectInitView (Compose Desktop UI, MVVM state)
       └─ ProjectInitializer (orchestrator, coroutine dispatch)
            └─ IProjectInitStep implementations (strategy pattern):
                 1. CloneStep        (~60-70% progress weight)
                 2. ConfigGradleStep (~10%)
                 3. SettingsStep     (~10%)
                 4. BuildLocalStep   (~10%)
                 5. ModuleBuildLocalStep (~10%)
```

**Key files:**
- `InitializationModels.kt` — shared data types: `StepExecutionContext`, `ProjectInitState`, `LogEntry`, `ModuleInfo`
- `GitUtils.kt` — `ProcessBuilder`-based git process management; uses `AtomicReference<Process?>` and `ConcurrentHashMap` for thread-safe cancellation
- `SecurityUtils.kt` — 4-layer input validation (URL, path, repo name, sanitization); all validation before any I/O
- `PluginSettings.kt` / `PluginSettingsConfigurable.kt` — persistent IDE settings (project root, default Git base URL, etc.)
- `MessageBundle.properties` / `*_en_US` / `*_zh_CN` — i18n strings

**Concurrency model:** All I/O runs on `Dispatchers.IO`. Clone step limits concurrency to 3 simultaneous clones via a `Semaphore`. Progress and log updates flow back to the UI via `suspend` callbacks. UI state is held in a `MutableState` / `StateFlow` and never mutated off the main thread.

**Security invariant:** Git commands are always built with `ProcessBuilder` argument arrays — never string concatenation or shell interpolation. `SecurityUtils` must validate all user inputs (Git URL, path, repo name) before they reach `GitUtils`.

## Plugin Registration

`src/main/resources/META-INF/plugin.xml` declares:
- Plugin ID: `com.songwh.bos-project-init`
- Action: registered under the `ToolsMenu` group
- Settings configurable: `PluginSettingsConfigurable`
- Required platform plugins: `com.intellij.modules.compose`, `com.intellij.modules.json`, `Git4Idea`
