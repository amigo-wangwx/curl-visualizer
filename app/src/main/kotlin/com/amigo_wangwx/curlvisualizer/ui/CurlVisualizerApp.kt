package com.amigo_wangwx.curlvisualizer.ui

import com.amigo_wangwx.curlvisualizer.curl.CurlCommandParser
import com.amigo_wangwx.curlvisualizer.curl.CurlDisplayInfo
import com.amigo_wangwx.curlvisualizer.curl.CurlRunResult
import com.amigo_wangwx.curlvisualizer.curl.CurlRunner
import com.amigo_wangwx.curlvisualizer.curl.HeaderLine
import com.amigo_wangwx.curlvisualizer.curl.JsonFormatter
import com.amigo_wangwx.curlvisualizer.data.history.CurlHistoryItem
import com.amigo_wangwx.curlvisualizer.data.history.HistoryState
import com.amigo_wangwx.curlvisualizer.data.history.HistoryStore
import com.amigo_wangwx.curlvisualizer.data.history.ResponseHistoryItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val historyTimeFormatter = DateTimeFormatter
    .ofPattern("MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())

/**
 * Root app theme and surface.
 *
 * The app uses a quiet developer-tool palette so response text remains the primary focus.
 */
@Composable
fun CurlVisualizerApp() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF7DD3FC),
            secondary = Color(0xFFA7F3D0),
            surface = Color(0xFF111827),
            background = Color(0xFF0B1020),
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            CurlVisualizerScreen()
        }
    }
}

/**
 * Main two-pane UI for entering curl and inspecting the response.
 *
 * State is owned here because one execution result drives status, JSON toggles, search, and clipboard behavior together.
 */
@Composable
private fun CurlVisualizerScreen() {
    val runner = remember { CurlRunner() }
    val historyStore = remember { HistoryStore() }
    val scope = rememberCoroutineScope()

    var curlText by remember {
        mutableStateOf(
            """
            curl https://httpbin.org/json
            """.trimIndent(),
        )
    }
    var result by remember { mutableStateOf<CurlRunResult?>(null) }
    var error by remember { mutableStateOf("") }
    var searchKeyword by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableStateOf(0) }
    var formatJson by remember { mutableStateOf(true) }
    var isRunning by remember { mutableStateOf(false) }
    var historyState by remember { mutableStateOf(historyStore.load()) }
    var selectedHistoryTab by remember { mutableStateOf(0) }
    var selectedDebugTab by remember { mutableStateOf(0) }

    val requestDisplayInfo = remember(curlText) { CurlCommandParser.parseDisplayInfo(curlText) }
    val rawBody = result?.response?.body.orEmpty()
    val formattedBody = remember(rawBody) { JsonFormatter.formatOrNull(rawBody) }
    val displayedBody = if (formatJson && formattedBody != null) formattedBody else rawBody
    val searchResult by remember(displayedBody, searchKeyword, currentMatchIndex) {
        derivedStateOf { SearchHighlighter.highlight(displayedBody, searchKeyword, currentMatchIndex) }
    }

    LaunchedEffect(displayedBody, searchKeyword) {
        currentMatchIndex = 0
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RequestPane(
            curlText = curlText,
            isRunning = isRunning,
            historyState = historyState,
            selectedHistoryTab = selectedHistoryTab,
            onCurlTextChange = { curlText = it },
            onHistoryTabChange = { selectedHistoryTab = it },
            onLoadRequest = { item ->
                curlText = item.command
                item.responseId
                    ?.let { responseId -> historyState.responses.firstOrNull { it.id == responseId } }
                    ?.let { response ->
                        result = historyStore.toRunResult(response)
                        error = ""
                    }
            },
            onLoadResponse = { item ->
                result = historyStore.toRunResult(item)
                error = ""
            },
            onDeleteRequest = {
                historyState = historyStore.deleteRequest(it.id)
            },
            onDeleteResponse = {
                historyState = historyStore.deleteResponse(it.id)
                if (result?.response?.body == it.body) {
                    result = null
                }
            },
            onClearRequests = {
                historyState = historyStore.clearRequests()
            },
            onClearResponses = {
                historyState = historyStore.clearResponses()
                result = null
            },
            onRun = {
                scope.launch {
                    isRunning = true
                    error = ""
                    runCatching {
                        runner.run(curlText)
                    }.onSuccess {
                        result = it
                        historyState = historyStore.record(curlText, it)
                    }.onFailure {
                        error = it.message ?: it.toString()
                    }
                    isRunning = false
                }
            },
        )

        ResponsePane(
            result = result,
            requestDisplayInfo = requestDisplayInfo,
            error = error,
            isRunning = isRunning,
            searchKeyword = searchKeyword,
            searchResult = searchResult,
            selectedDebugTab = selectedDebugTab,
            formatJson = formatJson,
            canFormatJson = formattedBody != null,
            displayedBody = displayedBody,
            onDebugTabChange = { selectedDebugTab = it },
            onSearchChange = { searchKeyword = it },
            onPreviousMatch = {
                if (searchResult.count > 0) {
                    currentMatchIndex =
                        if (searchResult.currentIndex <= 0) searchResult.count - 1 else searchResult.currentIndex - 1
                }
            },
            onNextMatch = {
                if (searchResult.count > 0) {
                    currentMatchIndex = (searchResult.currentIndex + 1) % searchResult.count
                }
            },
            onFormatJsonChange = { formatJson = it },
            onCopy = {
                copyToClipboard(displayedBody)
            },
        )
    }
}

