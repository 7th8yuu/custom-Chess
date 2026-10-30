package com.customchess.net

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Đường truyền hai chiều theo dòng văn bản, dùng chung cho TCP (Wi-Fi) và RFCOMM (Bluetooth).
 * Đọc trên một luồng riêng, ghi tuần tự trên một luồng riêng (không bao giờ chạm luồng giao diện).
 * Các callback được gọi từ luồng nền.
 */
class StreamLink(
    private val input: InputStream,
    private val output: OutputStream,
    private val closer: () -> Unit,
) {
    @Volatile private var closed = false
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "link-writer").apply { isDaemon = true } }

    @Volatile var onLine: (String) -> Unit = {}
    @Volatile var onClosed: () -> Unit = {}

    val isClosed: Boolean get() = closed

    fun start() {
        thread(isDaemon = true, name = "link-reader") {
            try {
                val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) onLine(line)
                }
            } catch (_: IOException) {
                // đường truyền đã đóng
            } finally {
                close()
            }
        }
    }

    fun send(line: String) {
        if (closed) return
        try {
            writer.execute {
                try {
                    output.write((line + "\n").toByteArray(Charsets.UTF_8))
                    output.flush()
                } catch (_: IOException) {
                    close()
                }
            }
        } catch (_: Exception) {
            // executor đã tắt
        }
    }

    fun send(m: Msg) = send(Protocol.encode(m))

    fun close() {
        if (closed) return
        closed = true
        try { closer() } catch (_: Exception) { }
        writer.shutdown()
        try { onClosed() } catch (_: Exception) { }
    }
}
