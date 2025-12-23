package com.fattyleo.networkinfoapp.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.PhoneStateListener
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.telephony.SignalStrength
import androidx.core.content.ContextCompat
import com.fattyleo.networkinfoapp.model.NetworkInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoCdma
import com.fattyleo.networkinfoapp.service.SignalStrengthService
import java.util.concurrent.Executor
import java.util.Date

class NetworkInfoRepository(private val context: Context) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
    private val _networkInfo = MutableStateFlow(NetworkInfo())
    val networkInfo: StateFlow<NetworkInfo> = _networkInfo

    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: TelephonyCallback? = null
    private val executor = Executor { command -> command.run() }

    // Store the last reported signal level for service firing
    private var lastSignalStrengthLevel: Int = -1

    init {
        fetchNetworkInfo()
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (!hasRequiredPermissions()) {
            Log.w("NetworkInfoRepo", "Permissions not granted. Cannot start listening for network changes.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback = object : TelephonyCallback(),
                TelephonyCallback.SignalStrengthsListener,
                TelephonyCallback.DisplayInfoListener,
                TelephonyCallback.CellInfoListener,
                TelephonyCallback.CallStateListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    Log.d("NetworkInfoRepo", "onSignalStrengthsChanged: ${signalStrength.level}")
                    fetchNetworkInfo() // Now fetchNetworkInfo will determine signal strength from CellInfo
                }

                override fun onDisplayInfoChanged(displayInfo: android.telephony.TelephonyDisplayInfo) {
                    Log.d("NetworkInfoRepo", "onDisplayInfoChanged: ${displayInfo.overrideNetworkType}")
                    fetchNetworkInfo()
                }

                override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
                    Log.d("NetworkInfoRepo", "onCellInfoChanged: ${cellInfo.size} cells")
                    fetchNetworkInfo()
                }

                override fun onCallStateChanged(state: Int) {
                    val callStateName = when (state) {
                        TelephonyManager.CALL_STATE_IDLE -> "Idle"
                        TelephonyManager.CALL_STATE_RINGING -> "Ringing"
                        TelephonyManager.CALL_STATE_OFFHOOK -> "Off-hook"
                        else -> "Unknown"
                    }
                    Log.d("NetworkInfoRepo", "onCallStateChanged: $callStateName")
                    _networkInfo.value = _networkInfo.value.copy(
                        callState = callStateName,
                        lastCallEventTimestamp = Date()
                    )
                    if (state == TelephonyManager.CALL_STATE_IDLE) {
                        fetchNetworkInfo()
                    }
                }
            }
            telephonyManager.registerTelephonyCallback(executor, telephonyCallback!!)
            Log.d("NetworkInfoRepo", "Registered TelephonyCallback")
        } else {
            phoneStateListener = object : PhoneStateListener() {
                @Deprecated("Deprecated in API 30")
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                    Log.d("NetworkInfoRepo", "onSignalStrengthsChanged (deprecated): ${signalStrength?.level}")
                    fetchNetworkInfo() // Now fetchNetworkInfo will determine signal strength from CellInfo
                }

                @Deprecated("Deprecated in API 30")
                override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
                    Log.d("NetworkInfoRepo", "onCellInfoChanged (deprecated): ${cellInfo?.size} cells")
                    fetchNetworkInfo()
                }

                @Deprecated("Deprecated in API 30")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    val callStateName = when (state) {
                        TelephonyManager.CALL_STATE_IDLE -> "Idle"
                        TelephonyManager.CALL_STATE_RINGING -> "Ringing"
                        TelephonyManager.CALL_STATE_OFFHOOK -> "Off-hook"
                        else -> "Unknown"
                    }
                    Log.d("NetworkInfoRepo", "onCallStateChanged (deprecated): $callStateName")
                    _networkInfo.value = _networkInfo.value.copy(
                        callState = callStateName,
                        lastCallEventTimestamp = Date()
                    )
                    if (state == TelephonyManager.CALL_STATE_IDLE) {
                        fetchNetworkInfo()
                    }
                }
            }
            telephonyManager.listen(
                phoneStateListener,
                PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or
                        PhoneStateListener.LISTEN_CELL_INFO or
                        PhoneStateListener.LISTEN_CALL_STATE
            )
            Log.d("NetworkInfoRepo", "Registered PhoneStateListener (deprecated)")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let {
                telephonyManager.unregisterTelephonyCallback(it)
                Log.d("NetworkInfoRepo", "Unregistered TelephonyCallback")
            }
            telephonyCallback = null
        } else {
            phoneStateListener?.let {
                telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
                Log.d("NetworkInfoRepo", "Unregistered PhoneStateListener")
            }
            phoneStateListener = null
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchNetworkInfo() { // Removed currentSignalStrength parameter
        if (!hasRequiredPermissions()) {
            _networkInfo.value = NetworkInfo(
                signalStrengthLevel = 0,
                signalStrengthDbm = 0,
                networkType = "Permission Denied",
                mcc = "Permission Denied",
                mnc = "Permission Denied",
                roamingStatus = "Permission Denied",
                isVolteCapable = "Permission Denied",
                operatorName = "Permission Denied",
                frequency = "Restricted/Permission Denied",
                bandwidth = "Restricted/Permission Denied",
                callState = _networkInfo.value.callState,
                lastCallEventTimestamp = _networkInfo.value.lastCallEventTimestamp
            )
            return
        }

        var currentInfo = _networkInfo.value.copy()

        // 1. General Network Type
        currentInfo = currentInfo.copy(networkType = getNetworkTypeName(telephonyManager.dataNetworkType))

        // 2. MCC, MNC, Operator Name, Roaming Status
        val networkOperator = telephonyManager.networkOperator
        if (networkOperator != null && networkOperator.length >= 5) {
            currentInfo = currentInfo.copy(
                mcc = networkOperator.substring(0, 3),
                mnc = networkOperator.substring(3)
            )
        }
        currentInfo = currentInfo.copy(
            roamingStatus = if (telephonyManager.isNetworkRoaming) "Roaming" else "Not Roaming",
            operatorName = telephonyManager.networkOperatorName
        )

        // 3. Subscription ID (Primary SIM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val activeSubs = subscriptionManager.getActiveSubscriptionInfoList()
                if (!activeSubs.isNullOrEmpty()) {
                    currentInfo = currentInfo.copy(subscriptionId = activeSubs[0].subscriptionId)
                } else {
                    currentInfo = currentInfo.copy(subscriptionId = -1)
                }
            } catch (e: Exception) {
                Log.e("NetworkInfoRepo", "Error getting default data subscription ID: ${e.message}")
            }
        }

        // 4. VoLTE Info (Inferred)
        currentInfo = currentInfo.copy(isVolteCapable = getVoLTEStatus(telephonyManager))

        // 5. Cell Info (for Signal Strength, Cell ID, LAC/TAC, ARFCNs)
        try {
            val cellInfoList: List<CellInfo>? = telephonyManager.allCellInfo
            cellInfoList?.firstOrNull { it.isRegistered }?.let { cellInfo ->
                val signalDbm: Int
                val signalLevel: Int

                when (cellInfo) {
                    is CellInfoLte -> {
                        signalDbm = cellInfo.cellSignalStrength.dbm
                        signalLevel = cellInfo.cellSignalStrength.level
                        currentInfo = currentInfo.copy(
                            cellId = cellInfo.cellIdentity.ci,
                            tac = cellInfo.cellIdentity.tac,
                            earfcn = cellInfo.cellIdentity.earfcn,
                            frequency = "LTE (EARFCN: ${cellInfo.cellIdentity.earfcn})",
                            networkType = getNetworkTypeName(TelephonyManager.NETWORK_TYPE_LTE)
                        )
                    }
                    is CellInfoWcdma -> {
                        signalDbm = cellInfo.cellSignalStrength.dbm
                        signalLevel = cellInfo.cellSignalStrength.level
                        currentInfo = currentInfo.copy(
                            cellId = cellInfo.cellIdentity.cid,
                            lac = cellInfo.cellIdentity.lac,
                            arfcn = cellInfo.cellIdentity.uarfcn,
                            frequency = "WCDMA (UARFCN: ${cellInfo.cellIdentity.uarfcn})",
                            networkType = getNetworkTypeName(TelephonyManager.NETWORK_TYPE_UMTS)
                        )
                    }
                    is CellInfoGsm -> {
                        signalDbm = cellInfo.cellSignalStrength.dbm
                        signalLevel = cellInfo.cellSignalStrength.level
                        currentInfo = currentInfo.copy(
                            cellId = cellInfo.cellIdentity.cid,
                            lac = cellInfo.cellIdentity.lac,
                            arfcn = cellInfo.cellIdentity.arfcn,
                            frequency = "GSM (ARFCN: ${cellInfo.cellIdentity.arfcn})",
                            networkType = getNetworkTypeName(TelephonyManager.NETWORK_TYPE_GSM)
                        )
                    }
                    is CellInfoCdma -> {
                        signalDbm = cellInfo.cellSignalStrength.dbm
                        signalLevel = cellInfo.cellSignalStrength.level
                        currentInfo = currentInfo.copy(
                            cellId = cellInfo.cellIdentity.basestationId,
                            lac = cellInfo.cellIdentity.networkId,
                            frequency = "CDMA",
                            networkType = getNetworkTypeName(TelephonyManager.NETWORK_TYPE_CDMA)
                        )
                    }
                    is CellInfoTdscdma -> {
                        signalDbm = cellInfo.cellSignalStrength.dbm
                        signalLevel = cellInfo.cellSignalStrength.level
                        currentInfo = currentInfo.copy(
                            cellId = cellInfo.cellIdentity.cid,
                            lac = cellInfo.cellIdentity.lac,
                            arfcn = cellInfo.cellIdentity.uarfcn,
                            frequency = "TD-SCDMA (UARFCN: ${cellInfo.cellIdentity.uarfcn})",
                            networkType = getNetworkTypeName(TelephonyManager.NETWORK_TYPE_TD_SCDMA)
                        )
                    }
                    /*is CellInfoNr -> { // Added for 5G NR, assuming you might add it later if not already present
                        signalDbm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cellInfo.cellSignalStrength.dbm else 0
                        signalLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cellInfo.cellSignalStrength.level else 0
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            currentInfo = currentInfo.copy(
                                cellId = cellInfo.cellIdentity.nci.toInt(),
                                tac = cellInfo.cellIdentity.tac,
                                nrarfcn = cellInfo.cellIdentity.nrarfcn,
                                frequency = "5G NR (NRARFCN: ${cellInfo.cellIdentity.nrarfcn})",
                                networkType = getNetworkTypeName(TelephonyManager.NETWORK_TYPE_NR)
                            )
                        }
                    }*/
                    else -> {
                        // Fallback to general signal strength from CellInfo if cell type is unknown but CellInfo exists
                        signalDbm = cellInfo.cellSignalStrength.dbm
                        signalLevel = cellInfo.cellSignalStrength.level
                    }
                }
                // Apply the extracted signal strength to the currentInfo
                currentInfo = currentInfo.copy(
                    signalStrengthDbm = signalDbm,
                    signalStrengthLevel = signalLevel
                )
            }
        } catch (e: SecurityException) {
            Log.e("NetworkInfoRepo", "SecurityException getting CellInfo: ${e.message}. Ensure ACCESS_FINE_LOCATION and runtime permissions are granted, and Location is enabled on device.")
            currentInfo = currentInfo.copy(
                cellId = null, lac = null, tac = null, arfcn = null, earfcn = null, nrarfcn = null,
                frequency = "Restricted (Location/Permission missing)",
                bandwidth = "Restricted (Location/Permission missing)",
                signalStrengthDbm = 0, signalStrengthLevel = 0
            )
        } catch (e: Exception) {
            Log.e("NetworkInfoRepo", "Error getting CellInfo: ${e.message}")
            currentInfo = currentInfo.copy(
                cellId = null, lac = null, tac = null, arfcn = null, earfcn = null, nrarfcn = null,
                frequency = "Error retrieving", bandwidth = "Error retrieving",
                signalStrengthDbm = 0, signalStrengthLevel = 0
            )
        }

        telephonyManager.signalStrength?.let { generalSignal ->
            currentInfo = currentInfo.copy(
                signalStrengthLevel = generalSignal.level
            )
        }
        // Check if signal level has changed using the values derived from CellInfo (or general fallback)
        val currentLevel = currentInfo.signalStrengthLevel
        val currentDbm = currentInfo.signalStrengthDbm

        if (currentLevel != lastSignalStrengthLevel) {
            Log.d("NetworkInfoRepo", "Signal strength level changed from $lastSignalStrengthLevel to $currentLevel. Firing service.")
            fireSignalStrengthChangedService(currentLevel, currentDbm, currentInfo.networkType)
            lastSignalStrengthLevel = currentLevel // Update last reported level
        }

        _networkInfo.value = currentInfo
    }

    private fun fireSignalStrengthChangedService(level: Int, dbm: Int, networkType: String) {
        val intent = Intent(context, SignalStrengthService::class.java).apply {
            action = SignalStrengthService.ACTION_SIGNAL_STRENGTH_CHANGED
            putExtra(SignalStrengthService.EXTRA_SIGNAL_LEVEL, level)
            putExtra(SignalStrengthService.EXTRA_SIGNAL_DBM, dbm)
            putExtra(SignalStrengthService.EXTRA_NETWORK_TYPE, networkType)
        }
        SignalStrengthService.enqueueWork(context, intent)
    }

    private fun hasRequiredPermissions(): Boolean {
        val readPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val fineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val networkState = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED
        return readPhoneState && fineLocation && networkState
    }

    private fun getNetworkTypeName(networkType: Int): String {
        return when (networkType) {
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            else -> "UNKNOWN ($networkType)"
        }
    }

    @SuppressLint("MissingPermission")
    private fun getVoLTEStatus(tm: TelephonyManager): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val voiceServiceState = tm.isConcurrentVoiceAndDataSupported
            return if (voiceServiceState) {
                if (tm.dataNetworkType == TelephonyManager.NETWORK_TYPE_LTE) {
                    "Support voice and data for LTE)"
                } else if (tm.dataNetworkType == TelephonyManager.NETWORK_TYPE_NR) {
                    "Support voice and data for NR)"
                } else {
                    "Not LTE/NR)"
                }
            } else {
                "No VoLTE"
            }
        }
        return "N/A (API < 28)"
    }
}