package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cad.CadTemplateSpec
import com.example.cad.CadTemplates
import com.example.cad.ComponentMesh
import com.example.cad.MeshEngine
import com.example.cad.PrinterNetworkClient
import com.example.data.AccelerometerMode
import com.example.data.CadComponent
import com.example.data.CadProject
import com.example.data.CadRepository
import com.example.data.ExportFormat
import com.example.data.KeychainShape
import com.example.data.PrinterProfile
import com.example.data.ShapePrimitiveType
import com.example.data.ThreadStandard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface CloudUiState<out T> {
    data object Loading : CloudUiState<Nothing>
    data class Success<T>(val data: T) : CloudUiState<T>
    data class Error(val message: String) : CloudUiState<Nothing>
}

enum class CadToolModal {
    NONE,
    QUOTE_DIMENSION,
    FILLET_CHAMFER,
    HOLE_DRILL,
    THREAD_ISO_INCH,
    LOFT_BUILDER,
    EXPORT_AND_PRINT,
    CLOUD_PROJECTS
}

data class EditorUiState(
    val activeProjectId: String? = null,
    val projectName: String = "Scatola Derivazione IP65 + TPU",
    val templateType: String = "JUNCTION_BOX_TPU",
    val projectDescription: String = "Scatola elettrica parametrica con coperchio, guarnizione TPU e 4 viti ISO M4",
    val components: List<CadComponent> = CadTemplates.allTemplates.first().defaultComponents,
    val selectedComponentId: String? = CadTemplates.allTemplates.first().defaultComponents.firstOrNull()?.id,
    // Inspector Panel State (Dockable & Detachable Floating Window)
    val isInspectorOpen: Boolean = true,
    val isInspectorFloating: Boolean = false,
    val floatingOffsetX: Float = 24f,
    val floatingOffsetY: Float = 140f,
    // Assembly & Build Plate State
    val explodeFactor: Float = 0.25f,
    val autoLayFlatOnBed: Boolean = false,
    val multiObject3mf: Boolean = true,
    // Viewport & Sensor State
    val showWireframeOverlay: Boolean = true,
    val showDimensionQuotes: Boolean = true,
    val showBuildPlateGrid: Boolean = true,
    val accelerometerMode: AccelerometerMode = AccelerometerMode.OFF,
    val activeModal: CadToolModal = CadToolModal.NONE,
    // Status banner
    val statusMessage: String? = null,
    val isBusy: Boolean = false
)

