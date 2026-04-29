package com.vektor.ui.screens.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.File
import java.io.FileOutputStream

/**
 * Step-by-step health profile wizard shown after account creation.
 * Each question is shown one at a time with a forward animation.
 */
@Composable
fun HealthOnboardingScreen(
    uid: String,
    onComplete: () -> Unit,
    viewModel: HealthOnboardingViewModel = hiltViewModel()
) {
    val step by viewModel.step.collectAsState()
    val totalSteps = HealthOnboardingViewModel.TOTAL_STEPS

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress indicator
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Health Profile Setup",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (step + 1).toFloat() / totalSteps },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Step ${step + 1} of $totalSteps",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn()) togetherWith
                (slideOutHorizontally { -it } + fadeOut())
            },
            label = "step_anim"
        ) { currentStep ->
            when (currentStep) {
                0 -> StepFullName(viewModel, onNext = { viewModel.nextStep() })
                1 -> StepDob(viewModel, onNext = { viewModel.nextStep() })
                2 -> StepBloodGroup(viewModel, onNext = { viewModel.nextStep() })
                3 -> StepAllergies(viewModel, onNext = { viewModel.nextStep() })
                4 -> StepConditions(viewModel, onNext = { viewModel.nextStep() })
                5 -> StepMedications(viewModel, onNext = { viewModel.nextStep() })
                6 -> StepEmergencyContact(viewModel, onNext = { viewModel.nextStep() })
                7 -> StepInsurance(viewModel, onNext = { viewModel.nextStep() })
                8 -> StepMedicalRecords(viewModel, onFinish = {
                    viewModel.saveProfile(uid)
                    onComplete()
                })
                else -> onComplete()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = {
            viewModel.saveProfile(uid)
            onComplete()
        }) {
            Text("Skip remaining questions")
        }
    }
}

// ── Step composables ──────────────────────────────────────────────────────────