/**
 * Left pane for curl command editing and execution.
 *
 * It keeps the primary action near the input because each run starts from this pane.
 */
@Composable
private fun RequestPane(
    curlText: String,
    isRunning: Boolean,
    historyState: HistoryState,
    selectedHistoryTab: Int,
    onCurlTextChange: (String) -> Unit,
    onHistoryTabChange: (Int) -> Unit,
    onLoadRequest: (CurlHistoryItem) -> Unit,
    onLoadResponse: (ResponseHistoryItem) -> Unit,
    onDeleteRequest: (CurlHistoryItem) -> Unit,
    onDeleteResponse: (ResponseHistoryItem) -> Unit,
    onClearRequests: () -> Unit,
    onClearResponses: () -> Unit,
    onRun: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(430.dp)
            .fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
        ) {
            Text(
                text = "Curl 输入",
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = curlText,
                onValueChange = onCurlTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                placeholder = { Text("粘贴 curl 命令") },
            )
            Spacer(Modifier.height(12.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunning,
                onClick = onRun,
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .width(18.dp)
                            .height(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("执行中")
                } else {
                    Text("执行 curl")
                }
            }
            Spacer(Modifier.height(12.dp))
            HistoryPane(
                modifier = Modifier.weight(1f),
                historyState = historyState,
                selectedHistoryTab = selectedHistoryTab,
                onHistoryTabChange = onHistoryTabChange,
                onLoadRequest = onLoadRequest,
                onLoadResponse = onLoadResponse,
                onDeleteRequest = onDeleteRequest,
                onDeleteResponse = onDeleteResponse,
                onClearRequests = onClearRequests,
                onClearResponses = onClearResponses,
            )
        }
    }
}

/**
 * Right pane for status, tools, response body, headers, and errors.
 *
 * The response body stays selectable while search highlighting remains visible.
 */
@Composable
private fun ResponsePane(
    result: CurlRunResult?,
    requestDisplayInfo: CurlDisplayInfo,
    error: String,
    isRunning: Boolean,
    searchKeyword: String,
    searchResult: SearchResult,
    selectedDebugTab: Int,
    formatJson: Boolean,
    canFormatJson: Boolean,
    displayedBody: String,
    onDebugTabChange: (Int) -> Unit,
    onSearchChange: (String) -> Unit,
    onPreviousMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onFormatJsonChange: (Boolean) -> Unit,
    onCopy: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
        ) {
            RequestSummaryPanel(
                result = result,
                requestDisplayInfo = requestDisplayInfo,
                hasBody = displayedBody.isNotEmpty(),
                onCopy = onCopy,
            )
            Spacer(Modifier.height(12.dp))
            ResponseToolbar(
                searchKeyword = searchKeyword,
                searchCount = searchResult.count,
                currentSearchIndex = searchResult.currentIndex,
                formatJson = formatJson,
                canFormatJson = canFormatJson,
                onSearchChange = onSearchChange,
                onPreviousMatch = onPreviousMatch,
                onNextMatch = onNextMatch,
                onFormatJsonChange = onFormatJsonChange,
            )
            Spacer(Modifier.height(12.dp))
            SectionHeader(title = "返回体")
            Spacer(Modifier.height(8.dp))
            ResponseBody(
                modifier = Modifier.weight(1f),
                text = if (isRunning) AnnotatedString("正在执行 curl...") else searchResult.text,
                currentMatch = searchResult.currentMatch,
            )

            val stderr = result?.stderr.orEmpty()
            Spacer(Modifier.height(10.dp))
            DebugPanel(
                result = result,
                requestDisplayInfo = requestDisplayInfo,
                error = error,
                stderr = stderr,
                selectedTab = selectedDebugTab,
                onTabChange = onDebugTabChange,
            )
        }
    }
}

