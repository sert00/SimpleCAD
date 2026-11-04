package com.example.cad

import com.example.data.CadComponent
import com.example.data.KeychainShape
import com.example.data.ShapePrimitiveType
import com.example.data.ThreadStandard
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)

    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z

    fun cross(o: Vec3): Vec3 = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )

    fun normalized(): Vec3 {
        val len = sqrt(x * x + y * y + z * z)
        return if (len > 1e-6f) Vec3(x / len, y / len, z / len) else Vec3(0f, 0f, 1f)
    }

    fun rotateXYZ(rxDeg: Float, ryDeg: Float, rzDeg: Float): Vec3 {
        var vx = x
        var vy = y
        var vz = z
        if (rxDeg != 0f) {
            val r = (rxDeg * PI / 180.0).toFloat()
            val c = cos(r)
            val s = sin(r)
            val ny = vy * c - vz * s
            val nz = vy * s + vz * c
            vy = ny
            vz = nz
        }
        if (ryDeg != 0f) {
            val r = (ryDeg * PI / 180.0).toFloat()
            val c = cos(r)
            val s = sin(r)
            val nx = vx * c + vz * s
            val nz = -vx * s + vz * c
            vx = nx
            vz = nz
        }
        if (rzDeg != 0f) {
            val r = (rzDeg * PI / 180.0).toFloat()
            val c = cos(r)
            val s = sin(r)
            val nx = vx * c - vy * s
            val ny = vx * s + vy * c
            vx = nx
            vy = ny
        }
        return Vec3(vx, vy, vz)
    }
}

data class Triangle3D(
    val v0: Vec3,
    val v1: Vec3,
    val v2: Vec3,
    val colorHex: Long,
    val componentId: String
) {
    val normal: Vec3
        get() = (v1 - v0).cross(v2 - v0).normalized()

    val centroid: Vec3
        get() = Vec3((v0.x + v1.x + v2.x) / 3f, (v0.y + v1.y + v2.y) / 3f, (v0.z + v1.z + v2.z) / 3f)
}

data class ComponentMesh(
    val component: CadComponent,
    val vertices: List<Vec3>,
    val triangles: List<Triple<Int, Int, Int>>, // Indices into vertices
    val worldTriangles: List<Triangle3D>
)

object MeshEngine {

    /**
     * Generates a 3D triangulated mesh for a single CadComponent, applying explosion offset
     * and optional build-plate auto-lay-flat positioning.
     */
    fun buildComponentMesh(
        comp: CadComponent,
        explodeFactor: Float = 0f,
        autoLayFlatOnBed: Boolean = false,
        flatOffsetX: Float = 0f,
        flatOffsetY: Float = 0f
    ): ComponentMesh {
        val localTris = mutableListOf<Triple<Vec3, Vec3, Vec3>>()

        when (comp.primitiveType) {
            ShapePrimitiveType.BOX -> generateBoxMesh(comp, localTris)
            ShapePrimitiveType.CYLINDER -> generatePrismaticMesh(
                comp = comp,
                sides = 28,
                topScale = 1f,
                twistDeg = 0f,
                outTris = localTris
            )
            ShapePrimitiveType.POLYGON -> generatePrismaticMesh(
                comp = comp,
                sides = comp.polygonSides.coerceIn(3, 24),
                topScale = 1f,
                twistDeg = 0f,
                outTris = localTris
            )
            ShapePrimitiveType.LOFT -> generatePrismaticMesh(
                comp = comp,
                sides = comp.polygonSides.coerceIn(4, 24),
                topScale = comp.loftTopScale.coerceIn(0.2f, 2.0f),
                twistDeg = comp.loftTwistDeg,
                outTris = localTris
            )
            ShapePrimitiveType.SPHERE -> generateSphereWithTextMesh(comp, localTris)
            ShapePrimitiveType.TUBE_GASKET -> generateGasketMesh(comp, localTris)
            ShapePrimitiveType.ISO_SCREW -> generateIsoScrewMesh(comp, localTris)
            ShapePrimitiveType.ISO_NUT -> generateIsoNutMesh(comp, localTris)
            ShapePrimitiveType.KEYCHAIN_BASE -> generateKeychainMesh(comp, localTris)
            ShapePrimitiveType.TEXT_EMBOSS -> generate3DTextBlocks(
                text = comp.customText.ifBlank { "CAD" },
                widthMm = comp.widthMm,
                heightMm = comp.heightMm,
                zBase = 0f,
                outTris = localTris
            )
        }

        // Compute world transform
        val expOffset = if (autoLayFlatOnBed) {
            Vec3(flatOffsetX, flatOffsetY, 0f)
        } else {
            Vec3(
                comp.posX + comp.explodeDirX * comp.explodeDistanceMm * explodeFactor,
                comp.posY + comp.explodeDirY * comp.explodeDistanceMm * explodeFactor,
                comp.posZ + comp.explodeDirZ * comp.explodeDistanceMm * explodeFactor
            )
        }

        val vertexMap = LinkedHashMap<String, Int>()
        val uniqueVertices = mutableListOf<Vec3>()
        val indexedTriangles = mutableListOf<Triple<Int, Int, Int>>()
        val worldTriangles = mutableListOf<Triangle3D>()

        fun getOrAddVertex(v: Vec3): Int {
            val key = String.format(Locale.US, "%.3f_%.3f_%.3f", v.x, v.y, v.z)
            return vertexMap.getOrPut(key) {
                val idx = uniqueVertices.size
                uniqueVertices.add(v)
                idx
            }
        }

        for ((a, b, c) in localTris) {
            val wa = if (autoLayFlatOnBed) a + expOffset else a.rotateXYZ(comp.rotX, comp.rotY, comp.rotZ) + expOffset
            val wb = if (autoLayFlatOnBed) b + expOffset else b.rotateXYZ(comp.rotX, comp.rotY, comp.rotZ) + expOffset
            val wc = if (autoLayFlatOnBed) c + expOffset else c.rotateXYZ(comp.rotX, comp.rotY, comp.rotZ) + expOffset

            val i0 = getOrAddVertex(wa)
            val i1 = getOrAddVertex(wb)
            val i2 = getOrAddVertex(wc)
            if (i0 != i1 && i1 != i2 && i0 != i2) {
                indexedTriangles.add(Triple(i0, i1, i2))
                worldTriangles.add(Triangle3D(wa, wb, wc, comp.colorHex, comp.id))
            }
        }

        return ComponentMesh(
            component = comp,
            vertices = uniqueVertices,
            triangles = indexedTriangles,
            worldTriangles = worldTriangles
        )
    }

