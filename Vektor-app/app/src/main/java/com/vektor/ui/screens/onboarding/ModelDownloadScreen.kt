package com.vektor.ui.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vektor.R

@Composable
fun ModelDownloadScreen(
    onContinue: () -> Unit,
    viewModel: ModelDownloadViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val statusText by viewModel.statusText.collectAsState()

    // Auto-continue when done
    LaunchedEffect(state) {
        if (state == DownloadState.DONE) {
            kotlinx.coroutines.delay(800)
            onContinue()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "Vektor Logo",
            modifier = Modifier.size(96.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Preparing Vektor AI",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            statusText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(32.dp))

        when (state) {
            DownloadState.CHECKING -> {
                CircularProgressIndicator()
            }

            DownloadState.NOT_FOUND -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Choose how to power Vektor AI:",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Option 1: Gemma 4 E2B
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Download Gemma 4 E2B (~4.4 GB)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Full model. Best quality. Runs fully offline.\nWi-Fi strongly recommended.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.startDownload() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Download Gemma 4 E2B")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Option 2: Gemma 3 270M
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Download Gemma 3 270M (~170 MB)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Smaller model. Faster download. Runs fully offline.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { viewModel.downloadSmallModel() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Download Gemma 3 270M")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Option 3: Gemini API
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Use Gemini API (requires internet)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                "No download needed. Always available. Requires internet connection.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { viewModel.useGeminiOnly(onContinue) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Use Gemini API")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = onContinue) {
                        Text("Skip — use stub mode")
                    }
                }
            }

            DownloadState.DOWNLOADING -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            DownloadState.EXTRACTING -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Extracting model files…", style = MaterialTheme.typography.bodySmall)
                }
            }

            DownloadState.DONE -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Starting…", style = MaterialTheme.typography.bodySmall)
            }

            DownloadState.ERROR -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Download failed. Check your internet connection.",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.retryDownload() },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("Retry")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.useGeminiOnly(onContinue) }) {
                        Text("Use Gemini API instead")
                    }
                    TextButton(onClick = onContinue) {
                        Text("Skip — use stub mode")
                    }
                }
            }
        }
    }
}
