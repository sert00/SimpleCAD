package com.example.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cad.MeshEngine
import com.example.cad.Vec3
import com.example.data.AccelerometerMode
import com.example.data.CadComponent
import com.example.data.ThreadStandard
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private data class ProjectedTri(
    val p0: Offset,
    val p1: Offset,
    val p2: Offset,
    val depth: Float,
    val fillColor: Color,
    val strokeColor: Color,
    val isSelected: Boolean,
    val componentId: String
)

private data class ComponentScreenBounds(
    val componentId: String,
    val center: Offset,
    val radiusPx: Float
)

@Composable
fun CadViewport3D(
    components: List<CadComponent>,
    selectedComponentId: String?,
    explodeFactor: Float,
    autoLayFlatOnBed: Boolean,
    showWireframe: Boolean,
    showDimensionQuotes: Boolean,
    accelerometerMode: AccelerometerMode,
    onSelectComponent: (String) -> Unit,
    onAccelerometerModeChange: (AccelerometerMode) -> Unit,
    onToggleWireframe: () -> Unit,
    onToggleDimensionQuotes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()

    // Camera orbit & zoom state
    var camPitchDeg by remember { mutableFloatStateOf(62f) }
    var camYawDeg by remember { mutableFloatStateOf(-38f) }
    var zoomFactor by remember { mutableFloatStateOf(2.15f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Object rotation offset when Accelerometer is in ROTATE_OBJECT mode
    var objTiltXDeg by remember { mutableFloatStateOf(0f) }
    var objTiltYDeg by remember { mutableFloatStateOf(0f) }

    // Live sensor telemetry readout
    var sensorReadoutX by remember { mutableFloatStateOf(0f) }
    var sensorReadoutY by remember { mutableFloatStateOf(0f) }
    var showAccelMenu by remember { mutableStateOf(false) }

    // Real hardware Accelerometer integration
    DisposableEffect(accelerometerMode) {
        if (accelerometerMode == AccelerometerMode.OFF) {
            objTiltXDeg = 0f
            objTiltYDeg = 0f
            return@DisposableEffect onDispose {}
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val values = event?.values ?: return
                if (values.size < 2) return
                val ax = values[0] // Tilt left/right (-10..+10 m/s^2)
                val ay = values[1] // Tilt forward/back (-10..+10 m/s^2)
                sensorReadoutX = ax
                sensorReadoutY = ay

                when (accelerometerMode) {
                    AccelerometerMode.ROTATE_OBJECT -> {
                        // Smooth low-pass filter to rotate the 3D drawing/part
                        val targetRotY = (-ax * 7.5f).coerceIn(-75f, 75f)
                        val targetRotX = ((ay - 4.5f) * 7.5f).coerceIn(-75f, 75f)
                        objTiltYDeg = objTiltYDeg * 0.82f + targetRotY * 0.18f
                        objTiltXDeg = objTiltXDeg * 0.82f + targetRotX * 0.18f
                    }
                    AccelerometerMode.ROTATE_CAMERA -> {
                        // Smooth low-pass filter to orbit the camera view
                        val targetYaw = -38f - ax * 9f
                        val targetPitch = (62f + (ay - 4.5f) * 6.5f).coerceIn(15f, 88f)
                        camYawDeg = camYawDeg * 0.85f + targetYaw * 0.15f
                        camPitchDeg = camPitchDeg * 0.85f + targetPitch * 0.15f
                    }
                    AccelerometerMode.OFF -> Unit
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (accelSensor != null) {
            sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    // Build 3D meshes dynamically from parametric components
    val meshes = remember(components, explodeFactor, autoLayFlatOnBed) {
        MeshEngine.buildAssemblyMeshes(
            components = components,
            explodeFactor = explodeFactor,
            autoLayFlatOnBed = autoLayFlatOnBed
        )
    }

    // Store projected screen bounds per component so tapping a 3D shape selects it and opens its Settings Box
    var componentBoundsList by remember { mutableStateOf<List<ComponentScreenBounds>>(emptyList()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0F18), Color(0xFF111B2B), Color(0xFF090D14))
                )
            )
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    zoomFactor = (zoomFactor * zoom).coerceIn(0.65f, 6.5f)
                    if (zoom > 0.96f && zoom < 1.04f) {
                        camYawDeg = (camYawDeg + pan.x * 0.45f) % 360f
                        camPitchDeg = (camPitchDeg - pan.y * 0.35f).coerceIn(10f, 89f)
                    } else {
                        panOffset += pan
                    }
                }
            }
            .pointerInput(componentBoundsList) {
                detectTapGestures { tapOffset ->
                    val hit = componentBoundsList
                        .sortedBy { hypot((it.center.x - tapOffset.x).toDouble(), (it.center.y - tapOffset.y).toDouble()) }
                        .firstOrNull {
                            hypot((it.center.x - tapOffset.x).toDouble(), (it.center.y - tapOffset.y).toDouble()) <= max(48f, it.radiusPx)
                        }
                    if (hit != null) {
                        onSelectComponent(hit.componentId)
                    }
                }
            }
            .testTag("cad_viewport_3d")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f + panOffset.x
            val cy = size.height * 0.54f + panOffset.y
            val scale = (size.minDimension / 210f) * zoomFactor

            fun projectPoint(pt: Vec3): Pair<Offset, Float> {
                val objRotated = if (accelerometerMode == AccelerometerMode.ROTATE_OBJECT) {
                    pt.rotateXYZ(objTiltXDeg, objTiltYDeg, 0f)
                } else {
                    pt
                }
                // Convert Z-up CAD coordinates into camera space
                val v = objRotated.rotateXYZ(-camPitchDeg, 0f, camYawDeg)
                val perspective = 900f / (900f - v.z.coerceIn(-400f, 600f))
                val sx = cx + v.x * scale * perspective
                val sy = cy - v.y * scale * perspective
                return Pair(Offset(sx, sy), v.z)
            }

            // 1. Draw 3D Build Plate Grid (200x200mm with 20mm steps)
            val gridHalf = 100f
            val step = 20f
            var g = -gridHalf
            while (g <= gridHalf) {
                val isAxis = g == 0f
                val pStartX = projectPoint(Vec3(g, -gridHalf, 0f)).first
                val pEndX = projectPoint(Vec3(g, gridHalf, 0f)).first
                drawLine(
                    color = if (isAxis) Color(0xFF00E676).copy(alpha = 0.65f) else Color(0xFF1F324D).copy(alpha = 0.55f),
                    start = pStartX,
                    end = pEndX,
                    strokeWidth = if (isAxis) 2.5f else 1.2f
                )

                val pStartY = projectPoint(Vec3(-gridHalf, g, 0f)).first
                val pEndY = projectPoint(Vec3(gridHalf, g, 0f)).first
                drawLine(
                    color = if (isAxis) Color(0xFFFF1744).copy(alpha = 0.65f) else Color(0xFF1F324D).copy(alpha = 0.55f),
                    start = pStartY,
                    end = pEndY,
                    strokeWidth = if (isAxis) 2.5f else 1.2f
                )
                g += step
            }

            // Draw Z-up axis indicator at origin
            val origin2D = projectPoint(Vec3(0f, 0f, 0f)).first
            val zTop2D = projectPoint(Vec3(0f, 0f, 70f)).first
            drawLine(
                color = Color(0xFF2979FF).copy(alpha = 0.8f),
                start = origin2D,
                end = zTop2D,
                strokeWidth = 2.8f
            )

            // 2. Project and depth-sort all 3D triangles (Painter's algorithm with directional CAD shading)
            val lightDir = Vec3(0.45f, -0.55f, 0.70f).normalized()
            val projectedList = ArrayList<ProjectedTri>(2048)
            val newBounds = mutableListOf<ComponentScreenBounds>()

            for (mesh in meshes) {
                val isSelected = mesh.component.id == selectedComponentId
                val baseColor = Color(mesh.component.colorHex)
                var minX = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE

                for (tri in mesh.worldTriangles) {
                    val (p0, z0) = projectPoint(tri.v0)
                    val (p1, z1) = projectPoint(tri.v1)
                    val (p2, z2) = projectPoint(tri.v2)

                    minX = min(minX, min(p0.x, min(p1.x, p2.x)))
                    maxX = max(maxX, max(p0.x, max(p1.x, p2.x)))
                    minY = min(minY, min(p0.y, min(p1.y, p2.y)))
                    maxY = max(maxY, max(p0.y, max(p1.y, p2.y)))

                    val avgDepth = (z0 + z1 + z2) / 3f
                    val normal = if (accelerometerMode == AccelerometerMode.ROTATE_OBJECT) {
                        tri.normal.rotateXYZ(objTiltXDeg, objTiltYDeg, 0f)
                    } else {
                        tri.normal
                    }
                    val diffuse = (normal.dot(lightDir) * 0.5f + 0.5f).coerceIn(0.25f, 1.0f)
                    val shaded = Color(
                        red = (baseColor.red * (0.35f + 0.65f * diffuse)).coerceIn(0f, 1f),
                        green = (baseColor.green * (0.35f + 0.65f * diffuse)).coerceIn(0f, 1f),
                        blue = (baseColor.blue * (0.35f + 0.65f * diffuse)).coerceIn(0f, 1f),
                        alpha = if (mesh.component.materialTag.contains("TPU")) 0.88f else 0.96f
                    )
                    val edgeColor = when {
                        isSelected -> Color(0xFF00E5FF)
                        showWireframe -> Color.Black.copy(alpha = 0.35f)
                        else -> shaded
                    }

                    projectedList.add(
                        ProjectedTri(
                            p0 = p0,
                            p1 = p1,
                            p2 = p2,
                            depth = avgDepth,
                            fillColor = shaded,
                            strokeColor = edgeColor,
                            isSelected = isSelected,
                            componentId = mesh.component.id
                        )
                    )
                }

                if (minX < maxX && minY < maxY) {
                    val center = Offset((minX + maxX) / 2f, (minY + maxY) / 2f)
                    val rad = hypot((maxX - minX).toDouble(), (maxY - minY).toDouble()).toFloat() * 0.45f
                    newBounds.add(ComponentScreenBounds(mesh.component.id, center, rad))
                }
            }

            componentBoundsList = newBounds
            projectedList.sortBy { it.depth }

            val path = Path()
            for (ptri in projectedList) {
                path.reset()
                path.moveTo(ptri.p0.x, ptri.p0.y)
                path.lineTo(ptri.p1.x, ptri.p1.y)
                path.lineTo(ptri.p2.x, ptri.p2.y)
                path.close()

                drawPath(path = path, color = ptri.fillColor)
                if (showWireframe || ptri.isSelected) {
                    drawPath(
                        path = path,
                        color = ptri.strokeColor,
                        style = Stroke(width = if (ptri.isSelected) 1.5f else 0.65f)
                    )
                }
            }

            // 3. Draw Fusion 360 style Parametric Dimension Quotes (Quota) on Selected Component
            val selectedComp = components.find { it.id == selectedComponentId }
            if (showDimensionQuotes && selectedComp != null) {
                val zOffset = if (autoLayFlatOnBed) 0f else {
                    selectedComp.posZ + selectedComp.explodeDirZ * selectedComp.explodeDistanceMm * explodeFactor
                }
                val hw = selectedComp.widthMm / 2f
                val hd = selectedComp.depthMm / 2f
                val h = selectedComp.heightMm

                // Width X Dimension Line
                val wStart = projectPoint(Vec3(-hw, -hd - 10f, zOffset)).first
                val wEnd = projectPoint(Vec3(hw, -hd - 10f, zOffset)).first
                drawLine(color = Color(0xFF00E5FF), start = wStart, end = wEnd, strokeWidth = 2.5f)
                drawCircle(color = Color(0xFF00E5FF), radius = 4f, center = wStart)
                drawCircle(color = Color(0xFF00E5FF), radius = 4f, center = wEnd)

                val wLabel = String.format(Locale.US, "X: %.1f mm", selectedComp.widthMm)
                val wMid = Offset((wStart.x + wEnd.x) / 2f - 28f, (wStart.y + wEnd.y) / 2f + 6f)
                drawText(
                    textMeasurer = textMeasurer,
                    text = wLabel,
                    topLeft = wMid,
                    style = TextStyle(
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        background = Color(0xCC0A0F18)
                    )
                )

                // Depth Y Dimension Line
                val dStart = projectPoint(Vec3(hw + 10f, -hd, zOffset)).first
                val dEnd = projectPoint(Vec3(hw + 10f, hd, zOffset)).first
                drawLine(color = Color(0xFFFF9100), start = dStart, end = dEnd, strokeWidth = 2.5f)
                val dLabel = String.format(Locale.US, "Y: %.1f mm", selectedComp.depthMm)
                val dMid = Offset((dStart.x + dEnd.x) / 2f + 6f, (dStart.y + dEnd.y) / 2f)
                drawText(
                    textMeasurer = textMeasurer,
                    text = dLabel,
                    topLeft = dMid,
                    style = TextStyle(
                        color = Color(0xFFFF9100),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        background = Color(0xCC0A0F18)
                    )
                )

                // Height Z Dimension Line
                val hStart = projectPoint(Vec3(-hw - 10f, hd, zOffset)).first
                val hEnd = projectPoint(Vec3(-hw - 10f, hd, zOffset + h)).first
                drawLine(color = Color(0xFF00E676), start = hStart, end = hEnd, strokeWidth = 2.5f)
                val hLabel = buildString {
                    append(String.format(Locale.US, "Z: %.1f mm", selectedComp.heightMm))
                    if (selectedComp.threadStandard != ThreadStandard.NONE) {
                        append(" • ${selectedComp.threadStandard.labelIt}")
                    }
                }
                val hMid = Offset((hStart.x + hEnd.x) / 2f - 40f, (hStart.y + hEnd.y) / 2f - 14f)
                drawText(
                    textMeasurer = textMeasurer,
                    text = hLabel,
                    topLeft = hMid,
                    style = TextStyle(
                        color = Color(0xFF00E676),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        background = Color(0xCC0A0F18)
                    )
                )
            }
        }

        // Top-Left HUD: View Info & Active Accelerometer Telemetry
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = Color(0xCC121A28),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.border(1.dp, Color(0xFF263852), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewInAr,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "VISTA 3D (${meshes.sumOf { it.triangles.size }} tri • ${components.size} parti)",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }

            if (accelerometerMode != AccelerometerMode.OFF) {
                Surface(
                    color = Color(0xDD3E2000),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.border(1.dp, Color(0xFFFF9100), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = null,
                            tint = Color(0xFFFF9100),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "%s [X:%.1f Y:%.1f]",
                                accelerometerMode.labelIt,
                                sensorReadoutX,
                                sensorReadoutY
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFFFDDB8)
                        )
                    }
                }
            }
        }

        // Top-Right HUD: Accelerometer Dual-Choice Button + Wireframe + Quota + Reset View
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Accelerometer Button with Dual Mode Dropdown (Ruota Pezzo vs Ruota Vista Camera)
            Box {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (accelerometerMode != AccelerometerMode.OFF) {
                        Color(0xFFFF9100)
                    } else {
                        Color(0xCC121A28)
                    },
                    modifier = Modifier
                        .border(
                            1.dp,
                            if (accelerometerMode != AccelerometerMode.OFF) Color.White else Color(0xFF2E4361),
                            RoundedCornerShape(12.dp)
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showAccelMenu = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("accelerometer_mode_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = "Controllo Accelerometro",
                            tint = if (accelerometerMode != AccelerometerMode.OFF) Color(0xFF1A0F00) else Color(0xFF00E5FF),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = when (accelerometerMode) {
                                AccelerometerMode.OFF -> "Sensore Giro/Accel"
                                AccelerometerMode.ROTATE_OBJECT -> "Accel: Ruota Disegno"
                                AccelerometerMode.ROTATE_CAMERA -> "Accel: Ruota Vista"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (accelerometerMode != AccelerometerMode.OFF) Color(0xFF1A0F00) else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                DropdownMenu(
                    expanded = showAccelMenu,
                    onDismissRequest = { showAccelMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Ruota Disegno / Pezzo 3D", fontWeight = FontWeight.Bold)
                                Text(
                                    "Inclina il telefono per ruotare il pezzo selezionato",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        onClick = {
                            onAccelerometerModeChange(AccelerometerMode.ROTATE_OBJECT)
                            showAccelMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Ruota Vista Camera", fontWeight = FontWeight.Bold)
                                Text(
                                    "Inclina il telefono per orbitare con la telecamera 3D",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        onClick = {
                            onAccelerometerModeChange(AccelerometerMode.ROTATE_CAMERA)
                            showAccelMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Disattiva Accelerometro (Touch Manuale)") },
                        onClick = {
                            onAccelerometerModeChange(AccelerometerMode.OFF)
                            showAccelMenu = false
                        }
                    )
                }
            }

            // Toggle Quota (Fusion 360 dimensions)
            IconButton(
                onClick = onToggleDimensionQuotes,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (showDimensionQuotes) Color(0xFF003642) else Color(0xCC121A28))
                    .border(1.dp, Color(0xFF00E5FF), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Straighten,
                    contentDescription = "Mostra Quote Dimensionali",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Toggle Wireframe
            IconButton(
                onClick = onToggleWireframe,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (showWireframe) Color(0xFF003642) else Color(0xCC121A28))
                    .border(1.dp, Color(0xFF2E4361), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.GridOn,
                    contentDescription = "Mostra Wireframe",
                    tint = if (showWireframe) Color(0xFF00E5FF) else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Reset Camera View
            IconButton(
                onClick = {
                    camPitchDeg = 62f
                    camYawDeg = -38f
                    zoomFactor = 2.15f
                    panOffset = Offset.Zero
                    objTiltXDeg = 0f
                    objTiltYDeg = 0f
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC121A28))
                    .border(1.dp, Color(0xFF2E4361), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = "Centra Vista 3D",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