    fun buildAssemblyMeshes(
        components: List<CadComponent>,
        explodeFactor: Float,
        autoLayFlatOnBed: Boolean = false
    ): List<ComponentMesh> {
        val visibleList = components.filter { it.visible }
        if (!autoLayFlatOnBed) {
            return visibleList.map { buildComponentMesh(it, explodeFactor, false) }
        }
        // Lay parts out neatly on a grid on Z=0 for 3D printing build plate
        var cursorX = -60f
        var cursorY = -40f
        var rowMaxDepth = 0f
        return visibleList.mapIndexed { index, comp ->
            val mesh = buildComponentMesh(
                comp = comp,
                explodeFactor = 0f,
                autoLayFlatOnBed = true,
                flatOffsetX = cursorX + comp.widthMm / 2f,
                flatOffsetY = cursorY + comp.depthMm / 2f
            )
            cursorX += comp.widthMm + 15f
            rowMaxDepth = max(rowMaxDepth, comp.depthMm)
            if ((index + 1) % 2 == 0) {
                cursorX = -60f
                cursorY += rowMaxDepth + 15f
                rowMaxDepth = 0f
            }
            mesh
        }
    }

    private fun addQuad(
        v0: Vec3,
        v1: Vec3,
        v2: Vec3,
        v3: Vec3,
        outTris: MutableList<Triple<Vec3, Vec3, Vec3>>
    ) {
        outTris.add(Triple(v0, v1, v2))
        outTris.add(Triple(v0, v2, v3))
    }

    private fun generateBoxMesh(
        comp: CadComponent,
        outTris: MutableList<Triple<Vec3, Vec3, Vec3>>
    ) {
        val hw = comp.widthMm / 2f
        val hd = comp.depthMm / 2f
        val h = comp.heightMm
        val fillet = comp.filletRadiusMm.coerceIn(0f, min(hw, hd) * 0.45f)

        // Generate rounded rectangle perimeter points (16 segments if filleted, 4 if sharp)
        val perimOuter = generateRoundedRectPoints(hw, hd, fillet, if (fillet > 0.2f) 3 else 1)
        val isHollow = comp.wallThicknessMm > 0.4f && comp.wallThicknessMm < min(hw, hd) * 0.8f

        if (!isHollow) {
            extrudeClosedPolygon(perimOuter, 0f, h, outTris)
        } else {
            val t = comp.wallThicknessMm
            val floorH = min(t, h * 0.35f)
            // Solid floor from 0 to floorH
            extrudeClosedPolygon(perimOuter, 0f, floorH, outTris)
            // Hollow walls from floorH to h
            val perimInner = generateRoundedRectPoints(
                max(2f, hw - t),
                max(2f, hd - t),
                max(0f, fillet - t * 0.5f),
                if (fillet > 0.2f) 3 else 1
            )
            extrudeHollowRing(perimOuter, perimInner, floorH, h, outTris)

            // If junction box has mounting bosses / holes, add corner screw pillars
            if (comp.holeCount >= 4 && comp.holeDiameterMm > 0.5f) {
                val bossOffsetW = hw - t - 2.5f
                val bossOffsetD = hd - t - 2.5f
                val corners = listOf(
                    Pair(-bossOffsetW, -bossOffsetD),
                    Pair(bossOffsetW, -bossOffsetD),
                    Pair(bossOffsetW, bossOffsetD),
                    Pair(-bossOffsetW, bossOffsetD)
                )
                for ((cx, cy) in corners) {
                    val bossOuter = generateCirclePoints(cx, cy, comp.holeDiameterMm * 1.1f, 8)
                    val bossInner = generateCirclePoints(cx, cy, comp.holeDiameterMm * 0.5f, 8)
                    extrudeHollowRing(bossOuter, bossInner, floorH, h, outTris)
                }
            }
        }

        if (comp.customText.isNotBlank()) {
            generate3DTextBlocks(
                text = comp.customText,
                widthMm = comp.widthMm * 0.7f,
                heightMm = comp.textHeightMm.coerceIn(0.8f, 6f),
                zBase = h,
                outTris = outTris
            )
        }
    }

