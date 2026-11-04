package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.CadComponent
import com.example.data.KeychainShape
import com.example.data.ShapePrimitiveType
import com.example.data.ThreadStandard
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Parametric Settings Box that can be docked at the bottom of the screen OR undocked
 * ("Disancora Box") via a dedicated button and dragged freely anywhere over the 3D viewport!
 */
@Composable
fun ParametricSettingsBox(
    component: CadComponent,
    isFloating: Boolean,
    floatingOffsetX: Float,
    floatingOffsetY: Float,
    onToggleFloating: () -> Unit,
    onDragFloating: (dx: Float, dy: Float) -> Unit,
    onUpdateComponent: ((CadComponent) -> CadComponent) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardModifier = if (isFloating) {
        modifier
            .offset { IntOffset(floatingOffsetX.roundToInt(), floatingOffsetY.roundToInt()) }
            .widthIn(min = 300.dp, max = 380.dp)
            .heightIn(max = 420.dp)
    } else {
        modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
    }

    Card(
        modifier = cardModifier
            .border(
                width = if (isFloating) 2.dp else 1.dp,
                color = if (isFloating) Color(0xFFFF9100) else Color(0xFF00E5FF).copy(alpha = 0.6f),
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("parametric_settings_box"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xF2121A28)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFloating) 16.dp else 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header bar (Draggable when floating!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isFloating) Color(0xFF332008) else Color(0xFF1B2638))
                    .pointerInput(isFloating) {
                        if (isFloating) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onDragFloating(dragAmount.x, dragAmount.y)
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(component.colorHex))
                    )
                    Column {
                        Text(
                            text = component.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = if (isFloating) {
                                "BOX DISANCORATO (Trascina qui per spostare)"
                            } else {
                                "${component.primitiveType.labelIt} • ${component.materialTag}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isFloating) Color(0xFFFF9100) else Color(0xFF00E5FF)
                        )
                    }
                }

                // Dedicated Undock / Dock button requested by user:
                // "Quando si preme un disegno e apre i settaggi, metti un tasto che se premuto disancora il setting box e lo puoi portare dove vuoi sullo schermo"
                AssistChip(
                    onClick = onToggleFloating,
                    label = {
                        Text(
                            text = if (isFloating) "Ancora" else "Disancora",
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isFloating) Icons.Default.PushPin else Icons.Default.OpenWith,
                            contentDescription = "Disancora o ancora il box settaggi",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isFloating) Color(0xFFFF9100) else Color(0xFF003642),
                        labelColor = if (isFloating) Color(0xFF1F1100) else Color(0xFF00E5FF),
                        leadingIconContentColor = if (isFloating) Color(0xFF1F1100) else Color(0xFF00E5FF)
                    ),
                    modifier = Modifier.testTag("toggle_floating_inspector_button")
                )

                IconButton(onClick = onDuplicate, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Duplica forma",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Elimina forma",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Chiudi settaggi",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Scrollable Parametric Sliders & Fields
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Custom 3D Text & Keychain Shape (shown prominently for Keychains, Ornaments, Boxes, Text)
                if (
                    component.primitiveType == ShapePrimitiveType.KEYCHAIN_BASE ||
                    component.primitiveType == ShapePrimitiveType.SPHERE ||
                    component.primitiveType == ShapePrimitiveType.TEXT_EMBOSS ||
                    component.primitiveType == ShapePrimitiveType.BOX
                ) {
                    OutlinedTextField(
                        value = component.customText,
                        onValueChange = { txt -> onUpdateComponent { it.copy(customText = txt.take(16)) } },
                        label = { Text("Testo 3D in Rilievo (es. Nome / Etichetta)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_3d_text_input")
                    )

                    if (component.primitiveType == ShapePrimitiveType.KEYCHAIN_BASE) {
                        Text(
                            text = "Forma Base Portachiavi:",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFFF9100)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            KeychainShape.entries.forEach { shapeOption ->
                                FilterChip(
                                    selected = component.keychainShape == shapeOption,
                                    onClick = { onUpdateComponent { it.copy(keychainShape = shapeOption) } },
                                    label = { Text(shapeOption.labelIt) }
                                )
                            }
                        }
                    }
                }

                // 2. Primary Parametric Dimensions (Quota X, Y, Z)
                ParametricSliderRow(
                    label = "Larghezza / Ø (X)",
                    value = component.widthMm,
                    range = 3f..220f,
                    unit = "mm",
                    accent = Color(0xFF00E5FF),
                    onValueChange = { v ->
                        onUpdateComponent {
                            if (it.primitiveType == ShapePrimitiveType.CYLINDER ||
                                it.primitiveType == ShapePrimitiveType.SPHERE ||
                                it.primitiveType == ShapePrimitiveType.ISO_SCREW
                            ) {
                                it.copy(widthMm = v, depthMm = v)
                            } else {
                                it.copy(widthMm = v)
                            }
                        }
                    }
                )

                if (component.primitiveType == ShapePrimitiveType.BOX ||
                    component.primitiveType == ShapePrimitiveType.TUBE_GASKET ||
                    component.primitiveType == ShapePrimitiveType.KEYCHAIN_BASE
                ) {
                    ParametricSliderRow(
                        label = "Profondità (Y)",
                        value = component.depthMm,
                        range = 10f..220f,
                        unit = "mm",
                        accent = Color(0xFFFF9100),
                        onValueChange = { v -> onUpdateComponent { it.copy(depthMm = v) } }
                    )
                }

                ParametricSliderRow(
                    label = "Altezza Estrusione (Z)",
                    value = component.heightMm,
                    range = 1.5f..180f,
                    unit = "mm",
                    accent = Color(0xFF00E676),
                    onValueChange = { v -> onUpdateComponent { it.copy(heightMm = v) } }
                )

                // 3. Polygon Sides & Loft Parameters
                if (component.primitiveType == ShapePrimitiveType.POLYGON ||
                    component.primitiveType == ShapePrimitiveType.LOFT
                ) {
                    ParametricSliderRow(
                        label = "Numero Lati Poligono",
                        value = component.polygonSides.toFloat(),
                        range = 3f..20f,
                        unit = "lati",
                        accent = Color(0xFFE040FB),
                        onValueChange = { v -> onUpdateComponent { it.copy(polygonSides = v.roundToInt()) } }
                    )
                }

                if (component.primitiveType == ShapePrimitiveType.LOFT) {
                    ParametricSliderRow(
                        label = "Scala Profilo Superiore Loft",
                        value = component.loftTopScale * 100f,
                        range = 25f..180f,
                        unit = "%",
                        accent = Color(0xFFE040FB),
                        onValueChange = { v -> onUpdateComponent { it.copy(loftTopScale = v / 100f) } }
                    )
                    ParametricSliderRow(
                        label = "Torsione Elicoidale Loft",
                        value = component.loftTwistDeg,
                        range = -90f..90f,
                        unit = "°",
                        accent = Color(0xFFE040FB),
                        onValueChange = { v -> onUpdateComponent { it.copy(loftTwistDeg = v) } }
                    )
                }

                // 4. Wall Thickness, Fillet (Raccorda), Hole (Fora), and Thread (Filetta ISO / Pollici)
                HorizontalDivider(color = Color(0xFF263852))
                Text(
                    text = "LAVORAZIONI CAD (RACCORDA • FORA • FILETTA ISO/POLLICI)",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF00E5FF)
                )

                if (component.primitiveType == ShapePrimitiveType.BOX ||
                    component.primitiveType == ShapePrimitiveType.TUBE_GASKET
                ) {
                    ParametricSliderRow(
                        label = "Spessore Parete / Guarnizione",
                        value = component.wallThicknessMm,
                        range = 0f..12f,
                        unit = "mm",
                        accent = Color(0xFF00E5FF),
                        onValueChange = { v -> onUpdateComponent { it.copy(wallThicknessMm = v) } }
                    )
                    ParametricSliderRow(
                        label = "Raggio Raccordo Spigoli (Fillet)",
                        value = component.filletRadiusMm,
                        range = 0f..20f,
                        unit = "mm",
                        accent = Color(0xFFFF9100),
                        onValueChange = { v -> onUpdateComponent { it.copy(filletRadiusMm = v) } }
                    )
                }

                ParametricSliderRow(
                    label = "Diametro Foro Passante (Fora Ø)",
                    value = component.holeDiameterMm,
                    range = 0f..60f,
                    unit = "mm",
                    accent = Color(0xFFFF5252),
                    onValueChange = { v -> onUpdateComponent { it.copy(holeDiameterMm = v) } }
                )

                // Thread selector (ISO Metric & Imperial UNC/UNF)
                Text(
                    text = "Filettatura (ISO Metrica o Pollici UNC/UNF):",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF8EA4C2)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ThreadStandard.entries.forEach { std ->
                        FilterChip(
                            selected = component.threadStandard == std,
                            onClick = { onUpdateComponent { it.copy(threadStandard = std) } },
                            label = { Text(std.labelIt) }
                        )
                    }
                }

                // 5. Material Tag & Slicer Object Color (for Multi-Object 3MF Export)
                HorizontalDivider(color = Color(0xFF263852))
                Text(
                    text = "MATERIALE & COLORE OGGETTO 3MF PER SLICER",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFFF9100)
                )
                val materials = listOf("PLA", "PETG", "TPU 95A", "ASA", "ABS", "PLA Silk Gold")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    materials.forEach { mat ->
                        FilterChip(
                            selected = component.materialTag == mat,
                            onClick = { onUpdateComponent { it.copy(materialTag = mat) } },
                            label = { Text(mat) }
                        )
                    }
                }

                val colors = listOf(
                    0xFF00E5FFL, 0xFFFF6D00L, 0xFF00E676L, 0xFFFFD600L,
                    0xFFD50000L, 0xFFE040FBL, 0xFF37474FL, 0xFFFFFFFFL
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    colors.forEach { hex ->
                        val isSelected = component.colorHex == hex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.Black,
                                    shape = CircleShape
                                )
                                .clickable { onUpdateComponent { it.copy(colorHex = hex) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParametricSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    accent: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFECF4FF)
            )
            Text(
                text = String.format(Locale.US, "%.1f %s", value, unit),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Color(0xFF233248)
            ),
            modifier = Modifier.height(26.dp)
        )
    }
}
