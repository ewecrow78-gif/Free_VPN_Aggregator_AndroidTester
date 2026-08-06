package com.ghostvpn.tester.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "test_results")
data class TestResult(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val configId: String,
    val startTime: Long,
    val networkType: String,
    val carrierName: String,
    val isSuccess: Boolean,
    val latencyMs: Long,
    val bytesTransferred: Long,
    val errorMessage: String? = null,
    val isUploaded: Boolean = false
)

data class VpnConfig(
    val id: String,
    val configUrl: String
)
