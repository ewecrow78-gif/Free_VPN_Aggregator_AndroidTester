package com.ghostvpn.tester.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ghostvpn.tester.data.local.ResultDatabase
import com.ghostvpn.tester.data.model.TestResult
import com.ghostvpn.tester.worker.WorkerSetup
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ResultDatabase.getDatabase(application).resultDao()

    val results: StateFlow<List<TestResult>> = db.getAllResults()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun startTestManually() {
        WorkerSetup.triggerNow(getApplication())
    }
}