/**
 * Shows the parsed request identity and response status above the response tools.
 *
 * Keeping URL, request counts, and run status together makes the current debugging context selectable and scannable.
 */
@Composable
private fun RequestSummaryPanel(
    result: CurlRunResult?,
    requestDisplayInfo: CurlDisplayInfo,
    hasBody: Boolean,
    onCopy: () -> Unit,
) {
    val bodySize = requestDisplayInfo.requestBody.toByteArray().size
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF172033), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusPill(requestDisplayInfo.method)
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    text = requestDisplayInfo.url,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE5E7EB),
                    maxLines = 4,
                )
            }
            OutlinedButton(
                enabled = hasBody,
                onClick = onCopy,
            ) {
                Text("复制返回")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill("Headers ${requestDisplayInfo.headers.size}")
            StatusPill("Params ${requestDisplayInfo.queryParams.size}")
            StatusPill(if (bodySize > 0) "Body $bodySize B" else "Body 0 B")
            Spacer(Modifier.weight(1f))
            StatusPill(result?.response?.statusLine ?: "等待执行")
            StatusPill("exit ${result?.exitCode ?: "-"}")
            StatusPill("${result?.elapsedMillis ?: 0} ms")
        }
    }
}

/**
 * Small section label used inside the response workspace.
 *
 * The compact label separates tool chrome from selectable payload text without stealing much space.
 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF94A3B8),
    )
}

/**
 * Bottom inspector for secondary request and response details.
 *
 * Tabs keep the response body large while still making headers, request body, and errors available in one place.
 */
@Composable
private fun DebugPanel(
    result: CurlRunResult?,
    requestDisplayInfo: CurlDisplayInfo,
    error: String,
    stderr: String,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
) {
    val requestParamCount = requestDisplayInfo.queryParams.size +
        if (requestDisplayInfo.requestBody.isNotBlank()) 1 else 0
    val errorCount = listOf(error, stderr).count { it.isNotBlank() }
    val tabs = listOf(
        "请求参数 $requestParamCount",
        "请求 Headers ${requestDisplayInfo.headers.size}",
        "返回 Headers ${result?.response?.headers.orEmpty().size}",
        "错误 $errorCount",
    )
    val text = when (selectedTab) {
        0 -> requestParamsText(requestDisplayInfo)
        1 -> requestHeadersText(requestDisplayInfo.headers)
        2 -> responseHeadersText(result)
        else -> listOf(error, stderr).filter { it.isNotBlank() }.joinToString("\n").ifBlank { "无错误输出" }
    }
    val isErrorTab = selectedTab == 3 && (error.isNotBlank() || stderr.isNotBlank())

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEachIndexed { index, title ->
                HistoryTabButton(
                    selected = selectedTab == index,
                    text = title,
                    onClick = { onTabChange(index) },
                )
            }
        }
        InspectorTextPanel(
            text = text,
            isError = isErrorTab,
        )
    }
}

/**
 * Compact response toolbar with body-focused tools.
 *
 * Search count is shown beside the field so long responses can be inspected without extra dialogs.
 */
