package com.ghostvpn.tester

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val TAG = "GhostVPNTester"
    private lateinit var logView: TextView
    private var cellularNetwork: Network? = null
    
    // Hardcoded PoC config payload. You'd normally fetch this from API.
    // Notice dialerProxy: "cellular_socks"
    private val xrayConfigTemplate = """
    {
      "log": {
        "loglevel": "debug"
      },
      "inbounds": [
        {
          "listen": "127.0.0.1",
          "port": 10809,
          "protocol": "socks",
          "settings": {
            "udp": true
          }
        }
      ],
      "outbounds": [
        {
          "tag": "proxy",
          "protocol": "vless",
          "settings": {
            "vnext": [
              {
                "address": "1.1.1.1",
                "port": 443,
                "users": [
                  {
                    "id": "b831381d-6324-4d53-ad4f-8cda48b30811",
                    "encryption": "none"
                  }
                ]
              }
            ]
          },
          "streamSettings": {
            "network": "tcp",
            "sockopt": {
              "dialerProxy": "cellular_socks"
            }
          }
        },
        {
          "tag": "cellular_socks",
          "protocol": "socks",
          "settings": {
            "servers": [
              {
                "address": "127.0.0.1",
                "port": 10810
              }
            ]
          }
        },
        {
            "tag": "direct",
            "protocol": "freedom"
        }
      ],
      "routing": {
        "rules": [
          {
            "type": "field",
            "outboundTag": "cellular_socks",
            "port": "10810"
          }
        ]
      }
    }
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logView = TextView(this)
        setContentView(logView)
        appendLog("App started. Requesting Cellular Network...")

        requestCellularNetwork()
    }

    private fun appendLog(msg: String) {
        Log.i(TAG, msg)
        runOnUiThread {
            logView.append(msg + "\n")
        }
    }

    private fun requestCellularNetwork() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                if (cellularNetwork == null) {
                    cellularNetwork = network
                    appendLog("Cellular Network Acquired: ${network}")
                    startPoC()
                }
            }
            override fun onLost(network: Network) {
                super.onLost(network)
                appendLog("Cellular Network Lost!")
                cellularNetwork = null
            }
        })
    }

    private fun startPoC() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Extract Xray binary
                appendLog("Extracting Xray binary...")
                val xrayFile = File(filesDir, "xray")
                if (!xrayFile.exists()) {
                    assets.open("xray").use { input ->
                        FileOutputStream(xrayFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                xrayFile.setExecutable(true)
                appendLog("Xray binary ready: ${xrayFile.absolutePath}")

                // 2. Start CellularSocksProxy
                appendLog("Starting CellularSocksProxy on 10810...")
                val proxy = CellularSocksProxy(10810, cellularNetwork!!)
                Thread { proxy.start() }.start()

                // 3. Write config.json
                val configFile = File(filesDir, "config.json")
                configFile.writeText(xrayConfigTemplate)

                // 4. Run Xray via ProcessBuilder
                appendLog("Starting Xray Process...")
                val pb = ProcessBuilder(xrayFile.absolutePath, "-c", configFile.absolutePath)
                pb.directory(filesDir)
                pb.redirectErrorStream(true)
                val process = pb.start()
                
                // Read logs in background
                Thread {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { Log.d("XRAY_CORE", it) }
                    }
                }.start()

                appendLog("Xray started! Waiting 3 seconds...")
                kotlinx.coroutines.delay(3000)

                // 5. Test HTTP via Xray inbound
                appendLog("Testing connectivity through Xray...")
                val client = OkHttpClient.Builder()
                    .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 10809)))
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()

                // Google generate_204 check
                val req = Request.Builder().url("http://www.google.com/generate_204").build()
                try {
                    val resp = client.newCall(req).execute()
                    appendLog("TEST RESULT: HTTP ${resp.code}")
                } catch (e: Exception) {
                    appendLog("TEST FAILED: ${e.message}")
                }

                // Cleanup
                appendLog("Destroying Xray process...")
                process.destroy()
                proxy.stop()
                appendLog("PoC Completed.")

            } catch (e: Exception) {
                appendLog("ERROR: ${e.message}")
            }
        }
    }
}