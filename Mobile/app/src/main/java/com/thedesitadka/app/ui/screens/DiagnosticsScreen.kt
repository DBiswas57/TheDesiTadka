package com.thedesitadka.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thedesitadka.core.config.ConfigRepository
import com.thedesitadka.core.model.ProviderStatus
import com.thedesitadka.core.security.IntegrityChecker
import com.thedesitadka.provider.ProviderEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    configRepository: ConfigRepository,
    providerEngine: ProviderEngine,
    onBackClick: () -> Unit
) {
    val manifest by configRepository.manifestFlow.collectAsState()
    val integrity = remember { IntegrityChecker.performCheck() }
    val providers = remember { providerEngine.getActiveProviders() }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = { Text("Diagnostics Mode", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Diagnostics Note
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "Zero-Secret Diagnostics: All auth tokens, cookies, and keys are automatically redacted in compliance with security policies.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Configuration State Card
            Text(text = "CONFIGURATION STATE", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    DiagRow("Schema Version", manifest.schemaVersion.toString())
                    DiagRow("Config Version", manifest.configVersion.toString())
                    DiagRow("Generated At", manifest.generatedAt.toString())
                    DiagRow("Expires At", if (manifest.expiresAt == Long.MAX_VALUE) "Never (Infinite)" else manifest.expiresAt.toString())
                    DiagRow("Min App Version", manifest.minimumAppVersion.toString())
                    DiagRow("Maintenance Mode", manifest.maintenanceMode.toString())
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Integrity State Card
            Text(text = "SECURITY INTEGRITY SIGNALS", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    DiagRow("Debug Build", integrity.isDebuggable.toString())
                    DiagRow("Root Risk Flag", integrity.isRooted.toString())
                    DiagRow("Emulator Flag", integrity.isEmulator.toString())
                    DiagRow("Tamper Signal", integrity.isTampered.toString())
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Provider Health State Card
            Text(text = "PROVIDER HEALTH & ADAPTERS", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    providers.forEachIndexed { index, provider ->
                        val status = providerEngine.healthMonitor.getStatus(provider.id, provider.status)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = provider.name, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(text = "ID: ${provider.id} | Base: ${provider.baseUrl}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = status.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = when (status) {
                                    ProviderStatus.ENABLED -> Color(0xFF10B981)
                                    ProviderStatus.DEGRADED -> Color(0xFFF59E0B)
                                    else -> MaterialTheme.colorScheme.error
                                }
                            )
                        }
                        if (index < providers.size - 1) {
                            Divider(color = MaterialTheme.colorScheme.surface, thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}