@Composable
private fun ResponseToolbar(
    searchKeyword: String,
    searchCount: Int,
    currentSearchIndex: Int,
    formatJson: Boolean,
    canFormatJson: Boolean,
    onSearchChange: (String) -> Unit,
    onPreviousMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onFormatJsonChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = searchKeyword,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent {
                        // 搜索框回车用于跳到下一处，避免用户在长响应里反复点按钮。
                        if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) {
                            onNextMatch()
                            true
                        } else {
                            false
                        }
                    },
                singleLine = true,
                label = { Text("搜索返回数据") },
                supportingText = {
                    Text(searchSummary(searchKeyword, searchCount, currentSearchIndex))
                },
            )
            OutlinedButton(
                enabled = searchCount > 0,
                onClick = onPreviousMatch,
            ) {
                Text("上一处")
            }
            OutlinedButton(
                enabled = searchCount > 0,
                onClick = onNextMatch,
            ) {
                Text("下一处")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = formatJson && canFormatJson,
                    enabled = canFormatJson,
                    onCheckedChange = onFormatJsonChange,
                )
                TextButton(
                    enabled = canFormatJson,
                    onClick = { onFormatJsonChange(!formatJson) },
                ) {
                    Text("JSON 格式化")
                }
            }
        }
    }
}

/**
 * Small status chip for response metadata.
 *
 * Fixed padding keeps the toolbar stable while status text changes between executions.
 */
@Composable
private fun StatusPill(text: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFF1F2937), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text = text, fontSize = 13.sp, color = Color(0xFFE5E7EB))
    }
}

/**
 * Scrollable response body area.
 *
 * Horizontal and vertical scroll states are separate because API responses often contain long JSON lines.
 */
@Composable
private fun ResponseBody(
    modifier: Modifier = Modifier,
    text: AnnotatedString,
    currentMatch: SearchMatch?,
) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    LaunchedEffect(text.text) {
        horizontalScroll.scrollTo(0)
    }
    LaunchedEffect(currentMatch) {
        val line = currentMatch
            ?.let { match -> text.text.take(match.start).count { it == '\n' } }
            ?: 0
        verticalScroll.animateScrollTo((line * 19).coerceAtLeast(0))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0B1020), RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        SelectionContainer {
            Text(
                text = text,
                modifier = Modifier
                    .verticalScroll(verticalScroll)
                    .horizontalScroll(horizontalScroll),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Color(0xFFE5E7EB),
            )
        }
    }
}

/**
 * Left pane history list for requests and saved response bodies.
 *
 * The history is placed below the editor so repeated debugging can reuse previous inputs without leaving the screen.
 */
@Composable
private fun HistoryPane(
    modifier: Modifier,
    historyState: HistoryState,
    selectedHistoryTab: Int,
    onHistoryTabChange: (Int) -> Unit,
    onLoadRequest: (CurlHistoryItem) -> Unit,
    onLoadResponse: (ResponseHistoryItem) -> Unit,
    onDeleteRequest: (CurlHistoryItem) -> Unit,
    onDeleteResponse: (ResponseHistoryItem) -> Unit,
    onClearRequests: () -> Unit,
    onClearResponses: () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryTabButton(
                selected = selectedHistoryTab == 0,
                text = "请求历史 ${historyState.requests.size}",
                onClick = { onHistoryTabChange(0) },
            )
            HistoryTabButton(
                selected = selectedHistoryTab == 1,
                text = "返回历史 ${historyState.responses.size}",
                onClick = { onHistoryTabChange(1) },
            )
        }
        Spacer(Modifier.height(8.dp))
        if (selectedHistoryTab == 0) {
            RequestHistoryList(
                modifier = Modifier.weight(1f),
                items = historyState.requests,
                onLoad = onLoadRequest,
                onDelete = onDeleteRequest,
                onClear = onClearRequests,
            )
        } else {
            ResponseHistoryList(
                modifier = Modifier.weight(1f),
                items = historyState.responses,
                onLoad = onLoadResponse,
                onDelete = onDeleteResponse,
                onClear = onClearResponses,
            )
        }
    }
}

/**
 * Toggle button for switching history categories.
 *
 * Filled and outlined states make the current category visible without adding another navigation component.
 */
@Composable
private fun HistoryTabButton(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(text)
        }
    }
}

/**
 * Request history list with rerun-ready curl commands.
 *
 * Clicking an item fills the editor, and linked response history is shown immediately when available.
 */
