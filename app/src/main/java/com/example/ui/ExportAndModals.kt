package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CadComponent
import com.example.data.CadProject
import com.example.data.ExportFormat
import com.example.data.PrinterProfile
import com.example.data.ThreadStandard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialog for exporting STEP, STL, and Multi-Object 3MF files, saving to Cloud Providers
 * (Google Drive / Nextcloud / Device SAF), sharing to Slicer apps, and sending directly to
 * local/remote 3D printers (OctoPrint, Moonraker/Klipper, PrusaLink).
 */
@Composable
fun ExportAnd3DPrintDialog(
    projectName: String,
    components: List<CadComponent>,
    multiObject3mf: Boolean,
    printerProfilesState: CloudUiState<List<PrinterProfile>>,
    onSetMultiObject3mf: (Boolean) -> Unit,
    onGenerateBytes: (ExportFormat) -> ByteArray,
    onShareFile: (ExportFormat) -> Unit,
    onSendToPrinter: (PrinterProfile, ExportFormat, Boolean) -> Unit,
    onAddPrinterProfile: (String, String, String, String, Int, Int, Float) -> Unit,
    onDeletePrinterProfile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedFormat by remember { mutableStateOf(ExportFormat.THREE_MF) }
    var startPrintImmediately by remember { mutableStateOf(false) }
    var showAddPrinterForm by remember { mutableStateOf(false) }

    // New Printer Form State
    var newPrinterName by remember { mutableStateOf("Klipper / Voron 2.4") }
    var newPrinterProtocol by remember { mutableStateOf("MOONRAKER") }
    var newPrinterUrl by remember { mutableStateOf("http://192.168.1.100:7125") }
    var newPrinterApiKey by remember { mutableStateOf("") }

    // Android Storage Access Framework (SAF) launcher to save directly to local storage or Google Drive / Cloud Providers
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.Default) { onGenerateBytes(selectedFormat) }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121A28),
        titleContentColor = Color.White,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF)
                    )
                    Text(
                        text = "Esporta CAD & Stampa 3D",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color.White)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "1. SCEGLI FORMATO DI USCITA CAD",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF00E5FF)
                )

                ExportFormat.entries.forEach { fmt ->
                    val isSelected = selectedFormat == fmt
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFormat = fmt }
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF263852),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF003642) else Color(0xFF1B2638)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fmt.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = ".${fmt.extension.uppercase()} • ${components.size} forme nell'assieme",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF8EA4C2)
                                )
                            }
                        }
                    }
                }

                // Multi-Object 3MF Option specifically requested by the user!
                if (selectedFormat == ExportFormat.THREE_MF) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF261C0A)),
                        modifier = Modifier.border(1.dp, Color(0xFFFF9100), RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Singolo 3MF con Oggetti Separati per Forma",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFFFF9100),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Ogni forma (${components.size} parti) diventa un oggetto distinto nel 3MF: nello Slicer assegni colori/filamenti diversi (es. PLA + TPU) senza colorare a mano!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFFFDDB8)
                                    )
                                }
                                Switch(
                                    checked = multiObject3mf,
                                    onCheckedChange = onSetMultiObject3mf,
                                    modifier = Modifier.testTag("multi_object_3mf_switch")
                                )
                            }
                        }
                    }
                }

                // Local / Cloud File Export Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val safeName = projectName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "SimpleCAD" }
                            createDocumentLauncher.launch("$safeName.${selectedFormat.extension}")
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color(0xFF002630))
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salva File / Drive")
                    }

                    OutlinedButton(
                        onClick = { onShareFile(selectedFormat) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Invia a Slicer")
                    }
                }

                HorizontalDivider(color = Color(0xFF263852))

                // 2. Direct Send to Local or Remote 3D Printers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "2. STAMPA 3D DIRETTA (LOCALE / REMOTA)",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF00E676)
                    )
                    TextButton(onClick = { showAddPrinterForm = !showAddPrinterForm }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showAddPrinterForm) "Nascondi" else "Aggiungi Stampante")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Avvia stampa subito dopo l'upload",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFECF4FF)
                    )
                    Switch(
                        checked = startPrintImmediately,
                        onCheckedChange = { startPrintImmediately = it }
                    )
                }

                if (showAddPrinterForm) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2638)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newPrinterName,
                                onValueChange = { newPrinterName = it },
                                label = { Text("Nome Stampante (es. Voron / Bambu / Prusa)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("MOONRAKER", "OCTOPRINT", "PRUSALINK", "CLOUD_SLICER").forEach { proto ->
                                    FilterChip(
                                        selected = newPrinterProtocol == proto,
                                        onClick = { newPrinterProtocol = proto },
                                        label = { Text(proto) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = newPrinterUrl,
                                onValueChange = { newPrinterUrl = it },
                                label = { Text("Indirizzo IP / URL Remoto (http://...)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newPrinterApiKey,
                                onValueChange = { newPrinterApiKey = it },
                                label = { Text("API Key / Token (opzionale)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    onAddPrinterProfile(
                                        newPrinterName,
                                        newPrinterProtocol,
                                        newPrinterUrl,
                                        newPrinterApiKey,
                                        250,
                                        250,
                                        0.4f
                                    )
                                    showAddPrinterForm = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Salva Stampante nel Cloud")
                            }
                        }
                    }
                }

                when (printerProfilesState) {
                    is CloudUiState.Loading -> Text("Caricamento stampanti...", color = Color.Gray)
                    is CloudUiState.Error -> Text(printerProfilesState.message, color = MaterialTheme.colorScheme.error)
                    is CloudUiState.Success -> {
                        if (printerProfilesState.data.isEmpty()) {
                            Text(
                                text = "Nessuna stampante salvata. Premi 'Aggiungi Stampante' per configurare OctoPrint, Moonraker/Klipper o PrusaLink.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8EA4C2)
                            )
                        } else {
                            printerProfilesState.data.forEach { printer ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2638)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${printer.name} [${printer.protocol}]",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White
                                            )
                                            Text(
                                                text = printer.endpointUrl,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF8EA4C2)
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                onSendToPrinter(printer, selectedFormat, startPrintImmediately)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF00E676),
                                                contentColor = Color(0xFF002610)
                                            )
                                        ) {
                                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Stampa")
                                        }
                                        IconButton(onClick = { onDeletePrinterProfile(printer.id) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Elimina stampante",
                                                tint = Color(0xFFFF5252)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Chiudi")
            }
        }
    )
}

