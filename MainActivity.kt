package com.fattyleo.networkinfoapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.*
import com.fattyleo.networkinfoapp.data.NetworkInfoRepository
import com.fattyleo.networkinfoapp.model.NetworkInfo
import com.fattyleo.networkinfoapp.viewmodel.NetworkInfoViewModel
import com.fattyleo.networkinfoapp.ui.theme.NetworkInfoAppTheme
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //notification channel for displaying notifications.
        val channel = NotificationChannel(
            "signal_channel_id",
            "Signal Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for signal strength changes"
        }

        val notificationManager: NotificationManager =
            getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        setContent {
            NetworkInfoAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    val networkInfoRepository = remember { NetworkInfoRepository(context.applicationContext) }
                    val viewModel: NetworkInfoViewModel = viewModel(
                        factory = NetworkInfoViewModel.Factory(networkInfoRepository)
                    )
                    NetworkInfoScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NetworkInfoScreen(viewModel: NetworkInfoViewModel) {
    val context = LocalContext.current
    val networkInfo by viewModel.networkInfo.collectAsState()

    // State to control feedback dialog visibility
    var showFeedbackDialog by remember { mutableStateOf(false) }

    // State to store the previous call state for comparison
    var previousCallState by remember { mutableStateOf(networkInfo.callState) }

    // Request permissions
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    )

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            viewModel.startNetworkUpdates()
        } else {
            Toast.makeText(context, "Permissions not granted. Network info might be limited.", Toast.LENGTH_LONG).show()
        }
    }

    // Effect to detect call end and show feedback dialog
    LaunchedEffect(networkInfo.callState) {
        // Check if call just ended (transition from Off-hook to Idle)
        if (previousCallState == "Off-hook" && networkInfo.callState == "Idle") {
            showFeedbackDialog = true
        }
        previousCallState = networkInfo.callState // Update previous state for next recomposition
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopNetworkUpdates()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Network Info") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (!permissionsState.allPermissionsGranted) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Please grant permissions to access network information.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                        Text("Request Permissions")
                    }
                }
            } else {
                NetworkInfoDisplay(networkInfo = networkInfo)
            }
        }
    }

    // Show feedback dialog if the state is true
    if (showFeedbackDialog) {
        CallFeedbackDialog(
            onDismiss = { showFeedbackDialog = false },
            onFeedbackSubmit = { feedback ->
                // Here you would typically send the feedback to a backend or save it locally
                Toast.makeText(context, "Feedback Submitted: $feedback", Toast.LENGTH_SHORT).show()
                showFeedbackDialog = false
            }
        )
    }
}

@Composable
fun NetworkInfoDisplay(networkInfo: NetworkInfo) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { InfoRow("Signal Level (Bars)", networkInfo.signalStrengthLevel.toString()) }
        item { InfoRow("Signal Strength (dBm)", "${networkInfo.signalStrengthDbm} dBm") }
        item { InfoRow("Network Type", networkInfo.networkType) }
        item { InfoRow("Operator Name", networkInfo.operatorName) }
        item { InfoRow("MCC (Mobile Country Code)", networkInfo.mcc) }
        item { InfoRow("MNC (Mobile Network Code)", networkInfo.mnc) }
        item { InfoRow("Roaming Status", networkInfo.roamingStatus) }
        item { InfoRow("VoLTE Status ", networkInfo.isVolteCapable) }
        item { InfoRow("Subscription ID", networkInfo.subscriptionId.toString()) }

        item {
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Call Info:", style = MaterialTheme.typography.titleMedium)
        }
        item { InfoRow("Call State", networkInfo.callState) }
        item {
            InfoRow(
                "Last Call Event",
                networkInfo.lastCallEventTimestamp?.let { dateFormat.format(it) } ?: "N/A"
            )
        }

        item {
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Detailed Cell Info (if available):", style = MaterialTheme.typography.titleMedium)
        }
        item { InfoRow("Cell ID", networkInfo.cellId?.toString() ?: "N/A") }
        item { InfoRow("LAC (2G/3G) / TAC (LTE/5G)", networkInfo.lac?.toString() ?: networkInfo.tac?.toString() ?: "N/A") }
        item { InfoRow("ARFCN (2G/3G)", networkInfo.arfcn?.toString() ?: "N/A") }
        item { InfoRow("EARFCN (LTE)", networkInfo.earfcn?.toString() ?: "N/A") }
        item { InfoRow("NR-ARFCN (5G NR)", networkInfo.nrarfcn?.toString() ?: "N/A") }
        item { InfoRow("Frequency (Inferred)", networkInfo.frequency) }
        item { InfoRow("Bandwidth (Restricted)", networkInfo.bandwidth) }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallFeedbackDialog(
    onDismiss: () -> Unit,
    onFeedbackSubmit: (String) -> Unit
) {
    var feedbackText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Call Experience Feedback") },
        text = {
            Column {
                Text("How was your recent call experience?")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = feedbackText,
                    onValueChange = { feedbackText = it },
                    label = { Text("Your feedback (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(onClick = { onFeedbackSubmit(feedbackText) }) {
                Text("Submit Feedback")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        }
    )
}