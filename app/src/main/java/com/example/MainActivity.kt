package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cad.CadTemplateSpec
import com.example.cad.CadTemplates
import com.example.data.CadRepository
import com.example.data.ShapePrimitiveType
import com.example.ui.AnimatedCadSplashScreen
import com.example.ui.CadAuthScreen
import com.example.ui.CadToolModal
import com.example.ui.CadViewport3D
import com.example.ui.CloudProjectsDialog
import com.example.ui.ExportAnd3DPrintDialog
import com.example.ui.ParametricSettingsBox
import com.example.ui.SimpleCadViewModel
import com.example.ui.signOutUser
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SimpleCadRootApp()
            }
        }
    }
}

@Composable
fun SimpleCadRootApp() {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (showSplash) {
        AnimatedCadSplashScreen(
            onSplashFinished = { showSplash = false }
        )
    } else if (currentUser == null) {
        CadAuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        val uid = currentUser!!.uid
        val cadViewModel: SimpleCadViewModel = viewModel(
            key = uid,
            factory = viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY])
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    SimpleCadViewModel(CadRepository(db), uid)
                }
            }
        )
        SimpleCadWorkspaceScreen(
            viewModel = cadViewModel,
            userEmail = currentUser?.email ?: "Utente Cloud"
        )
    }
}

@Composable
fun SimpleCadWorkspaceScreen(
    viewModel: SimpleCadViewModel,
    userEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.editorState.collectAsStateWithLifecycle()
    val cloudProjectsState by viewModel.cloudProjectsState.collectAsStateWithLifecycle()
    val printerProfilesState by viewModel.printerProfilesState.collectAsStateWithLifecycle()

    var showTemplateStrip by rememberSaveable { mutableStateOf(true) }
    val selectedComponent = state.components.find { it.id == state.selectedComponentId }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SimpleCadTopBar(
                projectName = state.projectName,
                userEmail = userEmail,
                showTemplateStrip = showTemplateStrip,
                onToggleTemplates = { showTemplateStrip = !showTemplateStrip },
                onStartFromScratch = { viewModel.startFromScratch() },
                onOpenCloudModal = { viewModel.openModal(CadToolModal.CLOUD_PROJECTS) },
                onOpenExportPrintModal = { viewModel.openModal(CadToolModal.EXPORT_AND_PRINT) },
                onSignOut = {
                    signOutUser(context, onSignOutComplete = {}, scope = scope)
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Expandable Ready-to-Use Parametric Templates Gallery
                AnimatedVisibility(visible = showTemplateStrip) {
                    TemplateGalleryCarousel(
                        templates = CadTemplates.allTemplates,
                        activeTemplateId = state.templateType,
                        onSelectTemplate = { tpl -> viewModel.loadTemplate(tpl) },
                        onStartScratch = { viewModel.startFromScratch() }
                    )
                }

                // 2. Fusion 360 Style CAD Modeling & Primitives Toolbar
                CadCommandToolbar(
                    onAddPrimitive = { type -> viewModel.addPrimitiveComponent(type) },
                    onToggleQuotes = { viewModel.toggleDimensionQuotes() },
                    onOpenInspectorForFeature = {
                        if (!state.isInspectorOpen) viewModel.toggleInspectorOpen()
                    }
                )

                // 3. Multi-Part Assembly Explosion & Build Plate Bar
                AssemblyExplosionBar(
                    components = state.components,
                    selectedComponentId = state.selectedComponentId,
                    explodeFactor = state.explodeFactor,
                    autoLayFlatOnBed = state.autoLayFlatOnBed,
                    onSelectComponent = { id -> viewModel.selectComponent(id) },
                    onExplodeChange = { f -> viewModel.setExplodeFactor(f) },
                    onToggleLayFlat = { viewModel.toggleAutoLayFlatOnBed() }
                )

                // 4. Status Toast / Banner
                if (state.statusMessage != null) {
                    Surface(
                        color = Color(0xFF003642),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.clearStatusMessage() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.statusMessage!!,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF00E5FF),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "OK",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 5. Real-Time 3D CAD Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    CadViewport3D(
                        components = state.components,
                        selectedComponentId = state.selectedComponentId,
                        explodeFactor = state.explodeFactor,
                        autoLayFlatOnBed = state.autoLayFlatOnBed,
                        showWireframe = state.showWireframeOverlay,
                        showDimensionQuotes = state.showDimensionQuotes,
                        accelerometerMode = state.accelerometerMode,
                        onSelectComponent = { id -> viewModel.selectComponent(id) },
                        onAccelerometerModeChange = { mode -> viewModel.setAccelerometerMode(mode) },
                        onToggleWireframe = { viewModel.toggleWireframe() },
                        onToggleDimensionQuotes = { viewModel.toggleDimensionQuotes() }
                    )
                }

                // 6. Docked Parametric Settings Box (when not detached/floating)
                if (state.isInspectorOpen && !state.isInspectorFloating && selectedComponent != null) {
                    ParametricSettingsBox(
                        component = selectedComponent,
                        isFloating = false,
                        floatingOffsetX = 0f,
                        floatingOffsetY = 0f,
                        onToggleFloating = { viewModel.toggleInspectorFloating() },
                        onDragFloating = { _, _ -> },
                        onUpdateComponent = { transform -> viewModel.updateSelectedComponent(transform) },
                        onDuplicate = { viewModel.duplicateSelectedComponent() },
                        onDelete = { viewModel.deleteComponent(selectedComponent.id) },
                        onClose = { viewModel.toggleInspectorOpen() },
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .navigationBarsPadding()
                    )
                }
            }

            // 7. Detached / Free-Floating Parametric Settings Box (can be dragged anywhere over the screen!)
            if (state.isInspectorOpen && state.isInspectorFloating && selectedComponent != null) {
                ParametricSettingsBox(
                    component = selectedComponent,
                    isFloating = true,
                    floatingOffsetX = state.floatingOffsetX,
                    floatingOffsetY = state.floatingOffsetY,
                    onToggleFloating = { viewModel.toggleInspectorFloating() },
                    onDragFloating = { dx, dy -> viewModel.updateFloatingInspectorOffset(dx, dy) },
                    onUpdateComponent = { transform -> viewModel.updateSelectedComponent(transform) },
                    onDuplicate = { viewModel.duplicateSelectedComponent() },
                    onDelete = { viewModel.deleteComponent(selectedComponent.id) },
                    onClose = { viewModel.toggleInspectorOpen() }
                )
            }
        }
    }

    // Modals for Export / 3D Print and Cloud Storage
    when (state.activeModal) {
        CadToolModal.EXPORT_AND_PRINT -> {
            ExportAnd3DPrintDialog(
                projectName = state.projectName,
                components = state.components,
                multiObject3mf = state.multiObject3mf,
                printerProfilesState = printerProfilesState,
                onSetMultiObject3mf = { viewModel.setMultiObject3mf(it) },
                onGenerateBytes = { fmt -> viewModel.generateExportBytes(fmt) },
                onShareFile = { fmt -> viewModel.shareExportedFile(context, fmt) },
                onSendToPrinter = { printer, fmt, imm ->
                    viewModel.sendDirectlyTo3DPrinter(printer, fmt, imm)
                },
                onAddPrinterProfile = { name, proto, url, key, w, d, noz ->
                    viewModel.addPrinterProfile(name, proto, url, key, w, d, noz)
                },
                onDeletePrinterProfile = { id -> viewModel.deletePrinterProfile(id) },
                onDismiss = { viewModel.openModal(CadToolModal.NONE) }
            )
        }
        CadToolModal.CLOUD_PROJECTS -> {
            CloudProjectsDialog(
                projectName = state.projectName,
                cloudProjectsState = cloudProjectsState,
                onUpdateProjectName = { viewModel.updateProjectName(it) },
                onSaveCurrentToCloud = { viewModel.saveProjectToCloud() },
                onLoadProject = { proj -> viewModel.loadCloudProject(proj) },
                onDeleteProject = { id -> viewModel.deleteCloudProject(id) },
                onDismiss = { viewModel.openModal(CadToolModal.NONE) }
            )
        }
        else -> Unit
    }
}

