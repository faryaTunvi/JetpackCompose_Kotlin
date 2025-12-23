package com.fattyleo.networkinfoapp.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.JobIntentService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SignalStrengthService : JobIntentService() {

    companion object {
        private const val JOB_ID = 1000
        const val ACTION_SIGNAL_STRENGTH_CHANGED = "com.fattyleo.networkinfoapp.ACTION_SIGNAL_STRENGTH_CHANGED"
        const val EXTRA_SIGNAL_LEVEL = "extra_signal_level"
        const val EXTRA_SIGNAL_DBM = "extra_signal_dbm"
        const val EXTRA_NETWORK_TYPE = "extra_network_type"

        fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(context, SignalStrengthService::class.java, JOB_ID, intent)
        }
    }

    override fun onHandleWork(intent: Intent) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        when (intent.action) {
            ACTION_SIGNAL_STRENGTH_CHANGED -> {
                val signalLevel = intent.getIntExtra(EXTRA_SIGNAL_LEVEL, 0)
                val signalDbm = intent.getIntExtra(EXTRA_SIGNAL_DBM, 0)
                val networkType = intent.getStringExtra(EXTRA_NETWORK_TYPE) ?: "Unknown"

                Log.d("SignalStrengthService", "[$timestamp] Signal Strength Changed: Level=$signalLevel, Dbm=$signalDbm, NetworkType=$networkType")
                // Here you can perform any background task, e.g.,
                // - Log to a file
                // - Send data to analytics
                // - Update a database
                // - Send a notification (with appropriate permissions)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SignalStrengthService", "Service Destroyed")
    }
}