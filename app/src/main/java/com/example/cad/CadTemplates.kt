package com.example.cad

import com.example.data.CadComponent
import com.example.data.KeychainShape
import com.example.data.ShapePrimitiveType
import com.example.data.ThreadStandard

data class CadTemplateSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val accentHex: Long,
    val defaultComponents: List<CadComponent>
)

object CadTemplates {

    val allTemplates: List<CadTemplateSpec> = listOf(
        // 1. Scatola derivazione elettrica con coperchio e guarnizione TPU + 4 viti M4
        CadTemplateSpec(
            id = "JUNCTION_BOX_TPU",
            title = "Scatola Derivazione IP65 + TPU",
            subtitle = "Scatola elettrica parametrica con coperchio, guarnizione in TPU 95A e 4 viti ISO M4",
            badge = "ASSIEME 4 PARTI",
            accentHex = 0xFF00E5FF,
            defaultComponents = listOf(
                CadComponent(
                    id = "jbox_base",
                    name = "Corpo Scatola Derivazione",
                    primitiveType = ShapePrimitiveType.BOX,
                    colorHex = 0xFF37474F,
                    materialTag = "PETG",
                    widthMm = 110f,
                    depthMm = 80f,
                    heightMm = 48f,
                    wallThicknessMm = 3.2f,
                    filletRadiusMm = 6f,
                    holeDiameterMm = 4.0f,
                    holeCount = 4,
                    posZ = 0f,
                    explodeDirZ = -0.8f,
                    explodeDistanceMm = 35f
                ),
                CadComponent(
                    id = "jbox_gasket",
                    name = "Guarnizione Tenuta TPU",
                    primitiveType = ShapePrimitiveType.TUBE_GASKET,
                    colorHex = 0xFFFF6D00,
                    materialTag = "TPU 95A",
                    widthMm = 108f,
                    depthMm = 78f,
                    heightMm = 2.6f,
                    wallThicknessMm = 3.0f,
                    filletRadiusMm = 5.5f,
                    posZ = 48.5f,
                    explodeDirZ = 0.6f,
                    explodeDistanceMm = 40f
                ),
                CadComponent(
                    id = "jbox_lid",
                    name = "Coperchio Superiore",
                    primitiveType = ShapePrimitiveType.BOX,
                    colorHex = 0xFF00E5FF,
                    materialTag = "PETG",
                    widthMm = 110f,
                    depthMm = 80f,
                    heightMm = 6f,
                    wallThicknessMm = 0f, // Solid plate
                    filletRadiusMm = 6f,
                    holeDiameterMm = 4.2f,
                    holeCount = 4,
                    customText = "IP65 230V",
                    textHeightMm = 1.6f,
                    posZ = 52f,
                    explodeDirZ = 1.4f,
                    explodeDistanceMm = 55f
                ),
                CadComponent(
                    id = "jbox_screws",
                    name = "Vite Chiusura M4x16",
                    primitiveType = ShapePrimitiveType.ISO_SCREW,
                    colorHex = 0xFFFFD600,
                    materialTag = "PLA+",
                    widthMm = 4.0f,
                    depthMm = 4.0f,
                    heightMm = 16f,
                    threadStandard = ThreadStandard.ISO_M4,
                    posX = 46f,
                    posY = 31f,
                    posZ = 58f,
                    explodeDirZ = 2.1f,
                    explodeDistanceMm = 65f
                )
            )
        ),

        // 2. Scatola standard con coperchio ad incastro
        CadTemplateSpec(
            id = "STD_BOX_LID",
            title = "Scatola Standard con Coperchio",
            subtitle = "Contenitore universale parametrico con bordi raccordati e coperchio a battuta",
            badge = "ASSIEME 2 PARTI",
            accentHex = 0xFF2979FF,
            defaultComponents = listOf(
                CadComponent(
                    id = "std_box_body",
                    name = "Vano Contenitore",
                    primitiveType = ShapePrimitiveType.BOX,
                    colorHex = 0xFF1E88E5,
                    materialTag = "PLA",
                    widthMm = 90f,
                    depthMm = 65f,
                    heightMm = 40f,
                    wallThicknessMm = 2.4f,
                    filletRadiusMm = 4f,
                    posZ = 0f,
                    explodeDirZ = -0.5f,
                    explodeDistanceMm = 30f
                ),
                CadComponent(
                    id = "std_box_cover",
                    name = "Coperchio Snap-Fit",
                    primitiveType = ShapePrimitiveType.BOX,
                    colorHex = 0xFF00E676,
                    materialTag = "PLA",
                    widthMm = 90f,
                    depthMm = 65f,
                    heightMm = 5f,
                    wallThicknessMm = 0f,
                    filletRadiusMm = 4f,
                    customText = "BOX",
                    textHeightMm = 1.5f,
                    posZ = 41f,
                    explodeDirZ = 1.2f,
                    explodeDistanceMm = 45f
                )
            )
        ),

        // 3. Pallina di Natale con Textbox personalizzabile
        CadTemplateSpec(
            id = "XMAS_BAUBLE_TEXT",
            title = "Pallina di Natale con Testo 3D",
            subtitle = "Sfera natalizia con occhiello integrato e scritta 3D estrusa bicolore",
            badge = "TESTO 3D",
            accentHex = 0xFFFF1744,
            defaultComponents = listOf(
                CadComponent(
                    id = "xmas_sphere",
                    name = "Sfera Natalizia",
                    primitiveType = ShapePrimitiveType.SPHERE,
                    colorHex = 0xFFD50000,
                    materialTag = "PLA Silk Red",
                    widthMm = 70f,
                    depthMm = 70f,
                    heightMm = 70f,
                    wallThicknessMm = 2.0f,
                    customText = "NATALE",
                    textHeightMm = 2.5f,
                    posZ = 0f,
                    explodeDirY = 0f,
                    explodeDistanceMm = 0f
                ),
                CadComponent(
                    id = "xmas_text_badge",
                    name = "Scritta Frontale Dorata",
                    primitiveType = ShapePrimitiveType.TEXT_EMBOSS,
                    colorHex = 0xFFFFD600,
                    materialTag = "PLA Silk Gold",
                    widthMm = 52f,
                    depthMm = 14f,
                    heightMm = 3.2f,
                    customText = "AUGURI",
                    textHeightMm = 3.2f,
                    posY = -36f,
                    posZ = 32f,
                    explodeDirY = -1.2f,
                    explodeDistanceMm = 35f
                )
            )
        ),

        // 4. Portachiavi con Textbox e scelta della forma
        CadTemplateSpec(
            id = "KEYCHAIN_CUSTOM",
            title = "Portachiavi Parametrico + Testo",
            subtitle = "Scegli forma (Targhetta, Esagono, Medaglia, Scudo, Cuore) e testo in rilievo per cambio colore in Slicer",
            badge = "MULTI-FORMA",
            accentHex = 0xFFFF9100,
            defaultComponents = listOf(
                CadComponent(
                    id = "keychain_tag",
                    name = "Base Portachiavi",
                    primitiveType = ShapePrimitiveType.KEYCHAIN_BASE,
                    colorHex = 0xFF00B0FF,
                    materialTag = "PLA",
                    widthMm = 68f,
                    depthMm = 28f,
                    heightMm = 3.2f,
                    filletRadiusMm = 5f,
                    holeDiameterMm = 5.0f,
                    keychainShape = KeychainShape.ROUNDED_TAG,
                    customText = "SIMO86",
                    textHeightMm = 2.0f,
                    posZ = 0f,
                    explodeDirZ = -0.3f,
                    explodeDistanceMm = 20f
                ),
                CadComponent(
                    id = "keychain_letters",
                    name = "Lettere Rilievo 3MF",
                    primitiveType = ShapePrimitiveType.TEXT_EMBOSS,
                    colorHex = 0xFFFFEA00,
                    materialTag = "PLA Accent",
                    widthMm = 48f,
                    depthMm = 14f,
                    heightMm = 2.2f,
                    customText = "SIMO86",
                    textHeightMm = 2.2f,
                    posZ = 3.2f,
                    explodeDirZ = 1.0f,
                    explodeDistanceMm = 28f
                )
            )
        ),

        // 5. Vite ISO / Pollici parametrica
        CadTemplateSpec(
            id = "ISO_BOLT_TEMPLATE",
            title = "Vite Filettata ISO / Pollici",
            subtitle = "Vite metrica (M3–M12) o imperiale (UNC/UNF) con testa esagonale e passo reale",
            badge = "MECCANICA ISO",
            accentHex = 0xFF00E676,
            defaultComponents = listOf(
                CadComponent(
                    id = "iso_bolt_main",
                    name = "Vite Esagonale ISO M8",
                    primitiveType = ShapePrimitiveType.ISO_SCREW,
                    colorHex = 0xFF00E676,
                    materialTag = "PETG Carbon",
                    widthMm = 8.0f,
                    depthMm = 8.0f,
                    heightMm = 30.0f,
                    threadStandard = ThreadStandard.ISO_M8,
                    isInternalThread = false
                )
            )
        ),

        // 6. Dado ISO / Pollici parametrico
        CadTemplateSpec(
            id = "ISO_NUT_TEMPLATE",
            title = "Dado Esagonale Filettato ISO",
            subtitle = "Dado metrico (M3–M12) o in pollici con foro filettato interno calibrato per stampa 3D",
            badge = "MECCANICA ISO",
            accentHex = 0xFFFFD600,
            defaultComponents = listOf(
                CadComponent(
                    id = "iso_nut_main",
                    name = "Dado ISO M8",
                    primitiveType = ShapePrimitiveType.ISO_NUT,
                    colorHex = 0xFFFFD600,
                    materialTag = "PETG Carbon",
                    widthMm = 15.0f,
                    depthMm = 15.0f,
                    heightMm = 6.5f,
                    holeDiameterMm = 8.0f,
                    threadStandard = ThreadStandard.ISO_M8,
                    isInternalThread = true
                )
            )
        ),

        // 7. Extra Template scelto da noi: Raccordo Loft / Riduttore Aspirazione Tubi (Fusion 360 Loft)
        CadTemplateSpec(
            id = "LOFT_DUCT_ADAPTER",
            title = "Raccordo Loft Tubi & Flange",
            subtitle = "Adattatore conico/poligonale Loft stile Fusion 360 con flangia forata e guarnizione TPU",
            badge = "LOFT FUSION",
            accentHex = 0xFFE040FB,
            defaultComponents = listOf(
                CadComponent(
                    id = "loft_flange",
                    name = "Flangia Base Forata",
                    primitiveType = ShapePrimitiveType.POLYGON,
                    colorHex = 0xFF7C4DFF,
                    materialTag = "ASA",
                    widthMm = 80f,
                    depthMm = 80f,
                    heightMm = 6f,
                    polygonSides = 8,
                    holeDiameterMm = 45f,
                    posZ = 0f,
                    explodeDirZ = -0.6f,
                    explodeDistanceMm = 30f
                ),
                CadComponent(
                    id = "loft_cone",
                    name = "Condotto Loft Conico",
                    primitiveType = ShapePrimitiveType.LOFT,
                    colorHex = 0xFFE040FB,
                    materialTag = "ASA",
                    widthMm = 66f,
                    depthMm = 66f,
                    heightMm = 45f,
                    polygonSides = 16,
                    loftTopScale = 0.58f,
                    loftTwistDeg = 15f,
                    holeDiameterMm = 34f,
                    posZ = 6f,
                    explodeDirZ = 0.8f,
                    explodeDistanceMm = 40f
                )
            )
        ),

        // 8. Extra Template scelto da noi: Supporto Stand Smartphone / Tablet da Scrivania
        CadTemplateSpec(
            id = "PHONE_DESK_STAND",
            title = "Stand Scrivania + Piedini TPU",
            subtitle = "Supporto ergonomico per smartphone/tablet con base antiscivolo in TPU e passacavo",
            badge = "ACCESSORI",
            accentHex = 0xFF18FFFF,
            defaultComponents = listOf(
                CadComponent(
                    id = "stand_body",
                    name = "Corpo Stand Inclinato",
                    primitiveType = ShapePrimitiveType.LOFT,
                    colorHex = 0xFF00B8D4,
                    materialTag = "PLA Matte",
                    widthMm = 75f,
                    depthMm = 85f,
                    heightMm = 65f,
                    polygonSides = 6,
                    loftTopScale = 0.62f,
                    loftTwistDeg = 0f,
                    posZ = 3f,
                    explodeDirZ = 0.7f,
                    explodeDistanceMm = 35f
                ),
                CadComponent(
                    id = "stand_tpu_pad",
                    name = "Base Antiscivolo TPU",
                    primitiveType = ShapePrimitiveType.TUBE_GASKET,
                    colorHex = 0xFFFF6D00,
                    materialTag = "TPU 95A",
                    widthMm = 76f,
                    depthMm = 86f,
                    heightMm = 3f,
                    wallThicknessMm = 6f,
                    filletRadiusMm = 8f,
                    posZ = 0f,
                    explodeDirZ = -0.8f,
                    explodeDistanceMm = 35f
                )
            )
        )
    )
}
