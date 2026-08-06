package com.ghostvpn.tester

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ghostvpn.tester.ui.MainViewModel
import com.ghostvpn.tester.worker.WorkerSetup
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start background periodic job on first launch
        WorkerSetup.setupPeriodicWork(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TesterAppScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun TesterAppScreen(viewModel: MainViewModel) {
    val results by viewModel.results.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "GhostVPN Cellular Tester", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { viewModel.startTestManually() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Run Test Now")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Recent Test Logs:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn {
            items(results) { result ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.isSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val date = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(result.startTime))
                        Text(text = "Time: $date | Carrier: ${result.carrierName}", style = MaterialTheme.typography.bodySmall)
                        Text(text = "Config ID: ${result.configId}", style = MaterialTheme.typography.bodyMedium)
                        if (result.isSuccess) {
                            Text(text = "Success! Ping: ${result.latencyMs}ms | Data: ${result.bytesTransferred}b")
                        } else {
                            Text(text = "Failed: ${result.errorMessage}", color = Color.Red)
                        }
                    }
                }
            }
        }
    }
}