@Composable
private fun SimpleCadTopBar(
    projectName: String,
    userEmail: String,
    showTemplateStrip: Boolean,
    onToggleTemplates: () -> Unit,
    onStartFromScratch: () -> Unit,
    onOpenCloudModal: () -> Unit,
    onOpenExportPrintModal: () -> Unit,
    onSignOut: () -> Unit
) {
    Surface(
        color = Color(0xFF121A28),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.ViewInAr,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(26.dp)
                )
                Column {
                    Text(
                        text = projectName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "SimpleCAD • $userEmail",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF8EA4C2),
                        maxLines = 1
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = onToggleTemplates,
                    label = { Text(if (showTemplateStrip) "Nascondi Template" else "Template") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.DashboardCustomize,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("toggle_templates_button")
                )

                AssistChip(
                    onClick = onStartFromScratch,
                    label = { Text("Da Zero") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.AddCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("start_from_scratch_button")
                )

                IconButton(
                    onClick = onOpenCloudModal,
                    modifier = Modifier.testTag("cloud_storage_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Cloud Storage",
                        tint = Color(0xFF00E5FF)
                    )
                }

                Button(
                    onClick = onOpenExportPrintModal,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E676),
                        contentColor = Color(0xFF002610)
                    ),
                    modifier = Modifier.testTag("export_and_print_button")
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("STEP/STL/3MF", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = onSignOut) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Esci",
                        tint = Color(0xFF8EA4C2)
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateGalleryCarousel(
    templates: List<CadTemplateSpec>,
    activeTemplateId: String,
    onSelectTemplate: (CadTemplateSpec) -> Unit,
    onStartScratch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0E1522))
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MODELLI PARAMETRICI PRONTI (SCATOLE IP65 TPU • NATALE • PORTACHIAVI • ISO)",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF00E5FF)
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.testTag("parametric_templates_carousel")
        ) {
            items(templates, key = { it.id }) { tpl ->
                val isSelected = tpl.id == activeTemplateId
                Card(
                    modifier = Modifier
                        .width(230.dp)
                        .clickable { onSelectTemplate(tpl) }
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(tpl.accentHex) else Color(0xFF233248),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .testTag("template_card_${tpl.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFF1A283D) else Color(0xFF131D2D)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color(tpl.accentHex).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = tpl.badge,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(tpl.accentHex),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "${tpl.defaultComponents.size} parti",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF8EA4C2)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = tpl.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = tpl.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF8EA4C2),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CadCommandToolbar(
    onAddPrimitive: (ShapePrimitiveType) -> Unit,
    onToggleQuotes: () -> Unit,
    onOpenInspectorForFeature: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141E2E))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("cad_command_toolbar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolCommandChip("Cubo / Scatola", Icons.Default.Category) {
            onAddPrimitive(ShapePrimitiveType.BOX)
        }
        ToolCommandChip("Cilindro", Icons.Default.AddCircleOutline) {
            onAddPrimitive(ShapePrimitiveType.CYLINDER)
        }
        ToolCommandChip("Poligono (Lati)", Icons.Default.AutoAwesomeMotion) {
            onAddPrimitive(ShapePrimitiveType.POLYGON)
        }
        ToolCommandChip("Loft Fusion", Icons.Default.Layers) {
            onAddPrimitive(ShapePrimitiveType.LOFT)
        }
        ToolCommandChip("Guarnizione TPU", Icons.Default.RoundedCorner) {
            onAddPrimitive(ShapePrimitiveType.TUBE_GASKET)
        }
        ToolCommandChip("Vite ISO/UNC", Icons.Default.Build) {
            onAddPrimitive(ShapePrimitiveType.ISO_SCREW)
        }
        ToolCommandChip("Dado ISO/UNC", Icons.Default.Handyman) {
            onAddPrimitive(ShapePrimitiveType.ISO_NUT)
        }
        ToolCommandChip("Testo 3D", Icons.Default.TextFields) {
            onAddPrimitive(ShapePrimitiveType.TEXT_EMBOSS)
        }
        ToolCommandChip("Quota (D)", Icons.Default.Straighten) {
            onToggleQuotes()
        }
        ToolCommandChip("Raccorda / Fora / Filetta", Icons.Default.Tune) {
            onOpenInspectorForFeature()
        }
    }
}