class SimpleCadViewModel(
    private val repository: CadRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _editorState = MutableStateFlow(EditorUiState())
    val editorState: StateFlow<EditorUiState> = _editorState.asStateFlow()

    val cloudProjectsState: StateFlow<CloudUiState<List<CadProject>>> =
        repository.observeUserProjects(currentUserId)
            .map<List<CadProject>, CloudUiState<List<CadProject>>> { CloudUiState.Success(it) }
            .catch { e -> emit(CloudUiState.Error(e.localizedMessage ?: "Errore caricamento progetti Cloud")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = CloudUiState.Loading
            )

    val printerProfilesState: StateFlow<CloudUiState<List<PrinterProfile>>> =
        repository.observePrinterProfiles(currentUserId)
            .map<List<PrinterProfile>, CloudUiState<List<PrinterProfile>>> { CloudUiState.Success(it) }
            .catch { e -> emit(CloudUiState.Error(e.localizedMessage ?: "Errore caricamento stampanti")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = CloudUiState.Loading
            )

    fun loadTemplate(template: CadTemplateSpec) {
        val cloned = template.defaultComponents.map { it.copy(id = UUID.randomUUID().toString()) }
        _editorState.update {
            it.copy(
                activeProjectId = null,
                projectName = template.title,
                templateType = template.id,
                projectDescription = template.subtitle,
                components = cloned,
                selectedComponentId = cloned.firstOrNull()?.id,
                explodeFactor = if (cloned.size > 1) 0.2f else 0f,
                autoLayFlatOnBed = false,
                isInspectorOpen = true,
                statusMessage = "Template '${template.title}' caricato (${cloned.size} componenti)"
            )
        }
    }

    fun startFromScratch() {
        val initialBox = CadComponent(
            id = UUID.randomUUID().toString(),
            name = "Base Solido 1",
            primitiveType = ShapePrimitiveType.BOX,
            colorHex = 0xFF00E5FF,
            materialTag = "PLA",
            widthMm = 60f,
            depthMm = 60f,
            heightMm = 35f,
            wallThicknessMm = 0f,
            filletRadiusMm = 2f
        )
        _editorState.update {
            it.copy(
                activeProjectId = null,
                projectName = "Nuovo Progetto CAD",
                templateType = "CUSTOM_SCRATCH",
                projectDescription = "Modellazione 3D parametrica da zero",
                components = listOf(initialBox),
                selectedComponentId = initialBox.id,
                explodeFactor = 0f,
                autoLayFlatOnBed = false,
                isInspectorOpen = true,
                statusMessage = "Editor inizializzato da zero. Usa la barra strumenti per aggiungere forme e lavorazioni."
            )
        }
    }

    fun loadCloudProject(project: CadProject) {
        val parsed = CadComponent.deserializeList(project.componentsJson)
        val safeComponents = parsed.ifEmpty { CadTemplates.allTemplates.first().defaultComponents }
        _editorState.update {
            it.copy(
                activeProjectId = project.id,
                projectName = project.name,
                templateType = project.templateType,
                projectDescription = project.description,
                components = safeComponents,
                selectedComponentId = safeComponents.firstOrNull()?.id,
                explodeFactor = project.explodeFactor.toFloat(),
                multiObject3mf = project.multiObject3mf,
                activeModal = CadToolModal.NONE,
                statusMessage = "Progetto Cloud '${project.name}' aperto"
            )
        }
    }

    fun selectComponent(componentId: String?) {
        _editorState.update {
            it.copy(
                selectedComponentId = componentId,
                isInspectorOpen = componentId != null
            )
        }
    }

    fun toggleInspectorOpen() {
        _editorState.update { it.copy(isInspectorOpen = !it.isInspectorOpen) }
    }

    fun toggleInspectorFloating() {
        _editorState.update {
            val nextFloating = !it.isInspectorFloating
            it.copy(
                isInspectorFloating = nextFloating,
                isInspectorOpen = true,
                statusMessage = if (nextFloating) {
                    "Box Settaggi disancorato: trascinalo ovunque sullo schermo!"
                } else {
                    "Box Settaggi ancorato al pannello inferiore"
                }
            )
        }
    }

    fun updateFloatingInspectorOffset(dx: Float, dy: Float) {
        _editorState.update {
            it.copy(
                floatingOffsetX = (it.floatingOffsetX + dx).coerceIn(-120f, 600f),
                floatingOffsetY = (it.floatingOffsetY + dy).coerceIn(20f, 1200f)
            )
        }
    }

    fun addPrimitiveComponent(type: ShapePrimitiveType) {
        val currentList = _editorState.value.components
        val idx = currentList.size + 1
        val palette = listOf(0xFF00E5FF, 0xFFFF9100, 0xFF00E676, 0xFFFFD600, 0xFFE040FB, 0xFFFF1744)
        val color = palette[(idx - 1) % palette.size]
        val highestZ = currentList.maxOfOrNull { it.posZ + it.heightMm } ?: 0f

        val newComp = when (type) {
            ShapePrimitiveType.BOX -> CadComponent(
                name = "Scatola / Cubo $idx",
                primitiveType = ShapePrimitiveType.BOX,
                colorHex = color,
                widthMm = 50f,
                depthMm = 50f,
                heightMm = 25f,
                wallThicknessMm = 0f,
                posZ = highestZ
            )
            ShapePrimitiveType.CYLINDER -> CadComponent(
                name = "Cilindro $idx",
                primitiveType = ShapePrimitiveType.CYLINDER,
                colorHex = color,
                widthMm = 40f,
                depthMm = 40f,
                heightMm = 35f,
                posZ = highestZ
            )
            ShapePrimitiveType.POLYGON -> CadComponent(
                name = "Poligono ${6} Lati $idx",
                primitiveType = ShapePrimitiveType.POLYGON,
                colorHex = color,
                widthMm = 48f,
                depthMm = 48f,
                heightMm = 25f,
                polygonSides = 6,
                posZ = highestZ
            )
            ShapePrimitiveType.LOFT -> CadComponent(
                name = "Loft Conico $idx",
                primitiveType = ShapePrimitiveType.LOFT,
                colorHex = color,
                widthMm = 55f,
                depthMm = 55f,
                heightMm = 40f,
                polygonSides = 8,
                loftTopScale = 0.55f,
                loftTwistDeg = 20f,
                posZ = highestZ
            )
            ShapePrimitiveType.SPHERE -> CadComponent(
                name = "Sfera $idx",
                primitiveType = ShapePrimitiveType.SPHERE,
                colorHex = color,
                widthMm = 50f,
                depthMm = 50f,
                heightMm = 50f,
                customText = "CAD",
                posZ = highestZ
            )
            ShapePrimitiveType.TUBE_GASKET -> CadComponent(
                name = "Guarnizione TPU $idx",
                primitiveType = ShapePrimitiveType.TUBE_GASKET,
                colorHex = 0xFFFF6D00,
                materialTag = "TPU 95A",
                widthMm = 60f,
                depthMm = 60f,
                heightMm = 3.0f,
                wallThicknessMm = 3.5f,
                posZ = highestZ
            )
            ShapePrimitiveType.ISO_SCREW -> CadComponent(
                name = "Vite ISO M6 $idx",
                primitiveType = ShapePrimitiveType.ISO_SCREW,
                colorHex = 0xFFFFD600,
                widthMm = 6f,
                depthMm = 6f,
                heightMm = 20f,
                threadStandard = ThreadStandard.ISO_M6,
                posZ = highestZ
            )
            ShapePrimitiveType.ISO_NUT -> CadComponent(
                name = "Dado ISO M6 $idx",
                primitiveType = ShapePrimitiveType.ISO_NUT,
                colorHex = 0xFF00E676,
                widthMm = 11f,
                depthMm = 11f,
                heightMm = 5f,
                holeDiameterMm = 6f,
                threadStandard = ThreadStandard.ISO_M6,
                isInternalThread = true,
                posZ = highestZ
            )
            ShapePrimitiveType.KEYCHAIN_BASE -> CadComponent(
                name = "Portachiavi $idx",
                primitiveType = ShapePrimitiveType.KEYCHAIN_BASE,
                colorHex = color,
                widthMm = 65f,
                depthMm = 28f,
                heightMm = 3.2f,
                keychainShape = KeychainShape.ROUNDED_TAG,
                customText = "CUSTOM",
                posZ = highestZ
            )
            ShapePrimitiveType.TEXT_EMBOSS -> CadComponent(
                name = "Testo 3D $idx",
                primitiveType = ShapePrimitiveType.TEXT_EMBOSS,
                colorHex = 0xFFFFEA00,
                widthMm = 48f,
                depthMm = 14f,
                heightMm = 2.5f,
                customText = "TESTO",
                posZ = highestZ
            )
        }

        _editorState.update {
            it.copy(
                components = it.components + newComp,
                selectedComponentId = newComp.id,
                isInspectorOpen = true,
                statusMessage = "Aggiunto '${newComp.name}' all'assieme"
            )
        }
    }

    fun updateSelectedComponent(transform: (CadComponent) -> CadComponent) {
        val selectedId = _editorState.value.selectedComponentId ?: return
        _editorState.update { state ->
            state.copy(
                components = state.components.map { comp ->
                    if (comp.id == selectedId) transform(comp) else comp
                }
            )
        }
    }

    fun deleteComponent(componentId: String) {
        _editorState.update { state ->
            if (state.components.size <= 1) {
                return@update state.copy(statusMessage = "L'assieme deve contenere almeno un componente.")
            }
            val filtered = state.components.filterNot { it.id == componentId }
            state.copy(
                components = filtered,
                selectedComponentId = filtered.firstOrNull()?.id,
                statusMessage = "Componente rimosso"
            )
        }
    }

    fun duplicateSelectedComponent() {
        val state = _editorState.value
        val selected = state.components.find { it.id == state.selectedComponentId } ?: return
        val copyComp = selected.copy(
            id = UUID.randomUUID().toString(),
            name = "${selected.name} (Copia)",
            posX = selected.posX + 15f,
            posY = selected.posY + 15f
        )
        _editorState.update {
            it.copy(
                components = it.components + copyComp,
                selectedComponentId = copyComp.id,
                statusMessage = "Duplicato '${copyComp.name}'"
            )
        }
    }

    fun setExplodeFactor(factor: Float) {
        _editorState.update { it.copy(explodeFactor = factor.coerceIn(0f, 1f)) }
    }

    fun toggleAutoLayFlatOnBed() {
        _editorState.update {
            val next = !it.autoLayFlatOnBed
            it.copy(
                autoLayFlatOnBed = next,
                statusMessage = if (next) {
                    "Componenti esplosi e allineati su piastra di stampa (Z=0)"
                } else {
                    "Ripristinata posizione 3D di assieme"
                }
            )
        }
    }

    fun setMultiObject3mf(enabled: Boolean) {
        _editorState.update { it.copy(multiObject3mf = enabled) }
    }

    fun setAccelerometerMode(mode: AccelerometerMode) {
        _editorState.update {
            it.copy(
                accelerometerMode = mode,
                statusMessage = when (mode) {
                    AccelerometerMode.OFF -> "Controllo accelerometro disattivato"
                    AccelerometerMode.ROTATE_OBJECT -> "Accelerometro attivo: inclina il telefono per RUOTARE IL DISEGNO 3D"
                    AccelerometerMode.ROTATE_CAMERA -> "Accelerometro attivo: inclina il telefono per RUOTARE LA VISTA CAMERA"
                }
            )
        }
    }

    fun toggleWireframe() {
        _editorState.update { it.copy(showWireframeOverlay = !it.showWireframeOverlay) }
    }

    fun toggleDimensionQuotes() {
        _editorState.update { it.copy(showDimensionQuotes = !it.showDimensionQuotes) }
    }

    fun openModal(modal: CadToolModal) {
        _editorState.update { it.copy(activeModal = modal) }
    }

    fun updateProjectName(name: String) {
        _editorState.update { it.copy(projectName = name) }
    }

    fun clearStatusMessage() {
        _editorState.update { it.copy(statusMessage = null) }
    }

    fun saveProjectToCloud() {
        val s = _editorState.value
        _editorState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val result = if (s.activeProjectId == null) {
                repository.createProject(
                    name = s.projectName,
                    templateType = s.templateType,
                    description = s.projectDescription,
                    components = s.components,
                    explodeFactor = s.explodeFactor,
                    multiObject3mf = s.multiObject3mf
                ).map { newId ->
                    _editorState.update { it.copy(activeProjectId = newId) }
                }
            } else {
                repository.updateProject(
                    projectId = s.activeProjectId,
                    name = s.projectName,
                    templateType = s.templateType,
                    description = s.projectDescription,
                    components = s.components,
                    explodeFactor = s.explodeFactor,
                    multiObject3mf = s.multiObject3mf
                )
            }
            _editorState.update {
                it.copy(
                    isBusy = false,
                    statusMessage = if (result.isSuccess) {
                        "Progetto '${s.projectName}' salvato nel Cloud Storage!"
                    } else {
                        "Errore salvataggio Cloud: ${result.exceptionOrNull()?.localizedMessage}"
                    }
                )
            }
        }
    }

    fun deleteCloudProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
        }
    }

    fun addPrinterProfile(
        name: String,
        protocol: String,
        endpointUrl: String,
        apiKey: String,
        bedWidthMm: Int,
        bedDepthMm: Int,
        nozzleMm: Float
    ) {
        viewModelScope.launch {
            val res = repository.createPrinterProfile(
                name = name,
                protocol = protocol,
                endpointUrl = endpointUrl,
                apiKey = apiKey,
                bedWidthMm = bedWidthMm,
                bedDepthMm = bedDepthMm,
                nozzleMm = nozzleMm
            )
            _editorState.update {
                it.copy(
                    statusMessage = if (res.isSuccess) {
                        "Stampante '$name' salvata nel Cloud"
                    } else {
                        "Errore salvataggio stampante: ${res.exceptionOrNull()?.localizedMessage}"
                    }
                )
            }
        }
    }

    fun deletePrinterProfile(printerId: String) {
        viewModelScope.launch {
            repository.deletePrinterProfile(printerId)
        }
    }

    fun generateExportBytes(format: ExportFormat): ByteArray {
        val s = _editorState.value
        val meshes: List<ComponentMesh> = MeshEngine.buildAssemblyMeshes(
            components = s.components,
            explodeFactor = s.explodeFactor,
            autoLayFlatOnBed = s.autoLayFlatOnBed
        )
        return when (format) {
            ExportFormat.STL -> MeshEngine.exportToStlBytes(s.projectName, meshes)
            ExportFormat.STEP -> MeshEngine.exportToStepBytes(s.projectName, meshes)
            ExportFormat.THREE_MF -> MeshEngine.exportTo3mfBytes(s.projectName, meshes, s.multiObject3mf)
        }
    }

    fun shareExportedFile(context: Context, format: ExportFormat) {
        _editorState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            try {
                val s = _editorState.value
                val safeName = s.projectName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "SimpleCAD" }
                val fileName = "$safeName.${format.extension}"
                val bytes = withContext(Dispatchers.Default) { generateExportBytes(format) }
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val outFile = File(exportDir, fileName)
                withContext(Dispatchers.IO) { outFile.writeBytes(bytes) }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = format.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TITLE, fileName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Invia $fileName a Slicer / Cloud"))
                _editorState.update {
                    it.copy(
                        isBusy = false,
                        statusMessage = "Esportato $fileName (${bytes.size / 1024} KB)"
                    )
                }
            } catch (e: Exception) {
                _editorState.update {
                    it.copy(
                        isBusy = false,
                        statusMessage = "Errore esportazione: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun sendDirectlyTo3DPrinter(
        printer: PrinterProfile,
        format: ExportFormat,
        startImmediately: Boolean
    ) {
        _editorState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val s = _editorState.value
            val safeName = s.projectName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "SimpleCAD" }
            val fileName = "$safeName.${format.extension}"
            val bytes = withContext(Dispatchers.Default) { generateExportBytes(format) }
            val result = PrinterNetworkClient.sendFileToPrinter(
                printer = printer,
                fileName = fileName,
                fileBytes = bytes,
                mimeType = format.mimeType,
                startPrintImmediately = startImmediately
            )
            _editorState.update {
                it.copy(
                    isBusy = false,
                    statusMessage = result.getOrElse { err ->
                        "Connessione a ${printer.name} (${printer.endpointUrl}): ${err.localizedMessage}"
                    }
                )
            }
        }
    }
}
