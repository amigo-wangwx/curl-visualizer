# Curl Visualizer

基于 Kotlin Compose Desktop 构建的 curl 返回数据可视化调试工具。

## 功能特性

- 执行单个 `curl` / `curl.exe` 命令。
- 展示请求 URL、请求参数、请求 Headers、返回 Headers、返回体、stderr、退出码和耗时。
- 返回体支持一键复制。
- 返回体支持搜索、高亮、上一处/下一处、回车跳下一处和命中数量展示。
- JSON 返回体支持格式化展示。
- 支持请求历史和返回历史，数据保存在 `~/.curl-visualizer/history.json`。
- 重复请求和重复返回数据会更新记录时间，不重复插入。
- 自动记录窗口大小，配置保存在 `~/.curl-visualizer/settings.json`。

## 运行

```bash
./gradlew :app:run
```

## 下载安装

在 [GitHub Releases](https://github.com/amigo-wangwx/curl-visualizer/releases) 下载对应平台的安装包：

- macOS：下载 `CurlVisualizer-版本号.dmg`。
- Windows：优先下载 `CurlVisualizer-版本号.msi`，也可以使用 `CurlVisualizer-版本号.exe`。

macOS 首次打开未签名应用时，可能会受到 Gatekeeper 或 quarantine 隔离属性限制。可以在“系统设置 > 隐私与安全性”中允许打开，或在确认来源可信后清理隔离属性。

Windows 首次运行未签名安装包时，可能会出现 SmartScreen 提醒。确认来源为本仓库 Release 后，可以选择继续运行。

## 打包

```bash
./gradlew :app:packageDmg
./gradlew :app:packageMsi
./gradlew :app:packageExe
```

`packageDmg` 用于生成 macOS 安装包，`packageMsi` 和 `packageExe` 需要在 Windows 上运行。

构建产物默认输出到根目录 `build/app/`，不会分散生成到 `app/build/`。如果需要自定义构建输出根目录，可以通过 Gradle 属性 `build.dir` 指定。

## 安全限制

应用只执行以 `curl` 或 `curl.exe` 开头的单个命令，并会拦截 shell 组合符、命令替换和写文件相关的 curl 参数。执行过程使用 `ProcessBuilder`，不会通过 shell 展开命令。

## 隐私提醒

历史记录可能包含 token、cookie、请求参数、业务数据或返回数据。需要清理时，可以在应用内删除单条历史或清空历史。