@Composable
private fun ToolCommandChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(16.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = Color(0xFF1B283C),
            labelColor = Color.White
        )
    )
}

@Composable
private fun AssemblyExplosionBar(
    components: List<com.example.data.CadComponent>,
    selectedComponentId: String?,
    explodeFactor: Float,
    autoLayFlatOnBed: Boolean,
    onSelectComponent: (String) -> Unit,
    onExplodeChange: (Float) -> Unit,
    onToggleLayFlat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF101826))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Component tree chips (tap any component to open its Settings Box!)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PARTI (${components.size}):",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFFF9100)
            )
            components.forEach { comp ->
                val isSelected = comp.id == selectedComponentId
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectComponent(comp.id) },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(comp.colorHex))
                        )
                    },
                    label = { Text("${comp.name} [${comp.materialTag}]") }
                )
            }
        }

        // Assembly Explode Slider + Auto-Lay Flat on Build Plate Button
        if (components.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = null,
                    tint = Color(0xFFFF9100),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = String.format(Locale.US, "Esplodi Assieme: %d%%", (explodeFactor * 100).toInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
                Slider(
                    value = explodeFactor,
                    onValueChange = onExplodeChange,
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .testTag("explode_assembly_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF9100),
                        activeTrackColor = Color(0xFFFF9100)
                    )
                )
                AssistChip(
                    onClick = onToggleLayFlat,
                    label = {
                        Text(
                            text = if (autoLayFlatOnBed) "Vista Assieme" else "Su Piastra Z=0",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (autoLayFlatOnBed) Color(0xFF00E676) else Color(0xFF1B2638),
                        labelColor = if (autoLayFlatOnBed) Color(0xFF002610) else Color(0xFF00E676)
                    ),
                    modifier = Modifier.testTag("lay_flat_on_plate_button")
                )
            }
        }
    }
}
