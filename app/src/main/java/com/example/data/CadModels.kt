package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ShapePrimitiveType(val labelIt: String) {
    BOX("Cubo / Scatola"),
    CYLINDER("Cilindro"),
    POLYGON("Poligono Estruso"),
    SPHERE("Sfera / Pallina"),
    LOFT("Loft (2 Profili)"),
    TUBE_GASKET("Guarnizione / Anello TPU"),
    ISO_SCREW("Vite Filettata"),
    ISO_NUT("Dado Filettato"),
    KEYCHAIN_BASE("Base Portachiavi"),
    TEXT_EMBOSS("Testo 3D Estruso")
}

enum class ThreadStandard(val labelIt: String, val pitchMm: Float) {
    NONE("Nessuna Filettatura", 0f),
    ISO_M3("ISO M3x0.5", 0.5f),
    ISO_M4("ISO M4x0.7", 0.7f),
    ISO_M5("ISO M5x0.8", 0.8f),
    ISO_M6("ISO M6x1.0", 1.0f),
    ISO_M8("ISO M8x1.25", 1.25f),
    ISO_M10("ISO M10x1.5", 1.5f),
    ISO_M12("ISO M12x1.75", 1.75f),
    UNC_1_4("1/4\"-20 UNC (Pollici)", 1.27f),
    UNC_3_8("3/8\"-16 UNC (Pollici)", 1.5875f),
    UNF_1_2("1/2\"-20 UNF (Pollici)", 1.27f)
}

enum class KeychainShape(val labelIt: String) {
    ROUNDED_TAG("Targhetta Arrotondata"),
    HEX_BADGE("Esagono Tecnico"),
    CIRCLE_MEDAL("Medaglia Circolare"),
    SHIELD("Scudo Sportivo"),
    HEART("Cuore Smussato")
}

enum class AccelerometerMode(val labelIt: String) {
    OFF("Accelerometro OFF"),
    ROTATE_OBJECT("Ruota Pezzo 3D"),
    ROTATE_CAMERA("Ruota Vista Camera")
}

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    STL("stl", "application/sla", "STL (Mesh Triangolare)"),
    STEP("step", "application/step", "STEP AP214 (CAD Solido ISO 10303)"),
    THREE_MF("3mf", "model/3mf", "3MF (Multi-Oggetto / Colori Slicer)")
}

/**
 * Represents a single 3D parametric component inside an assembly or part.
 */