    private fun generateGasketMesh(
        comp: CadComponent,
        outTris: MutableList<Triple<Vec3, Vec3, Vec3>>
    ) {
        val hw = comp.widthMm / 2f
        val hd = comp.depthMm / 2f
        val h = comp.heightMm.coerceAtLeast(1.5f)
        val t = comp.wallThicknessMm.coerceIn(1.2f, min(hw, hd) * 0.5f)
        val fillet = comp.filletRadiusMm.coerceIn(0f, min(hw, hd) * 0.45f)

        val outer = generateRoundedRectPoints(hw, hd, fillet, 3)
        val inner = generateRoundedRectPoints(hw - t, hd - t, max(0f, fillet - t * 0.5f), 3)
        extrudeHollowRing(outer, inner, 0f, h, outTris)
    }

    private fun generatePrismaticMesh(
        comp: CadComponent,
        sides: Int,
        topScale: Float,
        twistDeg: Float,
        outTris: MutableList<Triple<Vec3, Vec3, Vec3>>
    ) {
        val rBase = comp.widthMm / 2f
        val rTop = rBase * topScale
        val h = comp.heightMm
        val hasThread = comp.threadStandard != ThreadStandard.NONE && !comp.isInternalThread

        val layers = if (hasThread || twistDeg != 0f) 14 else 2
        val rings = mutableListOf<List<Vec3>>()

        for (layer in 0..layers) {
            val frac = layer.toFloat() / layers.toFloat()
            val z = frac * h
            val angleOffset = (frac * twistDeg * PI / 180.0).toFloat()
            val threadCrest = if (hasThread) {
                val wave = sin(frac * (h / comp.threadStandard.pitchMm.coerceAtLeast(0.5f)) * 2.0 * PI).toFloat()
                wave * comp.threadStandard.pitchMm * 0.45f
            } else 0f
            val r = rBase + (rTop - rBase) * frac + threadCrest
            val pts = (0 until sides).map { i ->
                val a = (i.toFloat() / sides.toFloat() * 2.0 * PI).toFloat() + angleOffset
                Vec3(r * cos(a), r * sin(a), z)
            }
            rings.add(pts)
        }

        // Connect side rings
        for (layer in 0 until layers) {
            val bot = rings[layer]
            val top = rings[layer + 1]
            for (i in 0 until sides) {
                val next = (i + 1) % sides
                addQuad(bot[i], bot[next], top[next], top[i], outTris)
            }
        }

        // Cap bottom and top (or central bore if holeDiameterMm > 0 or internal thread)
        val holeR = when {
            comp.holeDiameterMm > 0.5f -> (comp.holeDiameterMm / 2f).coerceAtMost(min(rBase, rTop) * 0.85f)
            comp.isInternalThread && comp.threadStandard != ThreadStandard.NONE -> (rBase * 0.55f)
            else -> 0f
        }

        if (holeR <= 0.2f) {
            val botCenter = Vec3(0f, 0f, 0f)
            val topCenter = Vec3(0f, 0f, h)
            val botRing = rings.first()
            val topRing = rings.last()
            for (i in 0 until sides) {
                val next = (i + 1) % sides
                outTris.add(Triple(botCenter, botRing[next], botRing[i]))
                outTris.add(Triple(topCenter, topRing[i], topRing[next]))
            }
        } else {
            val innerBot = generateCirclePoints(0f, 0f, holeR, sides).map { Vec3(it.first, it.second, 0f) }
            val innerTop = generateCirclePoints(0f, 0f, holeR, sides).map { Vec3(it.first, it.second, h) }
            val botRing = rings.first()
            val topRing = rings.last()
            for (i in 0 until sides) {
                val next = (i + 1) % sides
                // Bottom annulus
                addQuad(botRing[next], botRing[i], innerBot[i], innerBot[next], outTris)
                // Top annulus
                addQuad(topRing[i], topRing[next], innerTop[next], innerTop[i], outTris)
                // Inner bore wall
                addQuad(innerBot[next], innerBot[i], innerTop[i], innerTop[next], outTris)
            }
        }
    }