/**
 * Dialog for browsing, saving, and loading Cloud CAD Projects stored in Firebase Firestore.
 */
@Composable
fun CloudProjectsDialog(
    projectName: String,
    cloudProjectsState: CloudUiState<List<CadProject>>,
    onUpdateProjectName: (String) -> Unit,
    onSaveCurrentToCloud: () -> Unit,
    onLoadProject: (CadProject) -> Unit,
    onDeleteProject: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121A28),
        titleContentColor = Color.White,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF00E5FF))
                    Text("Cloud Storage Progetti CAD", style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color.White)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = onUpdateProjectName,
                    label = { Text("Nome Progetto Attivo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = onSaveCurrentToCloud,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_to_cloud_confirm_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color(0xFF002630)
                    )
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Salva Progetto Corrente nel Cloud", fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(color = Color(0xFF263852))

                Text(
                    text = "I TUOI PROGETTI SALVATI NEL CLOUD",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFFF9100)
                )

                when (cloudProjectsState) {
                    is CloudUiState.Loading -> Text("Sincronizzazione Cloud in corso...", color = Color.Gray)
                    is CloudUiState.Error -> Text(cloudProjectsState.message, color = MaterialTheme.colorScheme.error)
                    is CloudUiState.Success -> {
                        if (cloudProjectsState.data.isEmpty()) {
                            Text(
                                text = "Nessun progetto salvato nel tuo spazio Cloud. Premi 'Salva Progetto Corrente nel Cloud' per eseguire il primo backup!",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8EA4C2)
                            )
                        } else {
                            cloudProjectsState.data.forEach { proj ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2638)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = proj.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "${proj.partCount} parti • 3MF Multi-Obj: ${if (proj.multiObject3mf) "SÌ" else "NO"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF00E5FF)
                                            )
                                        }
                                        IconButton(onClick = { onLoadProject(proj) }) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDownload,
                                                contentDescription = "Carica progetto",
                                                tint = Color(0xFF00E676)
                                            )
                                        }
                                        IconButton(onClick = { onDeleteProject(proj.id) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Elimina progetto",
                                                tint = Color(0xFFFF5252)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Chiudi")
            }
        }
    )
}
