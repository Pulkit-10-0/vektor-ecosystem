package com.vektor.ui.screens.emergency

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun CountdownScreen(
    onSosDispatched: () -> Unit = {},
    onCancel: () -> Unit,
    viewModel: CountdownViewModel = hiltViewModel()
) {
    val countdownValue by viewModel.countdownValue.collectAsState()
    val sosDispatched by viewModel.sosDispatched.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startCountdown()
    }

    LaunchedEffect(sosDispatched) {
        if (sosDispatched) {
            onSosDispatched()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Emergency Detected",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "$countdownValue",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = MaterialTheme.typography.displayLarge.fontSize * 2
            ),
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (countdownValue > 0) "Dispatching alert in $countdownValue seconds…"
                   else "Alert dispatched!",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(64.dp))

        Button(
            onClick = {
                viewModel.cancelCountdown()
                onCancel()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Cancel Alert", modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text("CANCEL", style = MaterialTheme.typography.headlineMedium)
        }
    }
}