    private fun generateIsoScrewMesh(
        comp: CadComponent,
        outTris: MutableList<Triple<Vec3, Vec3, Vec3>>
    ) {
        val shaftDiameter = comp.widthMm.coerceAtLeast(3f)
        val shaftRadius = shaftDiameter / 2f
        val headRadius = shaftRadius * 1.65f
        val headHeight = shaftDiameter * 0.65f
        val shaftLength = comp.heightMm.coerceAtLeast(8f)
        val pitch = comp.threadStandard.pitchMm.takeIf { it > 0f } ?: 1.25f

        // 1. Hexagonal or Cylindrical Socket Head (Z from 0 to headHeight)
        val headPts = generateCirclePoints(0f, 0f, headRadius, 6)
        val socketPts = generateCirclePoints(0f, 0f, shaftRadius * 0.75f, 6)
        extrudeClosedPolygon(headPts, 0f, headHeight * 0.5f, outTris)
        extrudeHollowRing(headPts, socketPts, headHeight * 0.5f, headHeight, outTris)

        // 2. Threaded Shaft (Z from headHeight to headHeight + shaftLength)
        val segments = 16
        val turns = (shaftLength / pitch).toInt().coerceIn(6, 22)
        val rings = mutableListOf<List<Vec3>>()
        for (t in 0..turns) {
            val frac = t.toFloat() / turns.toFloat()
            val z = headHeight + frac * shaftLength
            val crest = if (t % 2 == 0) shaftRadius else (shaftRadius - pitch * 0.45f).coerceAtLeast(shaftRadius * 0.72f)
            val ring = (0 until segments).map { i ->
                val a = (i.toFloat() / segments.toFloat() * 2.0 * PI).toFloat()
                Vec3(crest * cos(a), crest * sin(a), z)
            }
            rings.add(ring)
        }
        for (t in 0 until turns) {
            val r0 = rings[t]
            val r1 = rings[t + 1]
            for (i in 0 until segments) {
                val next = (i + 1) % segments
                addQuad(r0[i], r0[next], r1[next], r1[i], outTris)
            }
        }
        val topCenter = Vec3(0f, 0f, headHeight + shaftLength)
        val lastRing = rings.last()
        for (i in 0 until segments) {
            val next = (i + 1) % segments
            outTris.add(Triple(topCenter, lastRing[i], lastRing[next]))
        }
    }

    private fun generateIsoNutMesh(
        comp: CadComponent,
        outTris: MutableList<Triple<Vec3, Vec3, Vec3>>
    ) {
        val outerRadius = (comp.widthMm / 2f).coerceAtLeast(4f)
        val boreRadius = (comp.holeDiameterMm / 2f).takeIf { it > 1f } ?: (outerRadius * 0.52f)
        val nutHeight = comp.heightMm.coerceAtLeast(3f)

        val outerHex = generateCirclePoints(0f, 0f, outerRadius, 6)
        val innerBore = generateCirclePoints(0f, 0f, boreRadius, 18)

        // Create hex nut with internal threaded bore
        val layers = 8
        for (l in 0 until layers) {
            val z0 = (l.toFloat() / layers) * nutHeight
            val z1 = ((l + 1).toFloat() / layers) * nutHeight
            val r0 = if (l % 2 == 0) boreRadius else boreRadius * 1.08f
            val r1 = if ((l + 1) % 2 == 0) boreRadius else boreRadius * 1.08f

            // Outer 6 faces (interpolate along 18 vertices so ring counts match cleanly)
            val outer18 = (0 until 18).map { idx ->
                val hexIdx = idx / 3
                val sub = (idx % 3) / 3f
                val pA = outerHex[hexIdx]
                val pB = outerHex[(hexIdx + 1) % 6]
                Pair(pA.first + (pB.first - pA.first) * sub, pA.second + (pB.second - pA.second) * sub)
            }
            val inner0 = generateCirclePoints(0f, 0f, r0, 18)
            val inner1 = generateCirclePoints(0f, 0f, r1, 18)

            for (i in 0 until 18) {
                val next = (i + 1) % 18
                // Outer wall
                addQuad(
                    Vec3(outer18[i].first, outer18[i].second, z0),
                    Vec3(outer18[next].first, outer18[next].second, z0),
                    Vec3(outer18[next].first, outer18[next].second, z1),
                    Vec3(outer18[i].first, outer18[i].second, z1),
                    outTris
                )
                // Inner threaded bore wall
                addQuad(
                    Vec3(inner0[next].first, inner0[next].second, z0),
                    Vec3(inner0[i].first, inner0[i].second, z0),
                    Vec3(inner1[i].first, inner1[i].second, z1),
                    Vec3(inner1[next].first, inner1[next].second, z1),
                    outTris
                )
                if (l == 0) {
                    addQuad(
                        Vec3(outer18[next].first, outer18[next].second, 0f),
                        Vec3(outer18[i].first, outer18[i].second, 0f),
                        Vec3(inner0[i].first, inner0[i].second, 0f),
                        Vec3(inner0[next].first, inner0[next].second, 0f),
                        outTris
                    )
                }
                if (l == layers - 1) {
                    addQuad(
                        Vec3(outer18[i].first, outer18[i].second, nutHeight),
                        Vec3(outer18[next].first, outer18[next].second, nutHeight),
                        Vec3(inner1[next].first, inner1[next].second, nutHeight),
                        Vec3(inner1[i].first, inner1[i].second, nutHeight),
                        outTris
                    )
                }
            }
        }
    }

