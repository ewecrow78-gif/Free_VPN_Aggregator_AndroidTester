package com.ghostvpn.tester.domain.tester

import android.content.Context
import android.net.Network
import android.telephony.TelephonyManager
import android.util.Log
import com.ghostvpn.tester.CellularSocksProxy
import com.ghostvpn.tester.data.model.TestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class XrayTestRunner(
    private val context: Context,
    private val cellularNetwork: Network
) {
    private val TAG = "XrayTestRunner"
    
    // Returns carrier name
    private fun getCarrierName(): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return tm.simOperatorName.ifEmpty { "Unknown" }
    }

    suspend fun runTest(configId: String, xrayConfigJson: String): TestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var isSuccess = false
        var latencyMs: Long = 0
        var bytesTransferred: Long = 0
        var errorMessage: String? = null
        
        val carrier = getCarrierName()
        
        var proxy: CellularSocksProxy? = null
        var process: Process? = null

        try {
            // 1. Extract Xray binary if not exists
            val xrayFile = File(context.filesDir, "xray")
            if (!xrayFile.exists()) {
                context.assets.open("xray").use { input ->
                    FileOutputStream(xrayFile).use { output ->
                        input.copyTo(output)
                    }
                }
                xrayFile.setExecutable(true)
            }

            // 2. Start CellularSocksProxy
            proxy = CellularSocksProxy(10810, cellularNetwork)
            Thread { proxy.start() }.start()

            // 3. Write config.json
            val configFile = File(context.filesDir, "config.json")
            configFile.writeText(xrayConfigJson)

            // 4. Run Xray via ProcessBuilder
            val pb = ProcessBuilder(xrayFile.absolutePath, "-c", configFile.absolutePath)
            pb.directory(context.filesDir)
            pb.redirectErrorStream(true)
            process = pb.start()

            delay(2000) // wait for xray to start

            // 5. Test HTTP via Xray inbound
            val client = OkHttpClient.Builder()
                .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 10809)))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val req = Request.Builder().url("http://www.google.com/generate_204").build()
            
            latencyMs = measureTimeMillis {
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    isSuccess = true
                    bytesTransferred = resp.body?.contentLength() ?: 0L
                    if (bytesTransferred < 0) bytesTransferred = 100 // estimate
                } else {
                    errorMessage = "HTTP ${resp.code}"
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Test error", e)
            errorMessage = e.message ?: "Unknown error"
        } finally {
            process?.destroy()
            proxy?.stop()
        }

        return@withContext TestResult(
            configId = configId,
            startTime = startTime,
            networkType = "CELLULAR",
            carrierName = carrier,
            isSuccess = isSuccess,
            latencyMs = latencyMs,
            bytesTransferred = bytesTransferred,
            errorMessage = errorMessage
        )
    }
}
