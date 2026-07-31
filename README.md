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

## CLI

安装本地 CLI 分发包：

```bash
./gradlew :cli:installDist
```

生成的可执行脚本位于：

```text
build/cli/install/curl-visualizer/bin/curl-visualizer
```

检测单个 URL：

```bash
./build/cli/install/curl-visualizer/bin/curl-visualizer \
  check-url https://example.com --timeout 10
```

批量检测时，输入文件每行放置一个 URL；空行会被忽略：

```bash
./build/cli/install/curl-visualizer/bin/curl-visualizer \
  check-batch input_urls.txt output_results.jsonl \
  --timeout 10 \
  --workers 8
```

`--timeout` 是每个请求的超时秒数，默认值为 `30`。批量命令的 `--workers`
用于限制并发请求数，默认值为 `4`。

CLI 退出码：

- `0`：命令执行完成；单个 URL 的 HTTP 或网络失败也会写入结果。
- `1`：命令或参数错误。
- `2`：输入或输出文件访问错误。
- `3`：运行被中断或出现无法继续执行的运行时错误。

### JSON/JSONL 协议

`check-url` 向标准输出写入一行 JSON。`check-batch` 向指定文件写入 JSONL，
每个非空输入行对应一条结果。每条结果固定包含以下字段：

```json
{
  "url": "https://example.com",
  "status": "ok",
  "statusCode": 200,
  "finalUrl": "https://example.com",
  "elapsedMillis": 123,
  "error": null
}
```

`statusCode`、`finalUrl` 和 `error` 没有值时仍会输出，值为 `null`。

`status` 的稳定取值：

- `ok`：请求得到未归入下列类别的 HTTP 响应。
- `redirect`：请求发生跳转，或最终响应仍为 `3xx`。
- `forbidden_or_login`：状态码为 `401`/`403`，或跳转后的最终路径是
  `/login`、`/signin`、`/sign-in` 及其子路径。
- `not_found`：状态码为 `404`。
- `server_error`：状态码为 `5xx`。
- `timeout`：请求超过指定超时。
- `network_error`：连接、DNS 或其他网络 I/O 错误。
- `ssl_error`：TLS/证书错误。
- `invalid_url`：不是带有效 host 的绝对 HTTP/HTTPS URL。

批量结果按请求完成顺序写入，调用方应使用 `url` 关联输入，而不应依赖行顺序。

## Library 接入

`curl-core` 是不依赖 Compose 的 JVM library。GUI、CLI 和其他 Gradle 项目都是
它的外部调用方：

- GUI 调用 core 的完整 curl 命令服务；core 自动应用 GUI 结果规则后返回包含
  headers、body、stderr 等信息的 `CurlRunResult`，因此现有调试和历史功能不变。
- CLI 调用 core 的 URL 检测服务，直接消费已经过默认规则分类的
  `CurlCheckResult`。
- 其他依赖方也调用 URL 检测服务；可以使用默认规则，或在构造 checker 时组合
  自定义规则。请求执行、批量并发和规则应用始终由 core 完成。

发布到本机 Maven 仓库：

```bash
./gradlew :curl-core:publishToMavenLocal
```

在其他 Gradle JVM/Kotlin 项目中接入：

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.amigo_wangwx.curlvisualizer:curl-core:1.1.0")
}
```

调用示例：

```kotlin
import com.amigo_wangwx.curlvisualizer.check.CurlCheckOptions
import com.amigo_wangwx.curlvisualizer.check.DefaultCurlUrlChecker
import kotlinx.coroutines.flow.collect

val checker = DefaultCurlUrlChecker()
val options = CurlCheckOptions(timeoutSeconds = 10, workers = 8)

val singleResult = checker.checkUrl("https://example.com", options)

checker.checkBatch(
    urls = listOf("https://example.com", "https://example.org"),
    options = options,
).collect { result ->
    println("${result.url}: ${result.status}")
}
```

`CurlCheckResult` 包含 `url`、`status`、`statusCode`、`finalUrl`、
`elapsedMillis` 和 `error`。单条和批量 API 返回的都是规则处理后的结果，
调用方不需要先请求再手动执行规则。

需要增加调用方特有规则时，将规则配置给 checker。特有规则会先于 core
默认规则执行，而请求和规则管线仍由 checker 内部完成：

```kotlin
import com.amigo_wangwx.curlvisualizer.check.CurlCheckPolicies
import com.amigo_wangwx.curlvisualizer.check.CurlCheckRule
import com.amigo_wangwx.curlvisualizer.check.CurlCheckStatus

val policy = CurlCheckPolicies.defaultWith(
    CurlCheckRule { facts ->
        CurlCheckStatus.FORBIDDEN_OR_LOGIN.takeIf {
            facts.finalUrl?.contains("/account-required") == true
        }
    },
)
val checkerWithCustomRules = DefaultCurlUrlChecker(policy)
val classifiedResult = checkerWithCustomRules.checkUrl("https://example.com")
```

GUI、CLI 和依赖方都只调用一次 core 服务并获得规则处理后的结果。底层请求事实
不会作为标准调用流程直接返回给任何调用方；规则处理也不会裁剪 GUI 所需的完整
响应数据。

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
