package com.vektor.ui.screens.health

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vektor.domain.model.UserProfile

@Composable
fun HealthScreen(
    onBack: () -> Unit,
    viewModel: HealthViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Health Profile", style = MaterialTheme.typography.headlineMedium)
        }

        if (profile == null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        CircularProgressIndicator()
                    }
                }
            }
        } else {
            val p: UserProfile = profile!!

            item {
                ProfileSection(title = "Personal Info") {
                    ProfileRow("Name", p.name)
                    ProfileRow("Date of Birth", p.dob.ifBlank { "Not set" })
                    ProfileRow("Blood Group", p.bloodGroup)
                }
            }

            item {
                ProfileSection(title = "Allergies") {
                    if (p.allergies.isEmpty()) {
                        Text("None recorded", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        p.allergies.forEach { allergy ->
                            Text("• $allergy", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                ProfileSection(title = "Conditions") {
                    if (p.conditions.isEmpty()) {
                        Text("None recorded", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        p.conditions.forEach { condition ->
                            Text("• $condition", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                ProfileSection(title = "Medications") {
                    if (p.medications.isEmpty()) {
                        Text("None recorded", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        p.medications.forEach { med ->
                            Text("• $med", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                ProfileSection(title = "Emergency Contacts") {
                    if (p.emergencyContacts.isEmpty()) {
                        Text("None recorded", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        p.emergencyContacts.forEach { contact ->
                            if (contact.name.isNotBlank()) {
                                Text(
                                    "• ${contact.name} (${contact.relation}) — ${contact.phone}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            item {
                ProfileSection(title = "Medical History") {
                    Text(
                        p.medicalHistory.ifBlank { "No history recorded." },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            item {
                ProfileSection(title = "Insurance") {
                    ProfileRow("Provider", p.insuranceProvider.ifBlank { "Not set" })
                    ProfileRow("Policy No.", p.insurancePolicyNo.ifBlank { "Not set" })
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Back")
                }
            }
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        ))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
