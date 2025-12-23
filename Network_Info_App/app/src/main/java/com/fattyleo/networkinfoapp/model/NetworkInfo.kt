package com.fattyleo.networkinfoapp.model

import java.util.Date // Import Date for timestamp

data class NetworkInfo(
    val signalStrengthLevel: Int = 0, // 0-4 "bars" usually
    val signalStrengthDbm: Int = 0,   // dBm value
    val networkType: String = "Unknown",
    val frequency: String = "Unknown/Restricted", // Limited access for 3rd party apps
    val bandwidth: String = "Unknown/Restricted", // Limited access for 3rd party apps
    val mcc: String = "Unknown",
    val mnc: String = "Unknown",
    val roamingStatus: String = "Unknown",
    val isVolteCapable: String = "Unknown", // Inferred
    val operatorName: String = "Unknown",
    val subscriptionId: Int = -1,
    val cellId: Int? = null,
    val lac: Int? = null, // Location Area Code (for 2G/3G)
    val tac: Int? = null, // Tracking Area Code (for LTE)
    val arfcn: Int? = null, // Absolute RF Channel Number (2G/3G)
    val earfcn: Int? = null, // E-UTRA Absolute RF Channel Number (LTE)
    val nrarfcn: Int? = null, // New Radio Absolute RF Channel Number (5G NR)
    // New fields for call status
    val callState: String = "Idle", // e.g., "Idle", "Ringing", "Off-hook"
    val lastCallEventTimestamp: Date? = null // Timestamp of the last call state change
)