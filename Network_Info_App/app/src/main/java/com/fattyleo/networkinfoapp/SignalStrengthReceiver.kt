package com.fattyleo.networkinfoapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat

class SignalStrengthReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        telephonyManager.listen(object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                super.onSignalStrengthsChanged(signalStrength)

                val signalLevel = signalStrength.level // Get signal level (0-4)
                sendNotification(context, signalLevel)
            }
        }, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
    }

    private fun sendNotification(context: Context, signalLevel: Int) {
        // Create and show a notification when the signal level changes
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationChannelId = "signal_change_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Signal Change Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, notificationChannelId)
            .setSmallIcon(R.drawable.outline_allergy_24) // Replace with your app's icon
            .setContentTitle("Signal Strength Changed")
            .setContentText("New Signal Level: $signalLevel")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)
    }
}