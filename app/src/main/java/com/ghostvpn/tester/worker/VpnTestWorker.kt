package com.ghostvpn.tester.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ghostvpn.tester.data.local.ResultDatabase
import com.ghostvpn.tester.domain.tester.XrayTestRunner
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class VpnTestWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val TAG = "VpnTestWorker"

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting VPN background test...")

        val cellularNetwork = waitForCellularNetwork(context)
        if (cellularNetwork == null) {
            Log.e(TAG, "No cellular network available")
            return Result.retry()
        }

        val runner = XrayTestRunner(context, cellularNetwork)
        val db = ResultDatabase.getDatabase(context).resultDao()

        // Mock getting config
        val mockConfigJson = """
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

        Log.i(TAG, "Running test for config: mock_config_1")
        val result = runner.runTest("mock_config_1", mockConfigJson)
        
        Log.i(TAG, "Test result: ${result.isSuccess} latency: ${result.latencyMs}ms")
        db.insert(result)

        // Mock upload logic
        val unuploaded = db.getUnuploadedResults()
        if (unuploaded.isNotEmpty()) {
            Log.i(TAG, "Uploading ${unuploaded.size} results...")
            // Here you would call AggregatorApi.submitResults(unuploaded)
            db.markAsUploaded(unuploaded.map { it.id })
            db.deleteUploaded()
        }

        return Result.success()
    }

    private suspend fun waitForCellularNetwork(context: Context): Network? = suspendCancellableCoroutine { cont ->
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        var resumed = false
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!resumed) {
                    resumed = true
                    cont.resume(network)
                    connectivityManager.unregisterNetworkCallback(this)
                }
            }
        }

        connectivityManager.requestNetwork(request, callback)

        cont.invokeOnCancellation {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
}