@Composable
private fun RequestHistoryList(
    modifier: Modifier,
    items: List<CurlHistoryItem>,
    onLoad: (CurlHistoryItem) -> Unit,
    onDelete: (CurlHistoryItem) -> Unit,
    onClear: () -> Unit,
) {
    HistoryListShell(
        modifier = modifier,
        isEmpty = items.isEmpty(),
        emptyText = "暂无请求历史",
        onClear = onClear,
    ) {
        items(items, key = { it.id }) { item ->
            val displayInfo = CurlCommandParser.parseDisplayInfo(item.command)
            RequestHistoryRow(
                method = displayInfo.method,
                url = displayInfo.url,
                subtitle = "${formatTime(item.executedAtMillis)}  ${item.statusLine.ifBlank { "no status" }}  exit ${item.exitCode}",
                detail = "${item.elapsedMillis} ms",
                onClick = { onLoad(item) },
                onDelete = { onDelete(item) },
            )
        }
    }
}

/**
 * Response history list with saved response bodies.
 *
 * Clicking an item loads the saved body into the response panel without executing curl.
 */
@Composable
private fun ResponseHistoryList(
    modifier: Modifier,
    items: List<ResponseHistoryItem>,
    onLoad: (ResponseHistoryItem) -> Unit,
    onDelete: (ResponseHistoryItem) -> Unit,
    onClear: () -> Unit,
) {
    HistoryListShell(
        modifier = modifier,
        isEmpty = items.isEmpty(),
        emptyText = "暂无返回历史",
        onClear = onClear,
    ) {
        items(items, key = { it.id }) { item ->
            HistoryRow(
                title = responsePreview(item.body),
                subtitle = "${formatTime(item.updatedAtMillis)}  ${item.statusLine.ifBlank { "no status" }}  exit ${item.exitCode}",
                detail = "${item.body.toByteArray().size / 1024} KB",
                onClick = { onLoad(item) },
                onDelete = { onDelete(item) },
            )
        }
    }
}

/**
 * Shared history list shell with an optional clear action.
 *
 * It keeps request and response history visually consistent while preserving separate delete behavior.
 */
@Composable
private fun HistoryListShell(
    modifier: Modifier,
    isEmpty: Boolean,
    emptyText: String,
    onClear: () -> Unit,
    content: LazyColumnScopeContent,
) {
    val listState = rememberLazyListState()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "历史可能包含 token、cookie 或业务数据",
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
            )
            TextButton(
                enabled = !isEmpty,
                onClick = onClear,
            ) {
                Text("清空")
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF0B1020), RoundedCornerShape(8.dp))
                .padding(8.dp),
        ) {
            if (isEmpty) {
                Text(
                    text = emptyText,
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF94A3B8),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(end = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    content()
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(10.dp)
                        .background(Color(0xFF172033), RoundedCornerShape(999.dp)),
                )
                VerticalScrollbar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(10.dp),
                    adapter = rememberScrollbarAdapter(listState),
                )
            }
        }
    }
}

private typealias LazyColumnScopeContent = androidx.compose.foundation.lazy.LazyListScope.() -> Unit

/**
 * Request history row focused on the full request URL.
 *
 * Clicking the row restores the original curl command in the editor, so the compact row does not repeat every flag.
 */
@Composable
private fun RequestHistoryRow(
    method: String,
    url: String,
    subtitle: String,
    detail: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF172033), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = method,
                modifier = Modifier
                    .background(Color(0xFF0B1020), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF7DD3FC),
                maxLines = 1,
            )
            Text(
                text = url,
                modifier = Modifier.weight(1f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE5E7EB),
                maxLines = 5,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = subtitle,
                modifier = Modifier.weight(1f),
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
            )
            Text(
                text = detail,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFCBD5E1),
            )
            TextButton(onClick = onDelete) {
                Text("删除")
            }
        }
    }
}

/**
 * One clickable history row.
 *
 * Delete is separate from row click so accidental cleanup is less likely during repeated debugging.
 */
@Composable
private fun HistoryRow(
    title: String,
    subtitle: String,
    detail: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF172033), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFE5E7EB),
            maxLines = 2,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = subtitle,
                modifier = Modifier.weight(1f),
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
            )
            Text(
                text = detail,
                fontSize = 11.sp,
                color = Color(0xFFCBD5E1),
            )
            TextButton(onClick = onDelete) {
                Text("删除")
            }
        }
    }
}

/**
 * Shows the parsed response headers.
 *
 * Header content is secondary, so it is capped to a compact block below the body.
 */
@Composable
private fun HeaderPanel(result: CurlRunResult?) {
    val headerText = buildString {
        appendLine(result?.response?.statusLine.orEmpty())
        result?.response?.headers.orEmpty().forEach {
            appendLine("${it.name}: ${it.value}")
        }
    }.trim()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(105.dp)
            .background(Color(0xFF172033), RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        SelectionContainer {
            Text(
                text = headerText,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color(0xFFCBD5E1),
            )
        }
    }
}

