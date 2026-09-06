# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.4] - 2026-09-06

### Changed

- `MessageBundle` resource files are now stored as UTF-8, so Chinese characters appear directly in the `.properties` files instead of `\uXXXX` escapes
- Added a custom `ResourceBundle.Control` (`Utf8Control`) so the bundle is read as UTF-8 at runtime
- Bumped version to 1.1.4

### Fixed

- IntelliJ displayed `MessageBundle_zh_CN.properties` Chinese text as unicode escape sequences; it now shows the actual Chinese characters

## [1.1.3] - 2026-09-06

### Fixed

- Git repository URL input field now scrolls to keep the caret visible, e.g. after pressing Backspace on the last line, the view follows the cursor instead of staying put

### Added

- MIT open source license (`LICENSE`)

### Changed

- CI release artifact is now named `bos-project-init-tool-<version>.zip` (previously fixed `plugin-artifact.zip`)
- Simplified `.gitignore` with directory-level rules instead of per-file enumeration
- Added `AGENTS.md` as the project guide; `CLAUDE.md` now points to it
- Bumped version to 1.1.3

## [1.1.2] - 2026

### Added

- Security validation and sanitization for Git URLs and repository names (`SecurityUtils` 4-layer checks)

### Changed

- Improved UI and translation wording
- Improved clone failure logging and user-facing messages

## [1.1.1] - 2026

### Added

- Support for appending `build-suffix.gradle.template` content
- Module version matching and precise dependency replacement

### Changed

- Refactored concurrent repository cloning and exception handling with thread-safe progress logging
- Simplified the Git utility module by removing redundant methods and the error classification enum
- Improved progress button interaction and styling
- Added the English README

## [1.0.2] - 2025

### Changed

- Updated the IDEA version requirement in README

## [1.0.1] - 2025

### Added

- Initial project skeleton and core functionality

[1.1.3]: https://github.com/shuiruoyan/bos-project-init-tool/compare/v1.1.2...v1.1.3
[1.1.2]: https://github.com/shuiruoyan/bos-project-init-tool/compare/v1.1.1...v1.1.2
[1.1.1]: https://github.com/shuiruoyan/bos-project-init-tool/compare/v1.0.2...v1.1.1
[1.0.2]: https://github.com/shuiruoyan/bos-project-init-tool/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/shuiruoyan/bos-project-init-tool/releases/tag/v1.0.1