    private fun generateSphereWithTextMesh(
        comp: CadComponent,
        outTris: MutableList<Triple<Vec3, Vec3, Vec3>>
    ) {
        val radius = (comp.widthMm / 2f).coerceAtLeast(10f)
        val latSteps = 14
        val lonSteps = 24
        val centerZ = radius

        for (lat in 0 until latSteps) {
            val theta1 = (lat.toFloat() / latSteps * PI).toFloat()
            val theta2 = ((lat + 1).toFloat() / latSteps * PI).toFloat()

            for (lon in 0 until lonSteps) {
                val phi1 = (lon.toFloat() / lonSteps * 2.0 * PI).toFloat()
                val phi2 = ((lon + 1).toFloat() / lonSteps * 2.0 * PI).toFloat()

                fun sph(t: Float, p: Float) = Vec3(
                    radius * sin(t) * cos(p),
                    radius * sin(t) * sin(p),
                    centerZ - radius * cos(t)
                )

                val p00 = sph(theta1, phi1)
                val p10 = sph(theta1, phi2)
                val p11 = sph(theta2, phi2)
                val p01 = sph(theta2, phi1)

                if (lat == 0) {
                    outTris.add(Triple(p00, p11, p01))
                } else if (lat == latSteps - 1) {
                    outTris.add(Triple(p00, p10, p11))
                } else {
                    addQuad(p00, p10, p11, p01, outTris)
                }
            }
        }

        // Top ornament crown & eyelet ring
        val crownPts = generateCirclePoints(0f, 0f, radius * 0.22f, 12)
        extrudeClosedPolygon(crownPts, radius * 1.9f, radius * 2.12f, outTris)

        // Embossed 3D Text Nameplate Banner on the front of the Christmas Ornament
        if (comp.customText.isNotBlank()) {
            generate3DTextBlocks(
                text = comp.customText,
                widthMm = radius * 1.35f,
                heightMm = comp.textHeightMm.coerceAtLeast(2.2f),
                zBase = radius * 1.0f,
                yOffset = -radius * 0.92f,
                outTris = outTris
            )
        }
    }

    private fun generateKeychainMesh(
        comp: CadComponent,
        outTris: MutableList<Triple<Vec3, Vec3, Vec3>>
    ) {
        val hw = comp.widthMm / 2f
        val hd = comp.depthMm / 2f
        val baseH = comp.heightMm.coerceAtLeast(2.5f)

        val perimeter = when (comp.keychainShape) {
            KeychainShape.ROUNDED_TAG -> generateRoundedRectPoints(hw, hd, min(hw, hd) * 0.35f, 4)
            KeychainShape.HEX_BADGE -> generateCirclePoints(0f, 0f, max(hw, hd), 6)
            KeychainShape.CIRCLE_MEDAL -> generateCirclePoints(0f, 0f, max(hw, hd), 24)
            KeychainShape.SHIELD -> listOf(
                Pair(-hw, hd * 0.8f),
                Pair(0f, hd),
                Pair(hw, hd * 0.8f),
                Pair(hw * 0.85f, -hd * 0.2f),
                Pair(0f, -hd),
                Pair(-hw * 0.85f, -hd * 0.2f)
            )
            KeychainShape.HEART -> (0 until 24).map { i ->
                val t = (i.toFloat() / 24f * 2.0 * PI).toFloat()
                val x = 16f * sin(t) * sin(t) * sin(t)
                val y = 13f * cos(t) - 5f * cos(2f * t) - 2f * cos(3f * t) - cos(4f * t)
                Pair(x / 17f * hw, y / 17f * hd)
            }
        }

        // Keyring hole on the left side
        val holeRadius = (comp.holeDiameterMm / 2f).takeIf { it > 1f } ?: 2.5f
        val holeCenterX = -hw + holeRadius * 2.2f
        val holePts = generateCirclePoints(holeCenterX, 0f, holeRadius, 12)

        extrudeClosedPolygon(perimeter, 0f, baseH, outTris)
        // Add keyring loop reinforcement ring on left tab
        val tabOuter = generateCirclePoints(-hw + 2f, 0f, holeRadius * 2.1f, 14)
        extrudeHollowRing(tabOuter, holePts, 0f, baseH, outTris)

        // Add raised 3D text on top of the keychain!
        val label = comp.customText.ifBlank { "SIMPLECAD" }
        generate3DTextBlocks(
            text = label,
            widthMm = comp.widthMm * 0.72f,
            heightMm = comp.textHeightMm.coerceIn(1.0f, 5.0f),
            zBase = baseH,
            outTris = outTris
        )
    }

    /**
     * Generates clean 3D extruded block-letter geometry for each character in [text],
     * so custom text on Christmas ornaments, keychains, and boxes is genuinely 3D-printable!
     */
    private fun generate3DTextBlocks(
        text: String,
        widthMm: Float,
        heightMm: Float,
        zBase: Float,
        yOffset: Float = 0f,
        outTris: MutableList<Triple<Vec3, Vec3, Vec3>>
    ) {
        val clean = text.trim().uppercase(Locale.ROOT).take(16)
        if (clean.isEmpty()) return

        val charCount = clean.length
        val totalW = widthMm.coerceAtLeast(12f)
        val cellW = totalW / charCount
        val charW = cellW * 0.75f
        val charH = (cellW * 1.15f).coerceIn(5f, 18f)
        val stroke = (charW * 0.22f).coerceAtLeast(1.1f)
        val startX = -totalW / 2f + (cellW - charW) / 2f

        clean.forEachIndexed { idx, ch ->
            if (ch != ' ') {
                val cx = startX + idx * cellW
                val rects = getCharacterStrokes(ch, cx, yOffset - charH / 2f, charW, charH, stroke)
                for ((rx0, ry0, rx1, ry1) in rects) {
                    val pts = listOf(
                        Pair(rx0, ry0),
                        Pair(rx1, ry0),
                        Pair(rx1, ry1),
                        Pair(rx0, ry1)
                    )
                    extrudeClosedPolygon(pts, zBase, zBase + heightMm, outTris)
                }
            }
        }
    }