/**
 * Shows validation errors and stderr from curl.
 *
 * stderr is kept visible because TLS, DNS, and proxy failures usually arrive there instead of stdout.
 */
@Composable
private fun ErrorPanel(error: String, stderr: String) {
    val text = listOf(error, stderr)
        .filter { it.isNotBlank() }
        .joinToString("\n")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF3B1D2A), RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        SelectionContainer {
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color(0xFFFECACA),
            )
        }
    }
}

/**
 * Shared compact text panel for the bottom inspector.
 *
 * Fixed height keeps response body space predictable while still allowing selection inside debug details.
 */
@Composable
private fun InspectorTextPanel(
    text: String,
    isError: Boolean,
) {
    val background = if (isError) Color(0xFF3B1D2A) else Color(0xFF172033)
    val foreground = if (isError) Color(0xFFFECACA) else Color(0xFFCBD5E1)

    OutlinedTextField(
        value = text,
        onValueChange = {},
        readOnly = true,
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = foreground,
        ),
        colors = TextFieldDefaults.colors(
            focusedTextColor = foreground,
            unfocusedTextColor = foreground,
            focusedContainerColor = background,
            unfocusedContainerColor = background,
            focusedIndicatorColor = background,
            unfocusedIndicatorColor = background,
            cursorColor = foreground,
        ),
    )
}

/**
 * Formats URL query parameters and curl data payload as one request-parameter view.
 *
 * Query params and body are grouped separately because both shape the request but come from different curl locations.
 */
private fun requestParamsText(requestDisplayInfo: CurlDisplayInfo): String {
    val queryText = if (requestDisplayInfo.queryParams.isEmpty()) {
        "未检测到 query params"
    } else {
        requestDisplayInfo.queryParams.joinToString("\n") {
            "${it.name.padEnd(18)} ${it.value}"
        }
    }
    val bodyText = requestDisplayInfo.requestBody.ifBlank { "未检测到 body" }

    return buildString {
        appendLine("Query Params")
        appendLine(queryText)
        appendLine()
        appendLine("Body")
        append(bodyText)
    }
}

/**
 * Formats parsed response headers for the debug inspector.
 *
 * The status line stays first so copied header details retain the original HTTP context.
 */
private fun responseHeadersText(result: CurlRunResult?): String {
    val text = buildString {
        appendLine(result?.response?.statusLine.orEmpty())
        result?.response?.headers.orEmpty().forEach {
            appendLine("${it.name}: ${it.value}")
        }
    }.trim()
    return text.ifBlank { "暂无返回 Headers" }
}

/**
 * Formats parsed request headers for the debug inspector.
 *
 * The parser keeps repeated headers as separate lines because duplicate header names can affect server behavior.
 */
private fun requestHeadersText(headers: List<HeaderLine>): String {
    return headers
        .joinToString("\n") { "${it.name}: ${it.value}" }
        .ifBlank { "未检测到请求 Headers" }
}

/**
 * Copies response text through the desktop clipboard.
 *
 * AWT clipboard avoids deprecated Compose clipboard APIs and works for the current JVM desktop target.
 */
private fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit()
        .systemClipboard
        .setContents(StringSelection(text), null)
}

/**
 * Formats search count and current match position for the toolbar.
 *
 * Blank keywords intentionally hide counts because no search is active.
 */
private fun searchSummary(
    keyword: String,
    searchCount: Int,
    currentSearchIndex: Int,
): String {
    if (keyword.isBlank()) return " "
    if (searchCount <= 0) return "命中 0 处"
    return "${currentSearchIndex + 1} / $searchCount"
}

/**
 * Formats history timestamps in local time.
 *
 * Compact timestamps keep rows scannable while still showing whether duplicate data only refreshed time.
 */
private fun formatTime(millis: Long): String {
    return historyTimeFormatter.format(Instant.ofEpochMilli(millis))
}

/**
 * Builds a short response preview for the response history list.
 *
 * Newlines are collapsed so multi-line JSON does not make one row dominate the history pane.
 */
private fun responsePreview(body: String): String {
    return body
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "(empty body)" }
        .take(160)
}