data class CadComponent(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Componente 1",
    val primitiveType: ShapePrimitiveType = ShapePrimitiveType.BOX,
    val colorHex: Long = 0xFF00E5FF,
    val materialTag: String = "PLA", // e.g., PLA, PETG, TPU 95A, ABS
    // Parametric dimensions in mm
    val widthMm: Float = 50f,
    val depthMm: Float = 50f,
    val heightMm: Float = 30f,
    val wallThicknessMm: Float = 2.4f,
    val polygonSides: Int = 6,
    val loftTopScale: Float = 0.65f,
    val loftTwistDeg: Float = 15f,
    // Features (Fusion 360 style modifiers)
    val filletRadiusMm: Float = 2.0f,
    val holeDiameterMm: Float = 0f,
    val holeCount: Int = 0,
    val threadStandard: ThreadStandard = ThreadStandard.NONE,
    val isInternalThread: Boolean = false,
    // Custom 3D Text & Keychain options
    val customText: String = "",
    val textHeightMm: Float = 2.0f,
    val keychainShape: KeychainShape = KeychainShape.ROUNDED_TAG,
    // 3D Transform & Assembly Explosion vector
    val posX: Float = 0f,
    val posY: Float = 0f,
    val posZ: Float = 0f,
    val rotX: Float = 0f,
    val rotY: Float = 0f,
    val rotZ: Float = 0f,
    val explodeDirX: Float = 0f,
    val explodeDirY: Float = 0f,
    val explodeDirZ: Float = 1f,
    val explodeDistanceMm: Float = 45f,
    val visible: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("primitiveType", primitiveType.name)
        put("colorHex", colorHex)
        put("materialTag", materialTag)
        put("widthMm", widthMm.toDouble())
        put("depthMm", depthMm.toDouble())
        put("heightMm", heightMm.toDouble())
        put("wallThicknessMm", wallThicknessMm.toDouble())
        put("polygonSides", polygonSides)
        put("loftTopScale", loftTopScale.toDouble())
        put("loftTwistDeg", loftTwistDeg.toDouble())
        put("filletRadiusMm", filletRadiusMm.toDouble())
        put("holeDiameterMm", holeDiameterMm.toDouble())
        put("holeCount", holeCount)
        put("threadStandard", threadStandard.name)
        put("isInternalThread", isInternalThread)
        put("customText", customText)
        put("textHeightMm", textHeightMm.toDouble())
        put("keychainShape", keychainShape.name)
        put("posX", posX.toDouble())
        put("posY", posY.toDouble())
        put("posZ", posZ.toDouble())
        put("rotX", rotX.toDouble())
        put("rotY", rotY.toDouble())
        put("rotZ", rotZ.toDouble())
        put("explodeDirX", explodeDirX.toDouble())
        put("explodeDirY", explodeDirY.toDouble())
        put("explodeDirZ", explodeDirZ.toDouble())
        put("explodeDistanceMm", explodeDistanceMm.toDouble())
        put("visible", visible)
    }

    companion object {
        fun fromJson(obj: JSONObject): CadComponent {
            val primitiveName = obj.optString("primitiveType", ShapePrimitiveType.BOX.name)
            val threadName = obj.optString("threadStandard", ThreadStandard.NONE.name)
            val keychainName = obj.optString("keychainShape", KeychainShape.ROUNDED_TAG.name)
            return CadComponent(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "Part"),
                primitiveType = runCatching { ShapePrimitiveType.valueOf(primitiveName) }.getOrDefault(ShapePrimitiveType.BOX),
                colorHex = obj.optLong("colorHex", 0xFF00E5FF),
                materialTag = obj.optString("materialTag", "PLA"),
                widthMm = obj.optDouble("widthMm", 50.0).toFloat(),
                depthMm = obj.optDouble("depthMm", 50.0).toFloat(),
                heightMm = obj.optDouble("heightMm", 30.0).toFloat(),
                wallThicknessMm = obj.optDouble("wallThicknessMm", 2.4).toFloat(),
                polygonSides = obj.optInt("polygonSides", 6),
                loftTopScale = obj.optDouble("loftTopScale", 0.65).toFloat(),
                loftTwistDeg = obj.optDouble("loftTwistDeg", 15.0).toFloat(),
                filletRadiusMm = obj.optDouble("filletRadiusMm", 2.0).toFloat(),
                holeDiameterMm = obj.optDouble("holeDiameterMm", 0.0).toFloat(),
                holeCount = obj.optInt("holeCount", 0),
                threadStandard = runCatching { ThreadStandard.valueOf(threadName) }.getOrDefault(ThreadStandard.NONE),
                isInternalThread = obj.optBoolean("isInternalThread", false),
                customText = obj.optString("customText", ""),
                textHeightMm = obj.optDouble("textHeightMm", 2.0).toFloat(),
                keychainShape = runCatching { KeychainShape.valueOf(keychainName) }.getOrDefault(KeychainShape.ROUNDED_TAG),
                posX = obj.optDouble("posX", 0.0).toFloat(),
                posY = obj.optDouble("posY", 0.0).toFloat(),
                posZ = obj.optDouble("posZ", 0.0).toFloat(),
                rotX = obj.optDouble("rotX", 0.0).toFloat(),
                rotY = obj.optDouble("rotY", 0.0).toFloat(),
                rotZ = obj.optDouble("rotZ", 0.0).toFloat(),
                explodeDirX = obj.optDouble("explodeDirX", 0.0).toFloat(),
                explodeDirY = obj.optDouble("explodeDirY", 0.0).toFloat(),
                explodeDirZ = obj.optDouble("explodeDirZ", 1.0).toFloat(),
                explodeDistanceMm = obj.optDouble("explodeDistanceMm", 45.0).toFloat(),
                visible = obj.optBoolean("visible", true)
            )
        }

        fun serializeList(list: List<CadComponent>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun deserializeList(json: String): List<CadComponent> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                val out = mutableListOf<CadComponent>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    out.add(fromJson(obj))
                }
                out
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

@IgnoreExtraProperties
data class CadProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val templateType: String = "CUSTOM_SCRATCH",
    val description: String = "",
    val componentsJson: String = "[]",
    val explodeFactor: Double = 0.0,
    val multiObject3mf: Boolean = true,
    val partCount: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PrinterProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val protocol: String = "MOONRAKER", // OCTOPRINT, MOONRAKER, PRUSALINK, CLOUD_SLICER
    val endpointUrl: String = "http://192.168.1.100:7125",
    val apiKey: String = "",
    val bedWidthMm: Long = 250L,
    val bedDepthMm: Long = 250L,
    val nozzleMm: Double = 0.4,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