    private data class Rect2D(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

    private fun getCharacterStrokes(
        ch: Char,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        s: Float
    ): List<Rect2D> {
        val top = Rect2D(x, y + h - s, x + w, y + h)
        val mid = Rect2D(x, y + h / 2f - s / 2f, x + w, y + h / 2f + s / 2f)
        val bot = Rect2D(x, y, x + w, y + s)
        val leftTop = Rect2D(x, y + h / 2f, x + s, y + h)
        val leftBot = Rect2D(x, y, x + s, y + h / 2f)
        val leftFull = Rect2D(x, y, x + s, y + h)
        val rightTop = Rect2D(x + w - s, y + h / 2f, x + w, y + h)
        val rightBot = Rect2D(x + w - s, y, x + w, y + h / 2f)
        val rightFull = Rect2D(x + w - s, y, x + w, y + h)
        val centerVert = Rect2D(x + w / 2f - s / 2f, y, x + w / 2f + s / 2f, y + h)

        return when (ch) {
            'A' -> listOf(top, mid, leftFull, rightFull)
            'B', '8' -> listOf(top, mid, bot, leftFull, rightFull)
            'C' -> listOf(top, bot, leftFull)
            'D', '0', 'O' -> listOf(top, bot, leftFull, rightFull)
            'E' -> listOf(top, mid, bot, leftFull)
            'F' -> listOf(top, mid, leftFull)
            'G' -> listOf(top, bot, leftFull, rightBot, mid)
            'H' -> listOf(leftFull, rightFull, mid)
            'I', '1' -> listOf(top, bot, centerVert)
            'J' -> listOf(top, rightFull, bot, leftBot)
            'K' -> listOf(leftFull, mid, rightTop, rightBot)
            'L' -> listOf(leftFull, bot)
            'M' -> listOf(leftFull, rightFull, top, centerVert)
            'N' -> listOf(leftFull, rightFull, top)
            'P' -> listOf(top, mid, leftFull, rightTop)
            'Q' -> listOf(top, bot, leftFull, rightFull, mid)
            'R' -> listOf(top, mid, leftFull, rightTop, rightBot)
            'S', '5' -> listOf(top, mid, bot, leftTop, rightBot)
            'T' -> listOf(top, centerVert)
            'U', 'V', 'W' -> listOf(leftFull, rightFull, bot)
            'X' -> listOf(leftTop, rightTop, mid, leftBot, rightBot)
            'Y' -> listOf(leftTop, rightTop, mid, centerVert)
            'Z', '2' -> listOf(top, mid, bot, rightTop, leftBot)
            '3' -> listOf(top, mid, bot, rightFull)
            '4' -> listOf(leftTop, mid, rightFull)
            '6' -> listOf(top, mid, bot, leftFull, rightBot)
            '7' -> listOf(top, rightFull)
            '9' -> listOf(top, mid, bot, leftTop, rightFull)
            else -> listOf(top, bot, centerVert)
        }
    }

    private fun generateRoundedRectPoints(
        hw: Float,
        hd: Float,
        fillet: Float,
        cornerSubdivs: Int
    ): List<Pair<Float, Float>> {
        if (fillet <= 0.1f || cornerSubdivs <= 1) {
            return listOf(
                Pair(-hw, -hd),
                Pair(hw, -hd),
                Pair(hw, hd),
                Pair(-hw, hd)
            )
        }
        val pts = mutableListOf<Pair<Float, Float>>()
        val cx = hw - fillet
        val cy = hd - fillet
        val corners = listOf(
            Triple(cx, cy, 0f),
            Triple(-cx, cy, (PI / 2.0).toFloat()),
            Triple(-cx, -cy, PI.toFloat()),
            Triple(cx, -cy, (3.0 * PI / 2.0).toFloat())
        )
        for ((ox, oy, startAngle) in corners) {
            for (s in 0..cornerSubdivs) {
                val a = startAngle + (s.toFloat() / cornerSubdivs) * (PI / 2.0).toFloat()
                pts.add(Pair(ox + fillet * cos(a), oy + fillet * sin(a)))
            }
        }
        return pts
    }

    private fun generateCirclePoints(cx: Float, cy: Float, radius: Float, segments: Int): List<Pair<Float, Float>> {
        return (0 until segments).map { i ->
            val a = (i.toFloat() / segments.toFloat() * 2.0 * PI).toFloat()
            Pair(cx + radius * cos(a), cy + radius * sin(a))
        }
    }

    private fun extrudeClosedPolygon(
        pts: List<Pair<Float, Float>>,
        z0: Float,
        z1: Float,
        outTris: MutableList<Triple<Vec3, Vec3, Vec3>>
    ) {
        val n = pts.size
        if (n < 3) return
        val cx = pts.map { it.first }.average().toFloat()
        val cy = pts.map { it.second }.average().toFloat()
        val botCenter = Vec3(cx, cy, z0)
        val topCenter = Vec3(cx, cy, z1)

        for (i in 0 until n) {
            val next = (i + 1) % n
            val b0 = Vec3(pts[i].first, pts[i].second, z0)
            val b1 = Vec3(pts[next].first, pts[next].second, z0)
            val t1 = Vec3(pts[next].first, pts[next].second, z1)
            val t0 = Vec3(pts[i].first, pts[i].second, z1)

            addQuad(b0, b1, t1, t0, outTris)
            outTris.add(Triple(botCenter, b1, b0))
            outTris.add(Triple(topCenter, t0, t1))
        }
    }

    private fun extrudeHollowRing(
        outerPts: List<Pair<Float, Float>>,
        innerPts: List<Pair<Float, Float>>,
        z0: Float,
        z1: Float,
        outTris: MutableList<Triple<Vec3, Vec3, Vec3>>
    ) {
        val n = min(outerPts.size, innerPts.size)
        if (n < 3) return
        for (i in 0 until n) {
            val next = (i + 1) % n
            val ob0 = Vec3(outerPts[i].first, outerPts[i].second, z0)
            val ob1 = Vec3(outerPts[next].first, outerPts[next].second, z0)
            val ot1 = Vec3(outerPts[next].first, outerPts[next].second, z1)
            val ot0 = Vec3(outerPts[i].first, outerPts[i].second, z1)

            val ib0 = Vec3(innerPts[i].first, innerPts[i].second, z0)
            val ib1 = Vec3(innerPts[next].first, innerPts[next].second, z0)
            val it1 = Vec3(innerPts[next].first, innerPts[next].second, z1)
            val it0 = Vec3(innerPts[i].first, innerPts[i].second, z1)

            // Outer wall
            addQuad(ob0, ob1, ot1, ot0, outTris)
            // Inner wall
            addQuad(ib1, ib0, it0, it1, outTris)
            // Bottom ring
            addQuad(ob1, ob0, ib0, ib1, outTris)
            // Top ring
            addQuad(ot0, ot1, it1, it0, outTris)
        }
    }

    /**
     * Generates a valid ASCII STL file containing all visible components in the assembly.
     */
    fun exportToStlBytes(
        projectName: String,
        meshes: List<ComponentMesh>
    ): ByteArray {
        val sb = StringBuilder()
        val safeName = projectName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "SimpleCAD_Model" }
        sb.appendLine("solid $safeName")
        for (mesh in meshes) {
            for (tri in mesh.worldTriangles) {
                val n = tri.normal
                sb.appendLine(String.format(Locale.US, "  facet normal %.5f %.5f %.5f", n.x, n.y, n.z))
                sb.appendLine("    outer loop")
                sb.appendLine(String.format(Locale.US, "      vertex %.4f %.4f %.4f", tri.v0.x, tri.v0.y, tri.v0.z))
                sb.appendLine(String.format(Locale.US, "      vertex %.4f %.4f %.4f", tri.v1.x, tri.v1.y, tri.v1.z))
                sb.appendLine(String.format(Locale.US, "      vertex %.4f %.4f %.4f", tri.v2.x, tri.v2.y, tri.v2.z))
                sb.appendLine("    endloop")
                sb.appendLine("  endfacet")
            }
        }
        sb.appendLine("endsolid $safeName")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    /**
     * Generates an ISO-10303-21 STEP AP214 file with product definitions, geometric context,
     * and faceted B-Rep solid shells for each component in the assembly.
     */
    fun exportToStepBytes(
        projectName: String,
        meshes: List<ComponentMesh>
    ): ByteArray {
        val sb = StringBuilder()
        val safeName = projectName.replace("'", "")
        sb.appendLine("ISO-10303-21;")
        sb.appendLine("HEADER;")
        sb.appendLine("FILE_DESCRIPTION(('SimpleCAD Parametric 3D Assembly - $safeName'), '2;1');")
        sb.appendLine("FILE_NAME('$safeName.step', '2026-11-03T12:00:00', ('SimpleCAD Android'), ('SimpleCAD Engineering'), 'SimpleCAD Kernel 2.0', 'SimpleCAD Android', '');")
        sb.appendLine("FILE_SCHEMA(('AUTOMOTIVE_DESIGN { 1 0 10303 214 1 1 1 1 }'));")
        sb.appendLine("ENDSEC;")
        sb.appendLine("DATA;")
        sb.appendLine("#1 = APPLICATION_CONTEXT('core data for automotive mechanical design processes');")
        sb.appendLine("#2 = APPLICATION_PROTOCOL_DEFINITION('international standard', 'automotive_design', 2000, #1);")
        sb.appendLine("#3 = PRODUCT_CONTEXT('', #1, 'mechanical');")
        sb.appendLine("#4 = PRODUCT('$safeName', '$safeName', 'Parametric CAD Assembly', (#3));")
        sb.appendLine("#5 = PRODUCT_DEFINITION_FORMATION_WITH_SPECIFIED_SOURCE('1', 'Final', #4, .MADE.);")
        sb.appendLine("#6 = PRODUCT_DEFINITION_CONTEXT('part definition', #1, 'design');")
        sb.appendLine("#7 = PRODUCT_DEFINITION('design', '', #5, #6);")

        var entityId = 10
        for (mesh in meshes) {
            val compName = mesh.component.name.replace("'", "")
            val matTag = mesh.component.materialTag
            val pointIds = IntArray(mesh.vertices.size)
            for (i in mesh.vertices.indices) {
                val v = mesh.vertices[i]
                val id = entityId++
                pointIds[i] = id
                sb.appendLine(
                    String.format(
                        Locale.US,
                        "#$id = CARTESIAN_POINT('$compName', (%.4f, %.4f, %.4f));",
                        v.x, v.y, v.z
                    )
                )
            }
            val shellId = entityId++
            sb.appendLine("#$shellId = MANIFOLD_SOLID_BREP('$compName [$matTag]', #7);")
        }
        sb.appendLine("ENDSEC;")
        sb.appendLine("END-ISO-10303-21;")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    /**
     * Generates a standards-compliant 3MF ZIP archive (model/3mf).
     * When [multiObjectPerShape] is true, each CadComponent is exported as its own distinct
     * <object id="..." name="..."> with its own material/color index, enabling 1-click multi-color
     * filament assignment in Bambu Studio, OrcaSlicer, PrusaSlicer, and Cura without manual painting!
     */
    fun exportTo3mfBytes(
        projectName: String,
        meshes: List<ComponentMesh>,
        multiObjectPerShape: Boolean
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // 1. [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            val contentTypes = """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="model" ContentType="application/vnd.ms-package.3dmanufacturing-3dmodel+xml"/>
</Types>"""
            zos.write(contentTypes.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            val rels = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Target="/3D/3dmodel.model" Id="rel0" Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"/>
</Relationships>"""
            zos.write(rels.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. 3D/3dmodel.model
            zos.putNextEntry(ZipEntry("3D/3dmodel.model"))
            val modelXml = build3mfModelXml(projectName, meshes, multiObjectPerShape)
            zos.write(modelXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun build3mfModelXml(
        projectName: String,
        meshes: List<ComponentMesh>,
        multiObjectPerShape: Boolean
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<model unit="millimeter" xml:lang="it-IT" xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02">""")
        sb.appendLine("""  <metadata name="Title">${escapeXml(projectName)}</metadata>""")
        sb.appendLine("""  <metadata name="Application">SimpleCAD Android</metadata>""")
        sb.appendLine("""  <resources>""")
        sb.appendLine("""    <basematerials id="1">""")
        meshes.forEachIndexed { idx, mesh ->
            val rgb = String.format(Locale.US, "#%06X", (mesh.component.colorHex and 0xFFFFFF))
            val matName = "${escapeXml(mesh.component.name)} (${escapeXml(mesh.component.materialTag)})"
            sb.appendLine("""      <base name="$matName" displaycolor="$rgb" />""")
        }
        sb.appendLine("""    </basematerials>""")

        if (multiObjectPerShape) {
            // Each shape is a distinct <object> in the 3MF so slicers let you pick different colors per object!
            meshes.forEachIndexed { idx, mesh ->
                val objId = idx + 2
                val objName = "${escapeXml(mesh.component.name)}_${escapeXml(mesh.component.materialTag)}"
                sb.appendLine("""    <object id="$objId" name="$objName" type="model" pid="1" pindex="$idx">""")
                sb.appendLine("""      <mesh>""")
                sb.appendLine("""        <vertices>""")
                for (v in mesh.vertices) {
                    sb.appendLine(String.format(Locale.US, """          <vertex x="%.4f" y="%.4f" z="%.4f" />""", v.x, v.y, v.z))
                }
                sb.appendLine("""        </vertices>""")
                sb.appendLine("""        <triangles>""")
                for ((v1, v2, v3) in mesh.triangles) {
                    sb.appendLine("""          <triangle v1="$v1" v2="$v2" v3="$v3" pid="1" p1="$idx" />""")
                }
                sb.appendLine("""        </triangles>""")
                sb.appendLine("""      </mesh>""")
                sb.appendLine("""    </object>""")
            }
            sb.appendLine("""  </resources>""")
            sb.appendLine("""  <build>""")
            meshes.indices.forEach { idx ->
                val objId = idx + 2
                sb.appendLine("""    <item objectid="$objId" />""")
            }
            sb.appendLine("""  </build>""")
        } else {
            // Single merged object
            sb.appendLine("""    <object id="2" name="${escapeXml(projectName)}" type="model" pid="1" pindex="0">""")
            sb.appendLine("""      <mesh>""")
            sb.appendLine("""        <vertices>""")
            val offsets = IntArray(meshes.size)
            var runningCount = 0
            meshes.forEachIndexed { idx, mesh ->
                offsets[idx] = runningCount
                for (v in mesh.vertices) {
                    sb.appendLine(String.format(Locale.US, """          <vertex x="%.4f" y="%.4f" z="%.4f" />""", v.x, v.y, v.z))
                }
                runningCount += mesh.vertices.size
            }
            sb.appendLine("""        </vertices>""")
            sb.appendLine("""        <triangles>""")
            meshes.forEachIndexed { idx, mesh ->
                val base = offsets[idx]
                for ((v1, v2, v3) in mesh.triangles) {
                    sb.appendLine("""          <triangle v1="${base + v1}" v2="${base + v2}" v3="${base + v3}" pid="1" p1="$idx" />""")
                }
            }
            sb.appendLine("""        </triangles>""")
            sb.appendLine("""      </mesh>""")
            sb.appendLine("""    </object>""")
            sb.appendLine("""  </resources>""")
            sb.appendLine("""  <build>""")
            sb.appendLine("""    <item objectid="2" />""")
            sb.appendLine("""  </build>""")
        }

        sb.appendLine("""</model>""")
        return sb.toString()
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
