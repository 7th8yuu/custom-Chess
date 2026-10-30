package com.customchess.net

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import android.os.Build
import android.content.Intent
import java.util.UUID
import kotlin.concurrent.thread

data class BtDevice(val name: String, val address: String, val paired: Boolean)

/** Bluetooth cổ điển (RFCOMM). Các hàm cần quyền BLUETOOTH_CONNECT đã được xin ở giao diện. */
@SuppressLint("MissingPermission")
object Bt {
    val SERVICE_UUID: UUID = UUID.fromString("7c3b2c30-5a4b-4e61-9d5e-7c0e5d0c4a11")

    fun adapter(context: Context): BluetoothAdapter? =
        (context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** Quyền cần xin lúc chạy tùy phiên bản Android. */
    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        } else {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun pairedDevices(context: Context): List<BtDevice> = try {
        adapter(context)?.bondedDevices.orEmpty().map { BtDevice(it.name ?: it.address, it.address, true) }
            .sortedBy { it.name.lowercase() }
    } catch (_: SecurityException) {
        emptyList()
    }

    fun deviceOf(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    fun toDevice(d: BluetoothDevice): BtDevice =
        BtDevice(d.name ?: d.address, d.address, d.bondState == BluetoothDevice.BOND_BONDED)

    /** Máy chủ: chờ một máy khác kết nối tới. */
    class Host(
        private val adapter: BluetoothAdapter,
        private val onConnected: (StreamLink, String) -> Unit,
        private val onError: (String) -> Unit,
    ) {
        @Volatile private var server: BluetoothServerSocket? = null
        @Volatile private var running = false

        fun start() {
            running = true
            thread(isDaemon = true, name = "bt-accept") {
                try {
                    val s = adapter.listenUsingRfcommWithServiceRecord("CustomChess", SERVICE_UUID)
                    server = s
                    val sock = s.accept()
                    running = false
                    s.close()
                    val link = StreamLink(sock.inputStream, sock.outputStream) { sock.close() }
                    onConnected(link, sock.remoteDevice?.name ?: sock.remoteDevice?.address.orEmpty())
                } catch (e: Exception) {
                    if (running) onError(e.message ?: "Lỗi Bluetooth")
                }
            }
        }

        fun stop() {
            running = false
            try { server?.close() } catch (_: Exception) { }
        }
    }

    /** Máy khách: kết nối tới địa chỉ Bluetooth (gọi ngoài luồng giao diện). */
    fun connect(adapter: BluetoothAdapter, address: String): StreamLink {
        adapter.cancelDiscovery()
        val device = adapter.getRemoteDevice(address)
        val sock = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
        try {
            sock.connect()
        } catch (e: Exception) {
            try { sock.close() } catch (_: Exception) { }
            throw e
        }
        return StreamLink(sock.inputStream, sock.outputStream) { sock.close() }
    }
}
