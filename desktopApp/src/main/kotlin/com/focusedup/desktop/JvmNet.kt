package com.focusedup.desktop

import com.focusedup.shared.platform.HttpResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL
import java.net.URLDecoder

/** Plain-JVM networking used for Notion (same on Android and desktop). */
object JvmNet {
    suspend fun http(method: String, url: String, headers: Map<String, String>, body: String?): HttpResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = method
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
                if (body != null) {
                    c.doOutput = true
                    c.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = c.responseCode
                val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                c.disconnect()
                HttpResult(code, text)
            }.getOrElse { HttpResult(0, "") }
        }

    /**
     * Listens on localhost:[port], runs [openBrowser], and returns the query parameters of the first
     * request to /notion (the sign-in redirect). Gives up after 5 minutes.
     */
    suspend fun awaitRedirect(port: Int, openBrowser: () -> Unit): Map<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            ServerSocket().use { server ->
                server.reuseAddress = true
                server.bind(InetSocketAddress(port))
                server.soTimeout = 5 * 60_000
                withContext(Dispatchers.Main) { openBrowser() }
                while (true) {
                    server.accept().use { s ->
                        if (!s.inetAddress.isLoopbackAddress) return@use
                        val line = s.getInputStream().bufferedReader().readLine().orEmpty()
                        val path = line.split(" ").getOrNull(1).orEmpty()
                        val out = s.getOutputStream()
                        if (!path.startsWith("/notion")) {
                            out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                            return@use
                        }
                        val html = "<html><body style='background:#03050A;color:#fff;font-family:sans-serif;text-align:center;padding-top:80px'>" +
                            "<h2>You're connected</h2><p>Go back to Focused Up.</p></body></html>"
                        val bytes = html.toByteArray()
                        out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        out.write(bytes)
                        out.flush()
                        return@runCatching path.substringAfter('?', "").split('&').filter { '=' in it }.associate {
                            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
                        }
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                null
            }
        }.getOrNull()
    }
}
