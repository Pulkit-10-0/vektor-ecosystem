package com.vektor.ui.screens.qr

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.vektor.qr.QrManager

@Composable
fun QrCardScreen(
    qrManager: QrManager,
    uid: String,
    onBack: () -> Unit
) {
    val bitmap = qrManager.generateAndSave(uid)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text("Emergency QR Code", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Scan this code to view Medical Info.")

        Spacer(modifier = Modifier.height(32.dp))

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Medical QR Code",
            modifier = Modifier.size(250.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Back to Home")
        }
    }
}