@Composable
private fun StepFullName(vm: HealthOnboardingViewModel, onNext: () -> Unit) {
    var value by remember { mutableStateOf(vm.fullName) }
    StepScaffold(
        question = "What's your full name?",
        hint = "This appears on your emergency QR card",
        onNext = { vm.fullName = value; onNext() },
        nextEnabled = value.isNotBlank()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text("Full name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        )
    }
}

@Composable
private fun StepDob(vm: HealthOnboardingViewModel, onNext: () -> Unit) {
    var value by remember { mutableStateOf(vm.dob) }
    StepScaffold(
        question = "Date of birth?",
        hint = "Format: YYYY-MM-DD",
        onNext = { vm.dob = value; onNext() },
        nextEnabled = true
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text("e.g. 1990-06-15") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StepBloodGroup(vm: HealthOnboardingViewModel, onNext: () -> Unit) {
    val groups = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "Unknown")
    var selected by remember { mutableStateOf(vm.bloodGroup.ifBlank { "Unknown" }) }
    StepScaffold(
        question = "What is your blood group?",
        hint = "Critical for emergency responders",
        onNext = { vm.bloodGroup = selected; onNext() },
        nextEnabled = true
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { group ->
                        FilterChip(
                            selected = selected == group,
                            onClick = { selected = group },
                            label = { Text(group) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Pad last row if needed
                    repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun StepAllergies(vm: HealthOnboardingViewModel, onNext: () -> Unit) {
    val items = remember { mutableStateListOf<String>().also { it.addAll(vm.allergies) } }
    var current by remember { mutableStateOf("") }
    StepScaffold(
        question = "Any allergies?",
        hint = "e.g. Penicillin, Latex, Peanuts — add each separately",
        onNext = { vm.allergies = items.toList(); onNext() },
        nextEnabled = true
    ) {
        ChipListInput(
            items = items,
            current = current,
            onCurrentChange = { current = it },
            onAdd = { if (current.isNotBlank()) { items.add(current.trim()); current = "" } },
            onRemove = { items.remove(it) },
            placeholder = "Add allergy"
        )
    }
}

@Composable
private fun StepConditions(vm: HealthOnboardingViewModel, onNext: () -> Unit) {
    val items = remember { mutableStateListOf<String>().also { it.addAll(vm.conditions) } }
    var current by remember { mutableStateOf("") }
    StepScaffold(
        question = "Any chronic conditions?",
        hint = "e.g. Type 2 Diabetes, Hypertension, Asthma",
        onNext = { vm.conditions = items.toList(); onNext() },
        nextEnabled = true
    ) {
        ChipListInput(
            items = items,
            current = current,
            onCurrentChange = { current = it },
            onAdd = { if (current.isNotBlank()) { items.add(current.trim()); current = "" } },
            onRemove = { items.remove(it) },
            placeholder = "Add condition"
        )
    }
}

@Composable
private fun StepMedications(vm: HealthOnboardingViewModel, onNext: () -> Unit) {
    val items = remember { mutableStateListOf<String>().also { it.addAll(vm.medications) } }
    var current by remember { mutableStateOf("") }
    StepScaffold(
        question = "Current medications?",
        hint = "Include dosage if known, e.g. Metformin 500mg twice daily",
        onNext = { vm.medications = items.toList(); onNext() },
        nextEnabled = true
    ) {
        ChipListInput(
            items = items,
            current = current,
            onCurrentChange = { current = it },
            onAdd = { if (current.isNotBlank()) { items.add(current.trim()); current = "" } },
            onRemove = { items.remove(it) },
            placeholder = "Add medication"
        )
    }
}

@Composable
private fun StepEmergencyContact(vm: HealthOnboardingViewModel, onNext: () -> Unit) {
    var name by remember { mutableStateOf(vm.emergencyContactName) }
    var phone by remember { mutableStateOf(vm.emergencyContactPhone) }
    var relation by remember { mutableStateOf(vm.emergencyContactRelation) }
    StepScaffold(
        question = "Emergency contact?",
        hint = "Who should be called in an emergency",
        onNext = {
            vm.emergencyContactName = name
            vm.emergencyContactPhone = phone
            vm.emergencyContactRelation = relation
            onNext()
        },
        nextEnabled = true
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = relation,
                onValueChange = { relation = it },
                label = { Text("Relation (e.g. Sister, Father)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
        }
    }
}

@Composable
private fun StepInsurance(vm: HealthOnboardingViewModel, onNext: () -> Unit) {
    var provider by remember { mutableStateOf(vm.insuranceProvider) }
    var policyNo by remember { mutableStateOf(vm.insurancePolicyNo) }
    StepScaffold(
        question = "Insurance details?",
        hint = "Optional — helps emergency responders",
        onNext = {
            vm.insuranceProvider = provider
            vm.insurancePolicyNo = policyNo
            onNext()
        },
        nextEnabled = true,
        nextLabel = "Next"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = provider,
                onValueChange = { provider = it },
                label = { Text("Insurance provider") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = policyNo,
                onValueChange = { policyNo = it },
                label = { Text("Policy number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StepMedicalRecords(vm: HealthOnboardingViewModel, onFinish: () -> Unit) {
    val context = LocalContext.current
    var selectedFileName by remember { mutableStateOf(
        if (vm.medicalRecordPath.isNotBlank()) File(vm.medicalRecordPath).name else ""
    ) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "medical_record"
                val destDir = File(context.filesDir, "medical_records")
                destDir.mkdirs()
                val destFile = File(destDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                vm.medicalRecordPath = destFile.absolutePath
                selectedFileName = fileName
            } catch (_: Exception) { }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text("Medical records?", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Optionally upload a PDF or image of your medical records",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (selectedFileName.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedFileName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(onClick = {
                        vm.medicalRecordPath = ""
                        selectedFileName = ""
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove file")
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedButton(
            onClick = { filePicker.launch("*/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (selectedFileName.isBlank()) "Select file (PDF or image)" else "Change file")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Finish")
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Skip this step")
        }
    }
}

// ── Shared UI helpers ─────────────────────────────────────────────────────────

@Composable
private fun StepScaffold(
    question: String,
    hint: String,
    onNext: () -> Unit,
    nextEnabled: Boolean,
    nextLabel: String = "Next",
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(question, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        content()
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onNext,
            enabled = nextEnabled,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(nextLabel)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun ChipListInput(
    items: List<String>,
    current: String,
    onCurrentChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    placeholder: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = current,
                onValueChange = onCurrentChange,
                label = { Text(placeholder) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
        if (items.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { item ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(item) },
                        trailingIcon = {
                            IconButton(
                                onClick = { onRemove(item) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
