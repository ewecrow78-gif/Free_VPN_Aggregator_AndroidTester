package com.ghostvpn.tester

import android.net.Network
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class CellularSocksProxy(private val port: Int, private val cellularNetwork: Network) {
    private val TAG = "CellularSocksProxy"
    private var serverSocket: ServerSocket? = null
    @Volatile private var isRunning = false

    fun start() {
        isRunning = true
        serverSocket = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
        Log.i(TAG, "SOCKS5 Proxy listening on 127.0.0.1:$port")

        while (isRunning) {
            try {
                val client = serverSocket!!.accept()
                thread { handleClient(client) }
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "Accept error: ${e.message}")
            }
        }
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (e: Exception) {}
    }

    private fun handleClient(client: Socket) {
        var targetSocket: Socket? = null
        try {
            val input = client.getInputStream()
            val output = client.getOutputStream()

            // 1. Handshake
            val version = input.read()
            if (version != 5) {
                Log.e(TAG, "Not SOCKS5 version: $version")
                client.close()
                return
            }
            val numMethods = input.read()
            val methods = ByteArray(numMethods)
            input.read(methods)
            // Respond with NO AUTH (0x00)
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            // 2. Request
            val reqVersion = input.read()
            val cmd = input.read()
            input.read() // RSV
            val atyp = input.read()

            var targetHost = ""
            if (atyp == 0x01) { // IPv4
                val addr = ByteArray(4)
                input.read(addr)
                targetHost = InetAddress.getByAddress(addr).hostAddress!!
            } else if (atyp == 0x03) { // Domain
                val len = input.read()
                val domainBytes = ByteArray(len)
                input.read(domainBytes)
                targetHost = String(domainBytes)
            } else if (atyp == 0x04) { // IPv6
                val addr = ByteArray(16)
                input.read(addr)
                targetHost = InetAddress.getByAddress(addr).hostAddress!!
            }

            val portBytes = ByteArray(2)
            input.read(portBytes)
            val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            if (cmd != 0x01) { // Only CONNECT is supported
                Log.e(TAG, "Unsupported command: $cmd")
                output.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                client.close()
                return
            }

            Log.i(TAG, "Connecting via Cellular to $targetHost:$targetPort")
            // 3. Connect via Cellular Network!
            targetSocket = cellularNetwork.socketFactory.createSocket(targetHost, targetPort)
            targetSocket.soTimeout = 0 // Infinite

            // Send Success
            output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            output.flush()

            val targetInput = targetSocket.getInputStream()
            val targetOutput = targetSocket.getOutputStream()

            // 4. Bridge streams
            val t1 = thread { bridge(input, targetOutput) }
            val t2 = thread { bridge(targetInput, output) }
            
            t1.join()
            t2.join()

        } catch (e: Exception) {
            Log.e(TAG, "Client error: ${e.message}")
        } finally {
            try { client.close() } catch (e: Exception) {}
            try { targetSocket?.close() } catch (e: Exception) {}
        }
    }

    private fun bridge(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(4096)
        try {
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (e: Exception) {
            // normal disconnect
        }
    }
}
