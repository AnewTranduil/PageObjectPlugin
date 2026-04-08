package com.github.artem.pageobjectplugin.ui.support

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Subscribes to JCEF's Chrome DevTools Protocol on `localhost:port` and buffers
 * `Runtime.consoleAPICalled` events. The plugin tool window's JCEF iframe is the
 * only active CDP target while a UI test runs, so we connect to the first listed
 * tab and stream raw JSON messages into [buffer].
 *
 * Best-effort throughout: any failure to connect, parse, or write is swallowed
 * and the consumer ends up with a missing or empty `jcef-console.log`.
 *
 * Lifecycle:
 *   - [start] from `TraceBundleExtension.beforeEach`
 *   - [flushTo] when writing the bundle
 *   - [close] (also called by [flushTo]) to release the WebSocket
 */
class CdpConsoleCollector(private val port: Int = 9222) : AutoCloseable {

    private val buffer = CopyOnWriteArrayList<String>()
    private val client = OkHttpClient()
    private var ws: WebSocket? = null

    fun start() {
        try {
            val listReq = Request.Builder().url("http://localhost:$port/json/list").build()
            val tabs = client.newCall(listReq).execute().use { it.body?.string().orEmpty() }
            // Heuristic: pick the first webSocketDebuggerUrl. The tool window is
            // the only active JCEF target during a test, so this is sufficient.
            val wsUrl = WS_URL_REGEX.findAll(tabs)
                .map { it.groupValues[1] }
                .firstOrNull() ?: return
            ws = client.newWebSocket(
                Request.Builder().url(wsUrl).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send("""{"id":1,"method":"Runtime.enable"}""")
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (text.contains("\"Runtime.consoleAPICalled\"")) {
                            buffer.add(text)
                        }
                    }
                },
            )
        } catch (_: Throwable) {
            // CDP not available — extension proceeds without a console log.
        }
    }

    /** Writes accumulated console messages to [path], one per line. */
    fun flushTo(path: Path) {
        try {
            Files.write(path, buffer.joinToString("\n").toByteArray())
        } catch (_: Throwable) {
            // best-effort
        }
    }

    override fun close() {
        try {
            ws?.close(1000, "done")
        } catch (_: Throwable) {
            // ignored
        }
    }

    companion object {
        private val WS_URL_REGEX =
            Regex("\"webSocketDebuggerUrl\"\\s*:\\s*\"(ws://[^\"]+)\"")
    }
}
