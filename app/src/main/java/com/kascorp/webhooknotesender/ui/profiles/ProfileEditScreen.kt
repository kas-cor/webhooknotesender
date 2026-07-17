package com.kascorp.webhooknotesender.ui.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kascorp.webhooknotesender.data.local.entity.ProfileEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    profileId: Long,
    viewModel: ProfilesViewModel,
    onNavigateBack: () -> Unit
) {
    val formState by viewModel.formState.collectAsState()
    val editState by viewModel.editState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(profileId) {
        viewModel.loadProfile(profileId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (editState.isEditing) "Edit Profile" else "Create Profile",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Name field
            OutlinedTextField(
                value = formState.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Name") },
                placeholder = { Text("e.g., Office Camera") },
                isError = formState.nameError != null,
                supportingText = formState.nameError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Type dropdown
            TypeDropdown(
                selectedType = formState.type,
                onTypeSelected = { viewModel.updateType(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Prompt field
            OutlinedTextField(
                value = formState.prompt,
                onValueChange = { viewModel.updatePrompt(it) },
                label = { Text("Prompt") },
                placeholder = { Text("Describe what the AI should do with this media...") },
                isError = formState.promptError != null,
                supportingText = formState.promptError?.let { { Text(it) } },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // URL field
            OutlinedTextField(
                value = formState.url,
                onValueChange = { viewModel.updateUrl(it) },
                label = { Text("Webhook URL") },
                placeholder = { Text("https://your-webhook.com/endpoint") },
                isError = formState.urlError != null,
                supportingText = {
                    formState.urlError?.let {
                        Text(
                            text = it,
                            color = if (it.contains("not secure"))
                                MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.error
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bearer token field
            OutlinedTextField(
                value = formState.bearerToken,
                onValueChange = { viewModel.updateBearerToken(it) },
                label = { Text("Bearer Token (optional)") },
                placeholder = { Text("sk-...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Save button
            Button(
                onClick = {
                    viewModel.saveProfile(onSuccess = onNavigateBack)
                },
                enabled = !formState.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (formState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(24.dp)
                            .width(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (editState.isEditing) "Update Profile" else "Create Profile",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeDropdown(
    selectedType: String,
    onTypeSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val types = listOf(
        "image" to "Image" to Icons.Filled.CameraAlt,
        "audio" to "Audio" to Icons.Filled.Mic,
        "video" to "Video" to Icons.Filled.Videocam
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = types.find { it.first.first == selectedType }?.let { "${it.first.second}" } ?: selectedType,
            onValueChange = {},
            readOnly = true,
            label = { Text("Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            types.forEach { (typeInfo, icon) ->
                val (typeVal, typeLabel) = typeInfo
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Text(typeLabel)
                        }
                    },
                    onClick = {
                        onTypeSelected(typeVal)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
