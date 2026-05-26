package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlin.math.*

// ==========================================
// GEMINI DIRECT REST API CONFIGURATION
// ==========================================

data class MPart(val text: String)
data class MContent(val parts: List<MPart>)

data class GeminiRequest(
    val contents: List<MContent>,
    val systemInstruction: MContent? = null
)

data class MCandidate(val content: MContent)
data class GeminiResponse(val candidates: List<MCandidate>?)

interface GeminiApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: GeminiApi = retrofit.create(GeminiApi::class.java)
}

// ==========================================
// BIOLOGICAL NODE GRAPH MODELS
// ==========================================

data class GraphNode(
    val id: String,
    val label: String,
    val type: String, // GENOME, PROTEIN, HOST, DRUG, VARIANT
    val description: String,
    val pdbId: String = "",
    val details: String = "",
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f
)

data class GraphEdge(
    val sourceId: String,
    val targetId: String,
    val relation: String
)

data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class ArchSubsystem(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val codeSnippet: String,
    val schemaInfo: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

data class Offset3D(val x: Float, val y: Float, val z: Float)

data class Atom3D(
    val id: String,
    val name: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val sizeDp: Float = 12f,
    val color: Color,
    val desc: String,
    val elementSymbol: String = ""
)

data class Bond3D(
    val sourceId: String,
    val targetId: String,
    val color: Color,
    val thicknessDp: Float = 2.5f,
    val isDashed: Boolean = false
)

data class VisualStructure(
    val id: String,
    val name: String,
    val category: String, // "VIRUS" or "MATERIAL"
    val subtitle: String,
    val description: String,
    val pdbReference: String,
    val atoms: List<Atom3D>,
    val bonds: List<Bond3D>,
    val baseEnvelopeRadiusDp: Float = 0f,
    val envelopeColor: Color = Color.Transparent
)

object StructureLibrary {
    fun getStructure(id: String): VisualStructure {
        return when (id) {
            "sars_cov_2" -> generateSarsCov2()
            "influenza" -> generateInfluenza()
            "adenovirus" -> generateAdenovirus()
            "bacteriophage" -> generateBacteriophage()
            "graphene" -> generateGraphene()
            "nanotube" -> generateNanotube()
            "silicon" -> generateSilicon()
            "nacl" -> generateNaCl()
            else -> generateSarsCov2()
        }
    }

    private fun generateSarsCov2(): VisualStructure {
        val atoms = mutableListOf<Atom3D>()
        val bonds = mutableListOf<Bond3D>()
        val radEnvelope = 80f
        
        // 1. Spindle Internal Helix RNA
        for (i in 1..15) {
            val angle = i * 0.7f
            val r = 35f
            val y = -40f + i * 5f
            val x = r * cos(angle)
            val z = r * sin(angle)
            atoms.add(
                Atom3D(
                    id = "RNA_$i",
                    name = "RNA Core Segment $i",
                    x = x, y = y, z = z,
                    sizeDp = 6f,
                    color = BioGreen,
                    desc = "Single-stranded viral RNA positive chain molecule code sequence segment of SARS-CoV-2.",
                    elementSymbol = "RNA"
                )
            )
            if (i > 1) {
                bonds.add(Bond3D("RNA_${i - 1}", "RNA_$i", color = BioGreen.copy(alpha = 0.5f), thicknessDp = 1.5f))
            }
        }

        // 2. Embedded Matrix Proteins at r = 80f
        atoms.add(Atom3D("E1", "Envelope E-Channel", radEnvelope * cos(0.2f), 15f, radEnvelope * sin(0.2f), sizeDp = 11f, color = BioGreen, desc = "Pentameric channel membrane protein supporting virion formation.", elementSymbol = "E"))
        atoms.add(Atom3D("E2", "Envelope E-Channel", radEnvelope * cos(2.3f), -20f, radEnvelope * sin(2.3f), sizeDp = 11f, color = BioGreen, desc = "Envelope membrane pore channel.", elementSymbol = "E"))
        
        atoms.add(Atom3D("M1", "Membrane M-Protein", radEnvelope * cos(1.0f), -35f, radEnvelope * sin(1.0f), sizeDp = 9f, color = BioBlue, desc = "Promotes membrane curvature & coordinates spike packing.", elementSymbol = "M"))
        atoms.add(Atom3D("M2", "Membrane M-Protein", radEnvelope * cos(4.0f), 25f, radEnvelope * sin(4.0f), sizeDp = 9f, color = BioBlue, desc = "Organizes primary viral shell assembly.", elementSymbol = "M"))
        atoms.add(Atom3D("M3", "Membrane M-Protein", radEnvelope * cos(5.2f), -5f, radEnvelope * sin(5.2f), sizeDp = 9f, color = BioBlue, desc = "Connects internal RNA matrix to the viral wall.", elementSymbol = "M"))

        // 3. Trimeric Spike Glycoproteins with stalks (extending to r = 135f)
        val spikeAngles = listOf(
            Triple(0.2f, 0.4f, "S1"),
            Triple(-0.4f, 1.2f, "S2"),
            Triple(1.1f, -0.6f, "S3"),
            Triple(-1.3f, -0.8f, "S4"),
            Triple(2.2f, 1.8f, "S5"),
            Triple(-2.1f, -2.2f, "S6")
        )
        spikeAngles.forEachIndexed { i, (theta, phi, sid) ->
            val radSpike = 135f
            val x0 = radSpike * sin(theta) * cos(phi)
            val y0 = radSpike * sin(theta) * sin(phi)
            val z0 = radSpike * cos(theta)

            val wallX = radEnvelope * sin(theta) * cos(phi)
            val wallY = radEnvelope * sin(theta) * sin(phi)
            val wallZ = radEnvelope * cos(theta)

            val color = if (i == 3) BioTeal else BioCoral
            val isRbdUp = i == 0 || i == 3
            val name = if (isRbdUp) "Spike S1 (RBD Active UP)" else "Spike S1 (RBD DOWN)"
            val desc = if (i == 3) {
                "Bound with high-affinity to human lung cellular ACE2 receptor."
            } else if (isRbdUp) {
                "Active receptor-binding domain pointing upwards, primed for host cell fusion."
            } else {
                "Receptor binding domain in down conformation, shielded from human antibody detection."
            }

            val baseId = "S_BASE_$i"
            atoms.add(Atom3D(baseId, "$sid Anchor", wallX, wallY, wallZ, sizeDp = 4f, color = color.copy(alpha = 0.5f), desc = "Spike stalk base embedding into viral lipid envelope."))
            
            atoms.add(
                Atom3D(
                    id = sid,
                    name = name,
                    x = x0, y = y0, z = z0,
                    sizeDp = 14f,
                    color = color,
                    desc = desc,
                    elementSymbol = "S"
                )
            )

            bonds.add(Bond3D(baseId, sid, color = color.copy(alpha = 0.6f), thicknessDp = 3f))
        }

        return VisualStructure(
            id = "sars_cov_2",
            name = "SARS-CoV-2 (COVID-19)",
            category = "VIRUS",
            subtitle = "Enveloped Coronaviridae Virion Assembly",
            description = "Severe acute respiratory syndrome coronavirus 2 (SARS-CoV-2) is an enveloped, positive-sense, single-stranded RNA virus. It triggers respiratory diseases via Spike-ACE2 binding checkpoints.",
            pdbReference = "6VSB / 5X29",
            atoms = atoms,
            bonds = bonds,
            baseEnvelopeRadiusDp = 80f,
            envelopeColor = BorderColor.copy(alpha = 0.4f)
        )
    }

    private fun generateInfluenza(): VisualStructure {
        val atoms = mutableListOf<Atom3D>()
        val bonds = mutableListOf<Bond3D>()
        val radEnvelope = 80f

        // 1. Segmented RNA core strings (4 parallel loops of bead-on-a-string RNA)
        val stringXs = listOf(-20f, -5f, 10f, 25f)
        stringXs.forEachIndexed { sIdx, sx ->
            for (bead in 1..5) {
                val y = -45f + bead * 15f
                val z = sin(bead * 1.2f + sIdx) * 15f
                val bid = "INF_RNA_${sIdx}_$bead"
                atoms.add(
                    Atom3D(
                        id = bid,
                        name = "Segment ${sIdx + 1} RNA bead $bead",
                        x = sx, y = y, z = z,
                        sizeDp = 5.5f,
                        color = BioGreen,
                        desc = "Segregated genomic vRNA strand of Influenza A virus, encoding replication complexes.",
                        elementSymbol = "vRNA"
                    )
                )
                if (bead > 1) {
                    bonds.add(Bond3D("INF_RNA_${sIdx}_${bead - 1}", bid, color = BioGreen.copy(alpha = 0.45f), thicknessDp = 1.3f))
                }
            }
        }

        // 2. M2 Proton Channels
        atoms.add(Atom3D("M2_1", "M2 Proton Channel", radEnvelope * cos(1.5f), 10f, radEnvelope * sin(1.5f), sizeDp = 11f, color = BioCoral, desc = "Integral membrane proton channel crucial for viral uncoating upon entry.", elementSymbol = "M2"))
        atoms.add(Atom3D("M2_2", "M2 Proton Channel", radEnvelope * cos(4.8f), -15f, radEnvelope * sin(4.8f), sizeDp = 11f, color = BioCoral, desc = "Proton selective pore channel model.", elementSymbol = "M2"))

        // 3. HA (Hemagglutinin) Spikes (6 items, Orange/Amber)
        val haAngles = listOf(
            Triple(0.3f, 0.5f, "HA1"),
            Triple(1.2f, -1.0f, "HA2"),
            Triple(-1.1f, -0.6f, "HA3"),
            Triple(2.3f, 2.5f, "HA4"),
            Triple(-0.4f, 2.8f, "HA5"),
            Triple(-2.2f, -1.3f, "HA6")
        )
        haAngles.forEachIndexed { i, (theta, phi, hid) ->
            val radSpike = 135f
            val x0 = radSpike * sin(theta) * cos(phi)
            val y0 = radSpike * sin(theta) * sin(phi)
            val z0 = radSpike * cos(theta)

            val wallX = radEnvelope * sin(theta) * cos(phi)
            val wallY = radEnvelope * sin(theta) * sin(phi)
            val wallZ = radEnvelope * cos(theta)

            atoms.add(Atom3D("HA_BASE_$i", "$hid Anchor", wallX, wallY, wallZ, sizeDp = 4f, color = BioAmber.copy(alpha = 0.4f), desc = "HA stalk membrane contact point."))
            atoms.add(
                Atom3D(
                    id = hid,
                    name = "Hemagglutinin Glycoprotein (HA)",
                    x = x0, y = y0, z = z0,
                    sizeDp = 13f,
                    color = BioAmber,
                    desc = "Mediates high-affinity binding to host sialic acid receptors to initiate infection.",
                    elementSymbol = "HA"
                )
            )
            bonds.add(Bond3D("HA_BASE_$i", hid, color = BioAmber.copy(alpha = 0.5f), thicknessDp = 2.5f))
        }

        // 4. NA (Neuraminidase) Spikes (4 items, Cyan/Teal)
        val naAngles = listOf(
            Triple(0.8f, 1.8f, "NA1"),
            Triple(-1.4f, 0.4f, "NA2"),
            Triple(1.9f, -2.4f, "NA3"),
            Triple(-1.9f, -2.9f, "NA4")
        )
        naAngles.forEachIndexed { i, (theta, phi, nid) ->
            val radSpike = 135f
            val x0 = radSpike * sin(theta) * cos(phi)
            val y0 = radSpike * sin(theta) * sin(phi)
            val z0 = radSpike * cos(theta)

            val wallX = radEnvelope * sin(theta) * cos(phi)
            val wallY = radEnvelope * sin(theta) * sin(phi)
            val wallZ = radEnvelope * cos(theta)

            atoms.add(Atom3D("NA_BASE_$i", "$nid Anchor", wallX, wallY, wallZ, sizeDp = 4f, color = BioTeal.copy(alpha = 0.4f), desc = "NA membrane contact."))
            atoms.add(
                Atom3D(
                    id = nid,
                    name = "Neuraminidase Enzyme (NA)",
                    x = x0, y = y0, z = z0,
                    sizeDp = 13f,
                    color = BioTeal,
                    desc = "Exhibits enzymatic sialidase activity to cleave sialic bonds, enabling newly assembled virions to bud out and release.",
                    elementSymbol = "NA"
                )
            )
            bonds.add(Bond3D("NA_BASE_$i", nid, color = BioTeal.copy(alpha = 0.5f), thicknessDp = 2.5f))
        }

        return VisualStructure(
            id = "influenza",
            name = "Influenza (Flu Virus)",
            category = "VIRUS",
            subtitle = "Segmented Flu Enveloped Orthomyxoviridae",
            description = "Enveloped Orthomyxoviridae pathogen containing a segmented negative-sense RNA genome. Its primary surface features are Hemagglutinin (HA) for cellular entry and Neuraminidase (NA) for viral particle release.",
            pdbReference = "1RU7 / 2MK8",
            atoms = atoms,
            bonds = bonds,
            baseEnvelopeRadiusDp = 80f,
            envelopeColor = BorderColor.copy(alpha = 0.35f)
        )
    }

    private fun generateAdenovirus(): VisualStructure {
        val atoms = mutableListOf<Atom3D>()
        val bonds = mutableListOf<Bond3D>()

        // 12 Vertices of standard Icosahedron (Penton Bases) scaled to R = 72f
        val g = 1.618034f
        val r = 72f
        val length = sqrt(1f + g * g)
        val u = 1f / length * r
        val v = g / length * r
        
        val vertexOffsets = listOf(
            Offset3D(0f, u, v), Offset3D(0f, -u, v), Offset3D(0f, u, -v), Offset3D(0f, -u, -v),
            Offset3D(u, v, 0f), Offset3D(-u, v, 0f), Offset3D(u, -v, 0f), Offset3D(-u, -v, 0f),
            Offset3D(v, 0f, u), Offset3D(-v, 0f, u), Offset3D(v, 0f, -u), Offset3D(-v, 0f, -u)
        )

        // Add 12 Penton vertices in BioTeal
        vertexOffsets.forEachIndexed { i, offset ->
            val vid = "PENT_$i"
            atoms.add(
                Atom3D(
                    id = vid,
                    name = "Penton Base Capsomer $i",
                    x = offset.x, y = offset.y, z = offset.z,
                    sizeDp = 12f,
                    color = BioTeal,
                    desc = "Pentameric capsid capsomer located at the icosahedral vertices. Integrates tightly with protruding fiber receptors.",
                    elementSymbol = "Penton"
                )
            )

            // Add long antenna protruding fiber with receptor knob at 1.9x height
            val mFactor = 1.9f
            val knobId = "KNOB_$i"
            atoms.add(
                Atom3D(
                    id = knobId,
                    name = "Fiber Receptor Knob $i",
                    x = offset.x * mFactor, y = offset.y * mFactor, z = offset.z * mFactor,
                    sizeDp = 9f,
                    color = BioCoral,
                    desc = "Symmetric fiber antenna tip. Binds to host epithelial CAR receptors to dock viral payloads.",
                    elementSymbol = "Knob"
                )
            )
            bonds.add(Bond3D(vid, knobId, color = TextSecondary.copy(alpha = 0.45f), thicknessDp = 1.8f))
        }

        // Draw icosahedron structural bonds. Connect any two vertices that are neighbors (distance is 2 * u ~ 1.05 * r)
        val toleranceMin = 1.0f * r
        val toleranceMax = 1.15f * r
        for (i in vertexOffsets.indices) {
            val a = vertexOffsets[i]
            for (j in (i + 1) until vertexOffsets.size) {
                val b = vertexOffsets[j]
                val dist = sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2) + (a.z - b.z).pow(2))
                if (dist in toleranceMin..toleranceMax) {
                    bonds.add(Bond3D("PENT_$i", "PENT_$j", color = BioTeal.copy(alpha = 0.4f), thicknessDp = 2.5f))
                }
            }
        }

        // Centroid hexons
        val faceCentroids = listOf(
            Triple(0, 4, 8), Triple(0, 8, 9), Triple(0, 9, 1), Triple(0, 1, 4), Triple(4, 10, 8)
        )
        faceCentroids.forEachIndexed { idx, (v1, v2, v3) ->
            val o1 = vertexOffsets[v1]
            val o2 = vertexOffsets[v2]
            val o3 = vertexOffsets[v3]
            val cx = (o1.x + o2.x + o3.x) / 3f
            val cy = (o1.y + o2.y + o3.y) / 3f
            val cz = (o1.z + o2.z + o3.z) / 3f
            atoms.add(Atom3D("HEX_$idx", "Hexon Face Glycoprotein $idx", cx, cy, cz, sizeDp = 7.5f, color = BioBlue.copy(alpha = 0.8f), desc = "Trimeric hexon capsomer constituent forming the main triangular facet plates of the non-enveloped capsid."))
        }

        return VisualStructure(
            id = "adenovirus",
            name = "Adenovirus",
            category = "VIRUS",
            subtitle = "dsDNA Non-Enveloped Icosahedral Capsid",
            description = "Adenoviruses are medium-sized, non-enveloped double-stranded DNA viruses equipped with a regular icosadeltahedral protein capsid shell. Each of the 12 vertices supports a protruding antenna fiber used to bind host CAR receptors.",
            pdbReference = "1N11 / 6SMM",
            atoms = atoms,
            bonds = bonds
        )
    }

    private fun generateBacteriophage(): VisualStructure {
        val atoms = mutableListOf<Atom3D>()
        val bonds = mutableListOf<Bond3D>()

        // 1. Phage Head: Hexagonal Bipyramid Capsid shifted upwards (y = -95f)
        val headY = -95f
        atoms.add(Atom3D("PH_TOP", "Icosahedral Head Apex", 0f, headY - 45f, 0f, sizeDp = 10f, color = BioTeal, desc = "Top vertex of the oblate icosahedral head capsid holding dsDNA.", elementSymbol = "Apex"))
        
        val headR = 30f
        val ringNodes = mutableListOf<String>()
        for (i in 0 until 6) {
            val angle = i * (2 * PI.toFloat() / 6)
            val rx = headR * cos(angle)
            val rz = headR * sin(angle)
            val bid = "PH_RING_$i"
            ringNodes.add(bid)
            atoms.add(
                Atom3D(
                    id = bid,
                    name = "Capsid facet vertex $i",
                    x = rx, y = headY, z = rz,
                    sizeDp = 8f,
                    color = BioTeal,
                    desc = "Crystalline major capsid protein gp23 vertex composing the head shell.",
                    elementSymbol = "gp23"
                )
            )
            bonds.add(Bond3D("PH_TOP", bid, color = BioTeal.copy(alpha = 0.5f), thicknessDp = 2f))
            if (i > 0) {
                bonds.add(Bond3D("PH_RING_${i - 1}", bid, color = BioTeal.copy(alpha = 0.4f), thicknessDp = 1.8f))
            }
        }
        bonds.add(Bond3D("PH_RING_5", "PH_RING_0", color = BioTeal.copy(alpha = 0.4f), thicknessDp = 1.8f))

        atoms.add(Atom3D("PH_NECK", "Capsid Neck (gp10)", 0f, headY + 35f, 0f, sizeDp = 11f, color = BioCoral, desc = "Junction collar interface containing gp10 neck stopper matching capsid to tail sheaths.", elementSymbol = "gp10"))
        ringNodes.forEach { bid ->
            bonds.add(Bond3D(bid, "PH_NECK", color = BioTeal.copy(alpha = 0.5f), thicknessDp = 2f))
        }

        // 2. Contractile Sheath Tail
        val sheathYStart = headY + 35f // -60f
        val sheathYEnd = 20f
        val steps = 4
        val sheathNodes = mutableListOf<String>()
        for (s in 0..steps) {
            val sy = sheathYStart + s * ((sheathYEnd - sheathYStart) / steps)
            val sid = "PH_SHEATH_$s"
            sheathNodes.add(sid)
            atoms.add(
                Atom3D(
                    id = sid,
                    name = "Contractile Tail Segment $s",
                    x = 0f, y = sy, z = 0f,
                    sizeDp = 13f,
                    color = BioTeal,
                    desc = "Helical contractile protein sheath gp18 wraps internal injector tube gp19.",
                    elementSymbol = "gp18"
                )
            )
            if (s > 0) {
                bonds.add(Bond3D("PH_SHEATH_${s - 1}", sid, color = BioTeal, thicknessDp = 3.5f))
            }
        }

        // 3. Spiked Baseplate at y = 20f
        atoms.add(Atom3D("PH_BASEPLATE", "Baseplate Center (gp48)", 0f, sheathYEnd, 0f, sizeDp = 15f, color = BioCoral, desc = "Multi-protein atomic landing gear triggered during host adsorption.", elementSymbol = "Base"))
        bonds.add(Bond3D(sheathNodes.last(), "PH_BASEPLATE", color = BioCoral, thicknessDp = 4f))

        val pinR = 20f
        val pinNodes = mutableListOf<String>()
        for (j in 0 until 6) {
            val angle = j * (2 * PI.toFloat() / 6)
            val px = pinR * cos(angle)
            val pz = pinR * sin(angle)
            val pid = "PH_PIN_$j"
            pinNodes.add(pid)
            atoms.add(Atom3D(pid, "Baseplate Adsorption Pin $j", px, sheathYEnd + 4f, pz, sizeDp = 9f, color = BioAmber, desc = "Highly selective baseplate pins gp11 which anchors to host walls.", elementSymbol = "gp11"))
            bonds.add(Bond3D("PH_BASEPLATE", pid, color = BioAmber.copy(alpha = 0.6f), thicknessDp = 2.5f))
            
            val ex = px * 2.3f
            val ey = sheathYEnd + 25f
            val ez = pz * 2.3f
            val eId = "PH_ELBOW_$j"
            atoms.add(Atom3D(eId, "Tail Fiber Elbow $j", ex, ey, ez, sizeDp = 7f, color = BioGreen, desc = "High-tensile joint of long tail fiber gp37."))
            bonds.add(Bond3D(pid, eId, color = BioGreen.copy(alpha = 0.5f), thicknessDp = 2f))

            val fx = px * 3.2f
            val fy = sheathYEnd + 65f
            val fz = pz * 3.2f
            val fId = "PH_FOOT_$j"
            atoms.add(Atom3D(fId, "Tail Fiber Terminal Foot $j", fx, fy, fz, sizeDp = 5f, color = BioGreen, desc = "Receptor lipopolysaccharide interactive fibers."))
            bonds.add(Bond3D(eId, fId, color = BioGreen.copy(alpha = 0.5f), thicknessDp = 1.8f))
        }

        return VisualStructure(
            id = "bacteriophage",
            name = "Bacteriophage T4",
            category = "VIRUS",
            subtitle = "Nano-Injective Caudoviricetes Myoviridae",
            description = "Bacteriophage T4 is a double-stranded DNA virus that targets Escherichia coli bacterial hosts. It operates like a nano-syringe, utilizing flexible tail fibers (landing gears) to ground, pins to dock, sheaths to contract and inject genomic DNA core.",
            pdbReference = "5Y1B / 3JA4",
            atoms = atoms,
            bonds = bonds
        )
    }

    private fun generateGraphene(): VisualStructure {
        val atoms = mutableListOf<Atom3D>()
        val bonds = mutableListOf<Bond3D>()
        var idCounter = 0
        
        for (row in -2..2) {
            for (col in -2..2) {
                val x = col * 38f + (if (abs(row) % 2 == 1) 19f else 0f)
                val y = row * 33f
                val z = sin(col * 0.9f) * 4f
                val id = "GRAPH_C_$idCounter"
                atoms.add(
                    Atom3D(
                        id = id,
                        name = "C-sp2 Carbon Atom",
                        x = x, y = y, z = z,
                        sizeDp = 11f,
                        color = BioGreen,
                        desc = "Atom-thin flat sp² carbon carbon lattice vertex. Three planar bonds create robust covalent shields.",
                        elementSymbol = "C"
                    )
                )
                idCounter++
            }
        }

        for (i in atoms.indices) {
            val a = atoms[i]
            for (j in (i + 1) until atoms.size) {
                val b = atoms[j]
                val dist = sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2) + (a.z - b.z).pow(2))
                if (dist in 24f..41f) {
                    bonds.add(Bond3D(a.id, b.id, color = BioGreen.copy(alpha = 0.7f), thicknessDp = 2.5f))
                }
            }
        }

        return VisualStructure(
            id = "graphene",
            name = "Graphene Sheet",
            category = "MATERIAL",
            subtitle = "2D Hexagonal Carbon Honeycomb Lattice",
            description = "Graphene is a planar allotrope of carbon arranged in a single-atom thin hexagonal honeycomb lattice. It supports extreme electron mobility, super-tensile load tolerance, and thermal conductance.",
            pdbReference = "N/A (Crystalline Allotrope)",
            atoms = atoms,
            bonds = bonds
        )
    }

    private fun generateNanotube(): VisualStructure {
        val atoms = mutableListOf<Atom3D>()
        val bonds = mutableListOf<Bond3D>()
        var idCounter = 0
        val rings = 4
        val itemsPerRing = 6
        val radius = 45f
        val heightStep = 32f

        for (r in 0 until rings) {
            val y = -50f + r * heightStep
            val angleOffset = if (r % 2 == 1) PI.toFloat() / itemsPerRing else 0f
            for (a in 0 until itemsPerRing) {
                val angle = a * (2 * PI.toFloat() / itemsPerRing) + angleOffset
                val x = radius * cos(angle)
                val z = radius * sin(angle)
                val id = "NT_C_$idCounter"
                atoms.add(
                    Atom3D(
                        id = id,
                        name = "Carbon SWCNT Node",
                        x = x, y = y, z = z,
                        sizeDp = 10.5f,
                        color = BioTeal,
                        desc = "Carbon atom embedded in single-walled carbon nanotube (SWCNT) walls.",
                        elementSymbol = "C"
                    )
                )
                idCounter++
            }
        }

        for (i in atoms.indices) {
            val a = atoms[i]
            for (j in (i + 1) until atoms.size) {
                val b = atoms[j]
                val dist = sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2) + (a.z - b.z).pow(2))
                if (dist in 26f..49f) {
                    bonds.add(Bond3D(a.id, b.id, color = BioTeal.copy(alpha = 0.62f), thicknessDp = 2f))
                }
            }
        }

        return VisualStructure(
            id = "nanotube",
            name = "Carbon Nanotube",
            category = "MATERIAL",
            subtitle = "1D Armchair Single-Walled Cylindrical Tube",
            description = "Carbon Nanotubes (CNTs) are cylindrical molecules consisting of rolled-up Graphene sheets. They have extraordinary mechanical strengths, high electrical current density thresholds, and act as nanoscale conduits.",
            pdbReference = "N/A (SWCNT Molecule)",
            atoms = atoms,
            bonds = bonds
        )
    }

    private fun generateSilicon(): VisualStructure {
        val atoms = mutableListOf<Atom3D>()
        val bonds = mutableListOf<Bond3D>()
        
        atoms.add(
            Atom3D(
                id = "SI_CORE",
                name = "Silicon Core Atom (Si)",
                x = 0f, y = 0f, z = 0f,
                sizeDp = 16f,
                color = BioGreen,
                desc = "Pure Silicon atom with 4 outer valence electrons displaying sp³ covalent geometry.",
                elementSymbol = "Si"
            )
        )

        val primaryOffsets = listOf(
            Offset3D(28f, 28f, 28f),
            Offset3D(-28f, -28f, 28f),
            Offset3D(-28f, 28f, -28f),
            Offset3D(28f, -28f, -28f)
        )
        primaryOffsets.forEachIndexed { i, offset ->
            val pId = "SI_P_$i"
            atoms.add(
                Atom3D(
                    id = pId,
                    name = "Tetrahedral Silicon Node",
                    x = offset.x, y = offset.y, z = offset.z,
                    sizeDp = 12.5f,
                    color = BioTeal,
                    desc = "Primary covalent diamond lattice junction Silicon atom.",
                    elementSymbol = "Si"
                )
            )
            bonds.add(Bond3D("SI_CORE", pId, color = BioTeal.copy(alpha = 0.7f), thicknessDp = 3f))

            val subBranches = when (i) {
                0 -> listOf(Offset3D(56f, 28f, 15f), Offset3D(28f, 56f, 15f), Offset3D(28f, 28f, 56f))
                1 -> listOf(Offset3D(-56f, -28f, 15f), Offset3D(-28f, -56f, 15f), Offset3D(-28f, -28f, 56f))
                2 -> listOf(Offset3D(-56f, 28f, -15f), Offset3D(-28f, 56f, -15f), Offset3D(-28f, 28f, -56f))
                else -> listOf(Offset3D(56f, -28f, -15f), Offset3D(28f, -56f, -15f), Offset3D(28f, -28f, -56f))
            }
            subBranches.forEachIndexed { j, sub ->
                val sbId = "SI_S_${i}_$j"
                atoms.add(
                    Atom3D(
                        id = sbId,
                        name = "Boundary Silicon Covalent Projection",
                        x = sub.x, y = sub.y, z = sub.z,
                        sizeDp = 9f,
                        color = BioBlue,
                        desc = "Crystalline surface Silicon shell interface completing semiconductor lattice units.",
                        elementSymbol = "Si"
                    )
                )
                bonds.add(Bond3D(pId, sbId, color = BioBlue.copy(alpha = 0.45f), thicknessDp = 1.8f))
            }
        }

        return VisualStructure(
            id = "silicon",
            name = "Silicon Crystal Lattice",
            category = "MATERIAL",
            subtitle = "Tetrahedral Diamond Cubic Semiconductor Unit",
            description = "Silicon forms standard tetrahedral covalent arrays within a regular diamond cubic crystal structure. These pure, rigid structures serve as the ultimate substrate for semiconductor electronics, microchips, and diode channels.",
            pdbReference = "N/A (Semiconductor Substrate)",
            atoms = atoms,
            bonds = bonds
        )
    }

    private fun generateNaCl(): VisualStructure {
        val atoms = mutableListOf<Atom3D>()
        val bonds = mutableListOf<Bond3D>()
        var idCounter = 0
        val ionSpacing = 45f

        for (i in -1..1) {
            for (j in -1..1) {
                for (k in -1..1) {
                    val sumCoord = i + j + k
                    val isChlorine = sumCoord % 2 == 0 
                    val x = i * ionSpacing
                    val y = j * ionSpacing
                    val z = k * ionSpacing
                    val id = "ION_$idCounter"

                    val size = if (isChlorine) 15f else 11f
                    val color = if (isChlorine) BioCoral else BioTeal
                    val name = if (isChlorine) "Chlorine Anion (Cl-)" else "Sodium Cation (Na+)"
                    val symbol = if (isChlorine) "Cl-" else "Na+"
                    val desc = if (isChlorine) {
                        "Chloride anion (Cl-) with a full outer valence shell of 18 electrons, yielding high ionic volume."
                    } else {
                        "Sodium cation (Na+) with its outer shell electron completely donated, yielding compact positive charge."
                    }

                    atoms.add(
                        Atom3D(
                            id = id,
                            name = name,
                            x = x, y = y, z = z,
                            sizeDp = size,
                            color = color,
                            desc = desc,
                            elementSymbol = symbol
                        )
                    )
                    idCounter++
                }
            }
        }

        for (i in 0 until 27) {
            val a = atoms[i]
            for (j in (i + 1) until 27) {
                val b = atoms[j]
                val dist = sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2) + (a.z - b.z).pow(2))
                if (dist in (ionSpacing - 3f)..(ionSpacing + 3f)) {
                    bonds.add(Bond3D(a.id, b.id, color = BorderColor.copy(alpha = 0.55f), thicknessDp = 2f))
                }
            }
        }

        return VisualStructure(
            id = "nacl",
            name = "Sodium Chloride (NaCl)",
            category = "MATERIAL",
            subtitle = "Alternating Ionic Face-Centered Cubic Halite Crystal",
            description = "Sodium Chloride (Common Salt) is the canonical model of strong ionic lattices. Positively-charged Sodium (Na+) ions and negatively-charged Chlorine (Cl-) ions alternate along strict perpendicular coordinates, held by high electrostatic forces.",
            pdbReference = "Halite Lattice Compound",
            atoms = atoms,
            bonds = bonds
        )
    }
}

// ==========================================
// VIEWMODEL FOR BUSINESS LOGIC & CHAT
// ==========================================

class SarcovViewModel : ViewModel() {
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage("welcome", "Hello! I am your SARCOV AI Scientific Assistant. I am trained on Nurcholish Adam's SARS-CoV-2 3D Knowledge Graph system architecture (https://github.com/NurcholishAdam/SARS-CoV-2-3D-Knowledge-Graph).\n\nAsk me anything about Neo4j Cypher queries, Protein structures (Spike, Envelope), variants like Omicron, or how the spatial processing pipeline works!", false)
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _selectedStructureId = MutableStateFlow("spike")
    val selectedStructureId: StateFlow<String> = _selectedStructureId.asStateFlow()

    // Interactive custom cypher console simulation query
    private val _currentCypherFilter = MutableStateFlow("MATCH (n) RETURN n")
    val currentCypherFilter: StateFlow<String> = _currentCypherFilter.asStateFlow()

    fun updateCypherFilter(query: String) {
        _currentCypherFilter.value = query
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun selectStructure(structureId: String) {
        _selectedStructureId.value = structureId
    }

    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        val userMsgId = System.currentTimeMillis().toString()
        _chatMessages.value = _chatMessages.value + ChatMessage(userMsgId, text, true)
        _isChatLoading.value = true

        viewModelScope.launch {
            // Check context keywords for automatic visualization tab switching
            val lowerPrompt = text.lowercase()
            var triggeredStructure: String? = null
            var responseDetail = ""
            
            if (lowerPrompt.contains("spike") || lowerPrompt.contains("protein s")) {
                triggeredStructure = "spike"
                responseDetail = "Now highlighting **Spike Protein S (6VSB)** in the dataset explorer."
            } else if (lowerPrompt.contains("envelope") || lowerPrompt.contains("protein e")) {
                triggeredStructure = "env"
                responseDetail = "Now highlighting **Envelope Protein E (5X29)** in the dataset explorer."
            } else if (lowerPrompt.contains("membrane") || lowerPrompt.contains("protein m")) {
                triggeredStructure = "memb"
                responseDetail = "Now highlighting **Membrane Protein M (7MGS)** in the dataset explorer."
            } else if (lowerPrompt.contains("rna") || lowerPrompt.contains("genome") || lowerPrompt.contains("genomic")) {
                triggeredStructure = "rna"
                responseDetail = "Now highlighting **Genomic RNA Sequence (MN908947)** in the dataset explorer."
            } else if (lowerPrompt.contains("ace2") || lowerPrompt.contains("receptor")) {
                triggeredStructure = "ace2"
                responseDetail = "Now highlighting **Host Cell Receptor ACE2 (1R4L)** in the dataset explorer."
            } else if (lowerPrompt.contains("tmpr") || lowerPrompt.contains("protease")) {
                triggeredStructure = "tmpr"
                responseDetail = "Now highlighting **TMPRSS2 Protease Enzyme (7Y10)** in the dataset explorer."
            } else if (lowerPrompt.contains("remdesivir")) {
                triggeredStructure = "remd"
                responseDetail = "Now highlighting **Remdesivir Polymerase Inhibitor** in the dataset explorer."
            } else if (lowerPrompt.contains("paxlovid")) {
                triggeredStructure = "pax"
                responseDetail = "Now highlighting **Paxlovid Protease Inhibitor** in the dataset explorer."
            } else if (lowerPrompt.contains("omicron")) {
                triggeredStructure = "omic"
                responseDetail = "Now highlighting **Omicron Variant B.1.1.529** in the dataset explorer."
            } else if (lowerPrompt.contains("delta")) {
                triggeredStructure = "delt"
                responseDetail = "Now highlighting **Delta Variant B.1.617.2** in the dataset explorer."
            }

            if (triggeredStructure != null) {
                _selectedStructureId.value = triggeredStructure
                _selectedTab.value = 1 // Switch to 3D structures tab
            }

            val systemInstructions = """
                You are "SARCOV AI Assistant", an expert bio-informatics agent designed to explain the system architecture of the SARS-CoV-2 3D Knowledge Graph project (created by researcher Nurcholish Adam).
                The authoritative codebase and paper source repository is: https://github.com/NurcholishAdam/SARS-CoV-2-3D-Knowledge-Graph.
                
                The system consists of:
                1. Data Source Layer: PDB atomic data and genomic strings.
                2. ETL Parser Engine: Python extracting residues, chains, and 3D Euclidean distances between atoms.
                3. Neo4j Spatial Database: High-dimensional knowledge graph linking physical structure nodes (Atom, Chain, Residue, Protein) to public health entities (Variants like Omicron/Delta, Host ACE2 receptors, Antivirals like Paxlovid and Remdesivir).
                4. Visual Frontends: Interactive network renderers.
                
                Always give highly detailed, accurate scientific and structural answers. If requested to provide Cypher queries, output flawless, readable Neo4j Cypher scripts.
                Always be extremely supportive of the researcher Nurcholish Adam's work.
            """.trimIndent()

            val apiKey = try {
                BuildConfig.GEMINI_API_KEY
            } catch (e: Exception) {
                ""
            }

            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                // Return a highly elaborate simulated scientific response if key is missing
                delay(1200)
                var responseSimulated = simulateScientificAnswer(text)
                if (triggeredStructure != null) {
                    responseSimulated = "💡 **[Auto-Visualizer Activated]** $responseDetail\n\n$responseSimulated"
                }
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    System.currentTimeMillis().toString(),
                    "⚠️ [Simulator Mode - API Key not set in Secrets Panel]\n\n" + responseSimulated,
                    false
                )
                _isChatLoading.value = false
                return@launch
            }

            try {
                // Request Gemini text generation
                val req = GeminiRequest(
                    contents = listOf(MContent(parts = listOf(MPart(text = text)))),
                    systemInstruction = MContent(parts = listOf(MPart(text = systemInstructions)))
                )
                val response = withContext(Dispatchers.IO) {
                    GeminiClient.api.generateContent(apiKey, req)
                }
                var rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "No scientific response could be resolved by the engine."
                if (triggeredStructure != null) {
                    rawText = "💡 **[Auto-Visualizer Activated]** $responseDetail\n\n$rawText"
                }
                _chatMessages.value = _chatMessages.value + ChatMessage(System.currentTimeMillis().toString(), rawText, false)
            } catch (e: Exception) {
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    System.currentTimeMillis().toString(),
                    "Science Desk Offline: Failed to query model. Reason: ${e.localizedMessage}\n\nRunning diagnostic simulated answer as helper:\n\n${simulateScientificAnswer(text)}",
                    false
                )
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    private fun simulateScientificAnswer(prompt: String): String {
        val clean = prompt.lowercase()
        return when {
            clean.contains("cypher") || clean.contains("query") || clean.contains("neo4j") -> {
                """
                    Here is a Cypher query template designed to traverse Nurcholish Adam's SARS-CoV-2 3D Knowledge Graph:
                    
                    ```cypher
                    // Find all drugs that inhibit proteins bound to human receptors
                    MATCH (d:Drug)-[i:INHIBITS]->(p:Protein)-[b:BINDS_TO]->(h:HostCell)
                    WHERE b.distance_angstrom < 3.5
                    RETURN d.name AS Antiviral, p.name AS TargetProtein, h.name AS Receptor, b.distance_angstrom AS AtomicDistance
                    ORDER BY AtomicDistance ASC
                    ```
                    
                    This query allows you to isolate active antiviral targets blocking infection checkpoints based on structural 3D proximity.
                """.trimIndent()
            }
            clean.contains("spike") || clean.contains("6vsb") || clean.contains("s protein") -> {
                """
                    The **Spike Protein (S)** is a trimeric glycoprotein critical for entry into host cells via binding to ACE2 receptors. 
                    - **PDB Reference:** `6VSB` (Pre-fusion conformation).
                    - **Key domains:** RBD (Receptor Binding Domain), S1 subunit (receptor interaction), S2 subunit (membrane fusion).
                    - **Inhibitors:** Monoclonal antibodies & fusion-blocking peptide chains.
                    - **Mutations:** Key changes such as E484K or N501Y in variants increase binding affinity or result in immune escape.
                """.trimIndent()
            }
            clean.contains("architecture") || clean.contains("system") || clean.contains("sarcov") -> {
                """
                    Nurcholish Adam's **SARCOV system architecture** is composed of an end-to-end biological translation engine:
                    
                    1. **Bio-Data Broker:** Ingests PDB coordinates and FASTA structural sequence strings.
                    2. **ETL Spatial Vectorizer:** Calculates pairwise Euclidean vectors between alpha-carbons (C-alpha residues) to construct 3D Delaunay networks.
                    3. **Neo4j DB Store:** Maps these spatial distances to graph structures.
                    4. **Interactive Graph UI:** Displays direct links among amino-acids, variants, and inhibitors in interactive web/mobile modules.
                """.trimIndent()
            }
            clean.contains("omicron") || clean.contains("variant") || clean.contains("delta") -> {
                """
                    SARS-CoV-2 **Variants** introduce significant structural shifts in structural nodes:
                    - **Omicron (B.1.1.529):** Carries over 30 mutations in the Spike protein alone (e.g. K417N, T478K, N501Y).
                    - **Delta (B.1.617.2):** Introduced L452R and T478K, which strengthened cell fusion and accelerated viral replication rates.
                    - **Graph Representation:** Nodes belonging to `Variant` are connected through a `:MUTATEST_TO` edge to the `Spike` protein node containing changed atomic structural properties.
                """.trimIndent()
            }
            else -> {
                """
                    Understood. In the context of the SARS-CoV-2 3D Knowledge Graph:
                    
                    The 3D atomic structures are derived from cryogenic electron microscopy (cryo-EM) data. By structural translation, every 3D coordinate (Atom x,y,z) is treated as a leaf node linked up to Residues, then to Peptides/Chains, Proteins, and drug compounds.
                    
                    Feel free to ask for specific **Cypher Neo4j codes**, details on **Spike RBD binding kinetics**, or the **ETL Spatial Parser system architecture**.
                """.trimIndent()
            }
        }
    }
}

// ==========================================
// CENTRAL APPLICATION ACTIVITY
// ==========================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    MainDashboard(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

// ==========================================
// COMPOSABLE DASHBOARD MAIN PANEL
// ==========================================

@Composable
fun MainDashboard(
    modifier: Modifier = Modifier,
    viewModel: SarcovViewModel = viewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    
    val tabs = listOf(
        "🗃️ Dataset Explorer",
        "🦠 Viral 3D Model",
        "🛰️ Architecture",
        "🤖 Gemini Assistant"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // App Header Toolbar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TransparentLogoImage(
                        size = 36.dp,
                        contentDescription = "SARS-CoV-19 Premium Logo"
                    )
                }
                
                // Status Lights & GitHub Integration Card
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        onClick = {
                            viewModel.selectTab(1) // Switch to Viral 3D Model tab
                        },
                        colors = CardDefaults.cardColors(containerColor = BioCoral.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, BioCoral.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("virion_header_card")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Switch to Viral 3D Model",
                                tint = BioCoral,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "VIRAL 3D MODEL",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(BioGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SYSTEM READY",
                            fontSize = 10.sp,
                            color = BioGreen,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = BorderColor, thickness = 1.dp)

        // Horizontal Navigation Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkSurface,
            contentColor = BioTeal,
            edgePadding = 8.dp,
            divider = { HorizontalDivider(color = BorderColor, thickness = 0.5.dp) },
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { viewModel.selectTab(index) },
                    modifier = Modifier.testTag("tab_$index"),
                    text = {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == index) BioTeal else TextSecondary
                        )
                    }
                )
            }
        }

        // Active Tab Screen Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                0 -> GraphDatasetScreen(viewModel)
                1 -> Virion3DScreen(viewModel)
                2 -> ArchitectureBlueprintsScreen()
                3 -> GeminiChatScreen(viewModel)
            }
        }
    }
}

// ==========================================
// TAB 1: KNOWLEDGE GRAPH DATASET EXPLORER
// ==========================================

@Composable
fun GraphDatasetScreen(viewModel: SarcovViewModel) {
    val cypherQuery by viewModel.currentCypherFilter.collectAsStateWithLifecycle()
    val selectedStructureId by viewModel.selectedStructureId.collectAsStateWithLifecycle()

    val nodesList = remember {
        listOf(
            GraphNode("RNA", "Genomic RNA Sequence", "GENOME", "Single-stranded viral RNA positive chain molecule.", "MN908947", "Sarcov viral genomic blueprint database reference.", 0f, 0f),
            GraphNode("SPIKE", "Spike Protein (S)", "PROTEIN", "Protruding trimeric entry molecule essential for membrane fusion.", "6VSB", "Binds host cell ACE2 surface protein with high affinity.", 0f, 0f),
            GraphNode("ENV", "Envelope Protein (E)", "PROTEIN", "Small vital membrane assembly channel component.", "5X29", "Facilitates viral budding, entry, and assembly phases.", 0f, 0f),
            GraphNode("MEMB", "Membrane Protein (M)", "PROTEIN", "Most dominant viral shape component in capsid layer.", "7MGS", "Constructs virion envelope layer structure.", 0f, 0f),
            GraphNode("ACE2", "Host ACE2 Receptor", "HOST", "Angiotensin-converting enzyme 2 human biological receptor.", "1R4L", "Active cellular entryway target for Spike binding.", 0f, 0f),
            GraphNode("TMPR", "TMPRSS2 Protease", "HOST", "Host serine enzyme assisting viral cellular entry.", "7Y10", "Cleaves Spike peptide bonds to activate membrane fusion.", 0f, 0f),
            GraphNode("REMD", "Remdesivir (RdRp Inhibitor)", "DRUG", "Adenosine analog viral RNA replication blocker.", "7BTF", "Inhibits viral replication cycle inside infected cells.", 0f, 0f),
            GraphNode("PAX", "Paxlovid (Mpro Blocker)", "DRUG", "Antiviral combination protease processing inhibitor.", "7RFS", "Halts polyprotein cleavage stages during self-replication.", 0f, 0f),
            GraphNode("OMIC", "Omicron variant (B.1.1.529)", "VARIANT", "Highly contagious spike-mutated strain of concern.", "7T9K", "Bypasses primary humoral immunity and vaccine bindings.", 0f, 0f),
            GraphNode("DELT", "Delta variant (B.1.617.2)", "VARIANT", "Highly transmissive strain of global concern.", "7V8A", "Triggers superior viral replication efficiency.", 0f, 0f)
        )
    }

    val edges = remember {
        listOf(
            GraphEdge("RNA", "SPIKE", "CODES_FOR"),
            GraphEdge("RNA", "ENV", "CODES_FOR"),
            GraphEdge("RNA", "MEMB", "CODES_FOR"),
            GraphEdge("SPIKE", "ACE2", "BINDS_TO"),
            GraphEdge("SPIKE", "TMPR", "PRIMED_BY"),
            GraphEdge("REMD", "RNA", "INHIBITS"),
            GraphEdge("PAX", "SPIKE", "INHIBITS"),
            GraphEdge("OMIC", "SPIKE", "MUTATEST_TO"),
            GraphEdge("DELT", "SPIKE", "MUTATEST_TO")
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }

    // Predefined Cypher templates
    val queryTemplates = listOf(
        "MATCH (n) RETURN n" to "Browse All Nodes",
        "MATCH (p:Protein)-[:BINDS_TO]->(h:Host)" to "Spike pathways",
        "MATCH (d:Drug)-[:INHIBITS]->(t)" to "Drug targets",
        "MATCH (v:Variant)-[:MUTATEST_TO]->(s:Protein)" to "Mutation spikes"
    )

    // Filter nodes based on Cypher filter query
    val cypherActiveNodeIds = remember(cypherQuery) {
        val q = cypherQuery.lowercase()
        when {
            q.contains("binds_to") || q.contains("p, h") -> setOf("SPIKE", "ACE2", "TMPR")
            q.contains("inhibits") || q.contains("d, t") -> setOf("REMD", "PAX", "RNA", "SPIKE")
            q.contains("mutatest_to") || q.contains("v, s") -> setOf("OMIC", "DELT", "SPIKE")
            else -> nodesList.map { it.id }.toSet()
        }
    }

    // Filter nodes by active category selection and search bar text
    val filteredNodes = remember(searchQuery, selectedCategory, cypherActiveNodeIds) {
        nodesList.filter { node ->
            val matchCypher = cypherActiveNodeIds.contains(node.id)
            val matchCategory = selectedCategory == "ALL" || node.type == selectedCategory
            val matchSearch = node.label.contains(searchQuery, ignoreCase = true) ||
                    node.id.contains(searchQuery, ignoreCase = true) ||
                    node.description.contains(searchQuery, ignoreCase = true)
            matchCypher && matchCategory && matchSearch
        }
    }

    // Determine current selected node details
    val activeNodeSelected = nodesList.find { it.id.equals(selectedStructureId, ignoreCase = true) } 
        ?: filteredNodes.firstOrNull() 
        ?: nodesList.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(12.dp)
    ) {
        // App intro Card featuring the custom SARS-CoV-19 logo
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // High-contrast clean card showcasing the dual-hemisphere biological & technical virus logo
                TransparentLogoImage(
                    size = 64.dp,
                    contentDescription = "SARS-CoV-19 Logo"
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "VIRAL & MOLECULAR STRUCTURE DATASETS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Authoritative relational database coordinates mapped from macromolecular PDB entry data. Examine node attributes, properties, and biological configurations.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // 1. Terminal Console & Cypher Controller
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "NEO4J CYPHER INTERFACE",
                    fontSize = 10.sp,
                    color = TerminalGreen,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Cypher quick query buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    queryTemplates.forEach { (queryCmd, label) ->
                        val isSelectedQuery = queryCmd == cypherQuery
                        Button(
                            onClick = { viewModel.updateCypherFilter(queryCmd) },
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelectedQuery) BioTeal.copy(alpha = 0.25f) else DarkCard,
                                contentColor = if (isSelectedQuery) BioTeal else TextSecondary
                            ),
                            border = BorderStroke(1.dp, if (isSelectedQuery) BioTeal else BorderColor),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                maxLines = 1,
                                fontWeight = FontWeight.Bold,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Terminal box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .border(BorderStroke(1.dp, BorderColor.copy(alpha = 0.6f)), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "neo4j@sarcov-graph-1:~$",
                                fontSize = 10.sp,
                                color = TerminalGreen,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = cypherQuery,
                                fontSize = 10.sp,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Query returned ${filteredNodes.size} dataset records matching criteria.",
                            fontSize = 9.sp,
                            color = BioTeal,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // 2. Search Box & Category Filters Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Text Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search nodes, descriptions...", fontSize = 11.sp, color = TextSecondary) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 11.sp, color = Color.White),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("node_dataset_search_bar"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BioTeal,
                    unfocusedBorderColor = BorderColor,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                ),
                shape = RoundedCornerShape(8.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )

            // Category tag chips scrollbar
            val categories = listOf("ALL", "GENOME", "PROTEIN", "HOST", "DRUG", "VARIANT")
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.weight(1.2f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(categories.size) { index ->
                    val cat = categories[index]
                    val isCatSelected = selectedCategory == cat
                    Card(
                        onClick = { selectedCategory = cat },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCatSelected) BioTeal.copy(alpha = 0.2f) else DarkCard
                        ),
                        border = BorderStroke(1.dp, if (isCatSelected) BioTeal else BorderColor),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("filter_chip_$cat")
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(
                                text = cat,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCatSelected) Color.White else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // 3. Grid / Split Master-Detail Layout
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // LEFT COLUMN: Nodes list (scrollable)
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filteredNodes.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "No dataset nodes found",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Try clearing the search or choosing 'ALL' filter.",
                                fontSize = 10.sp,
                                color = TextSecondary.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    items(filteredNodes.size) { idx ->
                        val node = filteredNodes[idx]
                        val isNodeSelected = activeNodeSelected.id == node.id
                        Card(
                            onClick = { viewModel.selectStructure(node.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("nodecard_${node.id}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isNodeSelected) DarkSurface else DarkCard
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isNodeSelected) BioTeal else BorderColor.copy(alpha = 0.6f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = node.id,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BioTeal,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        // Colored type pill
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = when (node.type) {
                                                        "GENOME" -> BioCoral.copy(alpha = 0.2f)
                                                        "PROTEIN" -> BioTeal.copy(alpha = 0.2f)
                                                        "HOST" -> Color(0xFF1E88E5).copy(alpha = 0.2f)
                                                        "DRUG" -> BioGreen.copy(alpha = 0.2f)
                                                        else -> Color(0xFFAB47BC).copy(alpha = 0.2f)
                                                    },
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = node.type,
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (node.type) {
                                                    "GENOME" -> BioCoral
                                                    "PROTEIN" -> BioTeal
                                                    "HOST" -> Color(0xFF90CAF9)
                                                    "DRUG" -> BioGreen
                                                    else -> Color(0xFFE1BEE7)
                                                }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = node.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = node.description,
                                        fontSize = 10.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Select node",
                                    tint = if (isNodeSelected) BioTeal else TextSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // RIGHT COLUMN: Selected Node detail & relational connections panel
            Card(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    Text(
                        text = "METADATA RECOGNITION PANEL",
                        fontSize = 9.sp,
                        color = BioCoral,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Node Title & Type details
                    Text(
                        text = activeNodeSelected.label.uppercase(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "GRAPH ID: " + activeNodeSelected.id,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = BioTeal
                        )
                        if (activeNodeSelected.pdbId.isNotEmpty() && activeNodeSelected.pdbId != "N/A") {
                            Text(
                                text = "PDB ID: " + activeNodeSelected.pdbId,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 6.dp))

                    // Node Biological Description
                    Text(
                        text = "BIOLOGY PROPERTIES",
                        fontSize = 8.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Text(
                        text = activeNodeSelected.description,
                        fontSize = 10.sp,
                        color = Color.White,
                        lineHeight = 13.sp
                    )

                    if (activeNodeSelected.details.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "DATABASE KNOWLEDGE DETAILS",
                            fontSize = 8.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Text(
                            text = activeNodeSelected.details,
                            fontSize = 10.sp,
                            color = BioTeal,
                            lineHeight = 13.sp
                        )
                    }

                    HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 6.dp))

                    // Adjacent edges graph dataset view
                    Text(
                        text = "NEO4J SCHEMA RELATIONSHIPS",
                        fontSize = 8.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    val relatedEdges = edges.filter { it.sourceId == activeNodeSelected.id || it.targetId == activeNodeSelected.id }

                    if (relatedEdges.isEmpty()) {
                        Text(
                            text = "No relationships configured for this index in the current Neo4j pipeline.",
                            fontSize = 10.sp,
                            color = TextSecondary.copy(alpha = 0.8f),
                            fontStyle = FontStyle.Italic
                        )
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(relatedEdges.size) { eIdx ->
                                val edge = relatedEdges[eIdx]
                                val isOutgoing = edge.sourceId == activeNodeSelected.id
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .border(BorderStroke(0.5.dp, BorderColor.copy(alpha = 0.4f)), RoundedCornerShape(4.dp))
                                        .padding(6.dp)
                                ) {
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isOutgoing) Icons.Default.ArrowForward else Icons.Default.PlayArrow,
                                                contentDescription = if (isOutgoing) "Outgoing link" else "Incoming link",
                                                tint = if (isOutgoing) BioTeal else BioCoral,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = if (isOutgoing) "OUTGOING: -${edge.relation}->" else "INCOMING: <-${edge.relation}-",
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isOutgoing) BioTeal else BioCoral
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isOutgoing) {
                                                "Target: " + (nodesList.find { it.id == edge.targetId }?.label ?: edge.targetId)
                                            } else {
                                                "Source: " + (nodesList.find { it.id == edge.sourceId }?.label ?: edge.sourceId)
                                            },
                                            fontSize = 9.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 1: PHYSICS NETWORK GRAPH EXPLORER (DEPRECATED/KEPT TO REDUCE EDIT SCALE)
// ==========================================

@Composable
fun OldNetworkGraphScreen(viewModel: SarcovViewModel) {
    // Collect the dynamic cypher filter state
    val cypherQuery by viewModel.currentCypherFilter.collectAsStateWithLifecycle()

    // Precalculate touch coordinates using Density to avoid suspending thread resolving issues
    val density = LocalDensity.current
    val touchRadiusPx = remember(density) { with(density) { 44.dp.toPx() } }
    val selectRadiusPx = remember(density) { with(density) { 40.dp.toPx() } }

    // Base Nodes & Edges Lists
    var nodesList by remember {
        mutableStateOf(
            listOf(
                GraphNode("RNA", "Genomic RNA Sequence", "GENOME", "Single-stranded viral RNA positive chain molecule.", "MN908947", "Sarcov viral genomic blueprint database reference.", 180f, 220f),
                GraphNode("SPIKE", "Spike Protein (S)", "PROTEIN", "Protruding trimeric entry molecule.", "6VSB", "Binds host cell ACE2 surface protein with high affinity.", 320f, 240f),
                GraphNode("ENV", "Envelope Protein (E)", "PROTEIN", "Small vital membrane assembly component.", "5X29", "Facilitates viral budding, entry, and assembly phases.", 150f, 400f),
                GraphNode("MEMB", "Membrane Protein (M)", "PROTEIN", "Most dominant viral shape component.", "7MGS", "Constructs virion envelope layer structure.", 240f, 150f),
                GraphNode("ACE2", "Host ACE2 Receptor", "HOST", "Angiotensin-converting enzyme 2 human receptor.", "1R4L", "Active cellular entryway target.", 480f, 320f),
                GraphNode("TMPR", "TMPRSS2 Protease", "HOST", "Host serine enzyme assisting viral entry.", "7Y10", "Cleaves Spike peptide bonds to activate membrane fusion.", 540f, 180f),
                GraphNode("REMD", "Remdesivir (RdRp Inhibitor)", "DRUG", "Adenosine analog RNA replication blocker.", "7BTF", "Inhibits viral replication cycle inside nodes.", 160f, 540f),
                GraphNode("PAX", "Paxlovid (Mpro Blocker)", "DRUG", "Antiviral combination processing inhibitor.", "7RFS", "Halts polyprotein cleavage stages.", 300f, 560f),
                GraphNode("OMIC", "Omicron (B.1.1.529)", "VARIANT", "Highly contagious spike mutated strain.", "7T9K", "Bypasses primary host immunity bindings.", 420f, 100f),
                GraphNode("DELT", "Delta (B.1.617.2)", "VARIANT", "Highly transmissive strain.", "7V8A", "Triggers superior viral replication efficiency.", 460f, 480f)
            )
        )
    }

    val edges = remember {
        listOf(
            GraphEdge("RNA", "SPIKE", "CODES_FOR"),
            GraphEdge("RNA", "ENV", "CODES_FOR"),
            GraphEdge("RNA", "MEMB", "CODES_FOR"),
            GraphEdge("SPIKE", "ACE2", "BINDS_TO"),
            GraphEdge("SPIKE", "TMPR", "PRIMED_BY"),
            GraphEdge("REMD", "RNA", "INHIBITS"),
            GraphEdge("PAX", "SPIKE", "INHIBITS"),
            GraphEdge("OMIC", "SPIKE", "MUTATEST_TO"),
            GraphEdge("DELT", "SPIKE", "MUTATEST_TO")
        )
    }

    var selectedNode by remember { mutableStateOf<GraphNode?>(nodesList[1]) }
    var draggedNodeId by remember { mutableStateOf<String?>(null) }

    // Cypher Console Predefines
    val queryTemplates = listOf(
        "MATCH (n) RETURN n" to "Browse All Nodes",
        "MATCH (p:Protein)-[:BINDS_TO]->(h:Host) RETURN p, h" to "Spike ACE2 Pathways",
        "MATCH (d:Drug)-[:INHIBITS]->(t) RETURN d, t" to "Drug Therapy Targets",
        "MATCH (v:Variant)-[:MUTATEST_TO]->(s:Protein) RETURN v, s" to "Mutation Spikes"
    )

    // Evaluate which nodes are currently Highlighted/Visible based on cypher query simulation
    val activeNodeIds = remember(cypherQuery) {
        val lowercaseQuery = cypherQuery.lowercase()
        when {
            lowercaseQuery.contains("binds_to") || lowercaseQuery.contains("p, h") -> setOf("SPIKE", "ACE2", "TMPR")
            lowercaseQuery.contains("inhibits") || lowercaseQuery.contains("d, t") -> setOf("REMD", "PAX", "RNA", "SPIKE")
            lowercaseQuery.contains("mutatest_to") || lowercaseQuery.contains("v, s") -> setOf("OMIC", "DELT", "SPIKE")
            else -> nodesList.map { it.id }.toSet()
        }
    }

    // Gentle Force-Directed Physics Updates
    LaunchedEffect(Unit) {
        val dampening = 0.88f
        val repulsionStrength = 2200f
        val attractionStrength = 0.05f
        val restLength = 160f

        while (isActive) {
            // Repulsion forces
            for (i in nodesList.indices) {
                val n1 = nodesList[i]
                if (n1.id == draggedNodeId) continue
                var fx = 0f
                var fy = 0f
                for (j in nodesList.indices) {
                    if (i == j) continue
                    val n2 = nodesList[j]
                    val dx = n1.x - n2.x
                    val dy = n1.y - n2.y
                    val distSq = dx * dx + dy * dy + 0.1f
                    val dist = sqrt(distSq)
                    if (dist < 320f) {
                        fx += (dx / dist) * (repulsionStrength / dist)
                        fy += (dy / dist) * (repulsionStrength / dist)
                    }
                }
                n1.vx = (n1.vx + fx) * dampening
                n1.vy = (n1.vy + fy) * dampening
            }

            // Attraction forces (along edges)
            for (edge in edges) {
                val sIdx = nodesList.indexOfFirst { it.id == edge.sourceId }
                val tIdx = nodesList.indexOfFirst { it.id == edge.targetId }
                if (sIdx != -1 && tIdx != -1) {
                    val sNode = nodesList[sIdx]
                    val tNode = nodesList[tIdx]
                    val dx = tNode.x - sNode.x
                    val dy = tNode.y - sNode.y
                    val dist = sqrt(dx * dx + dy * dy + 0.1f)
                    val force = (dist - restLength) * attractionStrength
                    val fX = (dx / dist) * force
                    val fY = (dy / dist) * force

                    if (sNode.id != draggedNodeId) {
                        sNode.vx += fX
                        sNode.vy += fY
                    }
                    if (tNode.id != draggedNodeId) {
                        tNode.vx -= fX
                        tNode.vy -= fY
                    }
                }
            }

            // Apply positions updates with soft container boundary walls
            nodesList = nodesList.map { node ->
                if (node.id == draggedNodeId) {
                    node
                } else {
                    var newX = node.x + node.vx
                    var newY = node.y + node.vy

                    // Bound checks
                    if (newX < 50f) { newX = 50f; node.vx *= -0.5f }
                    if (newX > 640f) { newX = 640f; node.vx *= -0.5f }
                    if (newY < 50f) { newY = 50f; node.vy *= -0.5f }
                    if (newY > 480f) { newY = 480f; node.vy *= -0.5f }

                    node.copy(x = newX, y = newY, vx = node.vx, vy = node.vy)
                }
            }
            delay(16) // ~60fps layout loop
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Cypher Simulator Quick bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, BorderColor),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "NEO4J CYPHER DIRECTORY TUNNEL",
                    fontSize = 11.sp,
                    color = TerminalGreen,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    queryTemplates.forEach { (tmpl, label) ->
                        val isCurrent = tmpl == cypherQuery
                        Button(
                            onClick = { viewModel.updateCypherFilter(tmpl) },
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCurrent) BioTeal.copy(alpha = 0.2f) else DarkCard,
                                contentColor = if (isCurrent) BioTeal else TextSecondary
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isCurrent) BioTeal else BorderColor
                            ),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // Interactive command line showing active Cypher
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .border(BorderStroke(1.dp, BorderColor.copy(alpha = 0.5f)), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "neo4j@sarcov-graph-1:~$",
                            fontSize = 10.sp,
                            color = TerminalGreen,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = cypherQuery,
                            fontSize = 10.sp,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Live Physics Graph Canvas Panel
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .pointerInput(nodesList) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val touched = nodesList.minByOrNull { node ->
                                val dx = node.x - offset.x
                                val dy = node.y - offset.y
                                dx * dx + dy * dy
                            }
                            if (touched != null) {
                                val dist = sqrt((touched.x - offset.x).pow(2) + (touched.y - offset.y).pow(2))
                                if (dist < touchRadiusPx) {
                                    draggedNodeId = touched.id
                                    selectedNode = touched
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            draggedNodeId?.let { id ->
                                nodesList = nodesList.map { n ->
                                    if (n.id == id) {
                                        n.copy(x = n.x + dragAmount.x, y = n.y + dragAmount.y)
                                    } else n
                                }
                            }
                        },
                        onDragEnd = {
                            draggedNodeId = null
                        }
                    )
                }
                .pointerInput(nodesList) {
                    detectTapGestures { tapLoc ->
                        val clicked = nodesList.minByOrNull { n ->
                            sqrt((n.x - tapLoc.x).pow(2) + (n.y - tapLoc.y).pow(2))
                        }
                        if (clicked != null) {
                            val dist = sqrt((clicked.x - tapLoc.x).pow(2) + (clicked.y - tapLoc.y).pow(2))
                            if (dist < selectRadiusPx) {
                                selectedNode = clicked
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Draw Network Grid Background Pattern (Deep Biotech Matrix feel)
                val gridSpacing = 40.dp.toPx()
                for (x in 0..(canvasWidth / gridSpacing).toInt()) {
                    drawLine(
                        color = BorderColor.copy(alpha = 0.1f),
                        start = Offset(x * gridSpacing, 0f),
                        end = Offset(x * gridSpacing, canvasHeight),
                        strokeWidth = 1f
                    )
                }
                for (y in 0..(canvasHeight / gridSpacing).toInt()) {
                    drawLine(
                        color = BorderColor.copy(alpha = 0.1f),
                        start = Offset(0f, y * gridSpacing),
                        end = Offset(canvasWidth, y * gridSpacing),
                        strokeWidth = 1f
                    )
                }

                // 1. Draw Network Connections (Edges)
                for (edge in edges) {
                    val sNode = nodesList.find { it.id == edge.sourceId }
                    val tNode = nodesList.find { it.id == edge.targetId }
                    if (sNode != null && tNode != null) {
                        // Is this relationship currently active in query filter?
                        val isEdgeActive = activeNodeIds.contains(sNode.id) && activeNodeIds.contains(tNode.id)
                        val edgeColor = when {
                            !isEdgeActive -> BorderColor.copy(alpha = 0.15f)
                            edge.relation == "BINDS_TO" -> BioTeal.copy(alpha = 0.65f)
                            edge.relation == "INHIBITS" -> BioCoral.copy(alpha = 0.65f)
                            edge.relation == "MUTATEST_TO" -> BioCoral.copy(alpha = 0.65f)
                            else -> BioGreen.copy(alpha = 0.55f)
                        }
                        
                        val strokeWidth = if (isEdgeActive) 4f else 1.5f

                        drawLine(
                            color = edgeColor,
                            start = Offset(sNode.x, sNode.y),
                            end = Offset(tNode.x, tNode.y),
                            strokeWidth = strokeWidth,
                            pathEffect = if (!isEdgeActive) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
                        )
                    }
                }

                // 2. Draw Nodes
                for (node in nodesList) {
                    val isActive = activeNodeIds.contains(node.id)
                    val isSelected = selectedNode?.id == node.id
                    val nodeColor = when (node.type) {
                        "GENOME" -> BioGreen
                        "PROTEIN" -> BioCoral
                        "HOST" -> BioTeal
                        "DRUG" -> BioTeal
                        "VARIANT" -> BioCoral
                        else -> BioBlue
                    }

                    val baseRadius = 24.dp.toPx()
                    val drawRadius = if (isSelected) baseRadius + 4f else baseRadius
                    val opacity = if (isActive) 1f else 0.25f

                    // Glowing backdrop rings for selected systems
                    if (isSelected && isActive) {
                        drawCircle(
                            color = nodeColor.copy(alpha = 0.25f),
                            radius = drawRadius + 14f,
                            center = Offset(node.x, node.y)
                        )
                        drawCircle(
                            color = nodeColor.copy(alpha = 0.4f),
                            radius = drawRadius + 6f,
                            center = Offset(node.x, node.y),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    // Main Solid Protein Node Sphere
                    drawCircle(
                        color = DarkSurface,
                        radius = drawRadius,
                        center = Offset(node.x, node.y),
                        alpha = opacity
                    )

                    drawCircle(
                        color = nodeColor,
                        radius = drawRadius,
                        center = Offset(node.x, node.y),
                        style = Stroke(width = 3.dp.toPx()),
                        alpha = opacity
                    )

                    // Draw inner accent center core dot
                    drawCircle(
                        color = nodeColor.copy(alpha = 0.7f),
                        radius = 6.dp.toPx(),
                        center = Offset(node.x, node.y),
                        alpha = opacity
                    )
                }
            }

            // Draw floating labels on top of Nodes to prevent canvas text scaling quality loss
            nodesList.forEach { node ->
                val isActive = activeNodeIds.contains(node.id)
                val isSelected = selectedNode?.id == node.id
                val textColor = if (isActive) Color.White else TextMuted
                val nodeColor = when (node.type) {
                    "GENOME" -> BioGreen
                    "PROTEIN" -> BioCoral
                    "HOST" -> BioTeal
                    "DRUG" -> BioTeal
                    "VARIANT" -> BioCoral
                    else -> BioBlue
                }

                Box(
                    modifier = Modifier
                        .offset(x = (node.x - 55f).dp, y = (node.y + 26f).dp)
                        .width(110.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = node.id,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = nodeColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(
                                    color = DarkBg.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(3.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = node.label,
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textColor,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Expanded Biological Node Inspector panel
        AnimatedVisibility(
            visible = selectedNode != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            selectedNode?.let { node ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface)
                        .padding(14.dp)
                ) {
                    HorizontalDivider(color = BorderColor, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        when (node.type) {
                                            "GENOME" -> BioGreen
                                            "PROTEIN" -> BioCoral
                                            "HOST" -> BioTeal
                                            "DRUG" -> BioTeal
                                            "VARIANT" -> BioCoral
                                            else -> BioBlue
                                        },
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = node.label,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        // Close Details panel button
                        IconButton(
                            onClick = { selectedNode = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Node details",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "ELEMENT DESCRIPTION",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                                Text(
                                    text = node.description + " " + node.details,
                                    fontSize = 11.sp,
                                    color = TextPrimary
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.width(100.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // PDB reference card tag
                            if (node.pdbId.isNotEmpty()) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = DarkBg),
                                    border = BorderStroke(1.dp, BorderColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "PDB REF",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BioTeal,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = node.pdbId,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color.White,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkBg),
                                border = BorderStroke(1.dp, BorderColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "NODE CLASS",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BioGreen,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = node.type,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 2: INTERACTIVE 3D VIRION STRUCTURAL MODEL
// ==========================================

data class ProjectedAtom(
    val atom: Atom3D,
    val px: Float,
    val py: Float,
    val depthZ: Float,
    val renderSize: Float
)

@Composable
fun Virion3DScreen(viewModel: SarcovViewModel = viewModel()) {
    val selectedStructureId by viewModel.selectedStructureId.collectAsStateWithLifecycle()
    val structure = remember(selectedStructureId) { StructureLibrary.getStructure(selectedStructureId) }

    var activeCategory by remember { mutableStateOf("VIRUS") }
    
    // Automatically synchronize category UI segment based on externally updated selection ID
    LaunchedEffect(selectedStructureId) {
        val struct = StructureLibrary.getStructure(selectedStructureId)
        if (struct.category != activeCategory) {
            activeCategory = struct.category
        }
    }

    // Reset default active element details smoothly on structural entity switch
    var activeAtomDetail by remember(selectedStructureId) {
        mutableStateOf(structure.atoms.firstOrNull())
    }

    // 3D rotation angle coordinates
    var rotX by remember { mutableStateOf(-0.3f) }
    var rotY by remember { mutableStateOf(0.4f) }

    val density = LocalDensity.current
    val hitDistPx = remember(density) { with(density) { 30.dp.toPx() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Explanatory label
        Text(
            text = "3D SCIENTIFIC VISUALIZATION",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
        Text(
            text = "Animate, swipe, or rotate complex biomolecular capsid shells or advanced semiconductor crystals. Hover or tap atoms to examine precise valence coordinate bindings.",
            fontSize = 11.sp,
            color = TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            textAlign = TextAlign.Start
        )

        // 1. Double interactive visual category selection card bars
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                onClick = { activeCategory = "VIRUS" },
                colors = CardDefaults.cardColors(
                    containerColor = if (activeCategory == "VIRUS") BioTeal.copy(alpha = 0.15f) else DarkSurface
                ),
                border = BorderStroke(1.dp, if (activeCategory == "VIRUS") BioTeal else BorderColor),
                modifier = Modifier.weight(1f)
            ) {
                Box(modifier = Modifier.padding(8.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "🦠 Bio-Viruses",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeCategory == "VIRUS") BioTeal else TextSecondary
                    )
                }
            }

            Card(
                onClick = { activeCategory = "MATERIAL" },
                colors = CardDefaults.cardColors(
                    containerColor = if (activeCategory == "MATERIAL") BioTeal.copy(alpha = 0.15f) else DarkSurface
                ),
                border = BorderStroke(1.dp, if (activeCategory == "MATERIAL") BioTeal else BorderColor),
                modifier = Modifier.weight(1f)
            ) {
                Box(modifier = Modifier.padding(8.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "💎 Nano-Materials",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeCategory == "MATERIAL") BioTeal else TextSecondary
                    )
                }
            }
        }

        // 2. Row of Chip switches
        val categoryStructures = remember(activeCategory) {
            if (activeCategory == "VIRUS") {
                listOf(
                    "sars_cov_2" to "SARS-CoV-2",
                    "influenza" to "Influenza A",
                    "adenovirus" to "Adenovirus",
                    "bacteriophage" to "Phage T4"
                )
            } else {
                listOf(
                    "graphene" to "Graphene Sheet",
                    "nanotube" to "Carbon Nanotube",
                    "silicon" to "Silicon Lattice",
                    "nacl" to "NaCl Salt"
                )
            }
        }

        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categoryStructures.size) { index ->
                val (id, label) = categoryStructures[index]
                val isSelected = selectedStructureId == id
                Card(
                    onClick = { viewModel.selectStructure(id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) BioTeal.copy(alpha = 0.2f) else DarkCard
                    ),
                    border = BorderStroke(1.dp, if (isSelected) BioTeal else BorderColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else TextSecondary
                        )
                    }
                }
            }
        }

        // 3. Descriptive Scientific Card of active structure model
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = structure.name.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = structure.subtitle,
                            fontSize = 9.sp,
                            color = BioTeal,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (structure.pdbReference != "N/A" && structure.pdbReference.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkBg),
                            border = BorderStroke(0.5.dp, BorderColor)
                        ) {
                            Text(
                                text = "PDB REFS: " + structure.pdbReference,
                                fontSize = 8.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = structure.description,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 14.sp
                )
            }
        }

        // 4. Interactive 3D Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        rotY += dragAmount.x * 0.005f
                        rotX -= dragAmount.y * 0.005f
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f

                // Concentric design lines
                drawCircle(
                    color = BorderColor.copy(alpha = 0.04f),
                    radius = 160.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 1f)
                )
                drawCircle(
                    color = BorderColor.copy(alpha = 0.02f),
                    radius = 240.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 1f)
                )

                // Render capsid/envelope boundary shadow background underlay
                if (structure.baseEnvelopeRadiusDp > 0f) {
                    val envRad = structure.baseEnvelopeRadiusDp.dp.toPx()
                    val gradientCenter = Offset(cx - envRad * 0.2f, cy - envRad * 0.2f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                DarkSurface,
                                DarkCard,
                                structure.envelopeColor.copy(alpha = 0.12f)
                            ),
                            center = gradientCenter,
                            radius = envRad
                        ),
                        radius = envRad,
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = structure.envelopeColor,
                        radius = envRad,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Project 3D vector points using linear perspective scaling
                val projectedAtoms = structure.atoms.map { atom ->
                    val x1 = atom.x * cos(rotY) - atom.z * sin(rotY)
                    val z1 = atom.x * sin(rotY) + atom.z * cos(rotY)
                    val y1 = atom.y

                    val y2 = y1 * cos(rotX) - z1 * sin(rotX)
                    val z2 = y1 * sin(rotX) + z1 * cos(rotX)
                    val x2 = x1

                    val distanceFactor = 320f
                    val scale = distanceFactor / (distanceFactor + z2)

                    ProjectedAtom(
                        atom = atom,
                        px = cx + x2 * scale,
                        py = cy + y2 * scale,
                        depthZ = z2,
                        renderSize = atom.sizeDp.dp.toPx() * scale
                    )
                }.sortedBy { it.depthZ }

                val projectedMap = projectedAtoms.associateBy { it.atom.id }

                // Draw connectivity bounds
                structure.bonds.forEach { bond ->
                    val sourceProj = projectedMap[bond.sourceId]
                    val targetProj = projectedMap[bond.targetId]
                    if (sourceProj != null && targetProj != null) {
                        val bondColor = bond.color
                        val avgDepth = (sourceProj.depthZ + targetProj.depthZ) / 2f
                        val alpha = if (avgDepth < 0) 0.3f else 0.8f
                        val thickness = bond.thicknessDp.dp.toPx() * (320f / (320f + avgDepth))
                        drawLine(
                            color = bondColor.copy(alpha = alpha),
                            start = Offset(sourceProj.px, sourceProj.py),
                            end = Offset(targetProj.px, targetProj.py),
                            strokeWidth = thickness
                        )
                    }
                }

                // Draw molecular atoms
                projectedAtoms.forEach { proj ->
                    val atomColor = proj.atom.color
                    val alpha = if (proj.depthZ < 0) 0.4f else 1.0f
                    val isSelected = activeAtomDetail?.id == proj.atom.id

                    drawCircle(
                        color = DarkSurface,
                        radius = proj.renderSize,
                        center = Offset(proj.px, proj.py),
                        alpha = alpha
                    )

                    drawCircle(
                        color = atomColor,
                        radius = proj.renderSize,
                        center = Offset(proj.px, proj.py),
                        style = Stroke(width = if (isSelected) 3.5.dp.toPx() else 1.5.dp.toPx()),
                        alpha = alpha
                    )

                    drawCircle(
                        color = atomColor.copy(alpha = if (isSelected) 0.45f else 0.2f),
                        radius = proj.renderSize - 1.5.dp.toPx(),
                        center = Offset(proj.px, proj.py),
                        alpha = alpha
                    )

                    // Draw atomic letter symbols perfectly aligned inside canvas
                    if (proj.depthZ >= 0 && proj.atom.elementSymbol.isNotEmpty() && proj.renderSize > 8.dp.toPx()) {
                        val textPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = (proj.renderSize * 0.9f).coerceIn(8.dp.toPx(), 13.dp.toPx())
                            textAlign = android.graphics.Paint.Align.CENTER
                            typeface = android.graphics.Typeface.MONOSPACE
                            isFakeBoldText = true
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            proj.atom.elementSymbol,
                            proj.px,
                            proj.py + (textPaint.textSize / 3f),
                            textPaint
                        )
                    }
                }
            }

            // Clicking layer detector
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(selectedStructureId) {
                        detectTapGestures { tapLoc ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f

                            val projectedAtomsList = structure.atoms.map { atom ->
                                val x1 = atom.x * cos(rotY) - atom.z * sin(rotY)
                                val z1 = atom.x * sin(rotY) + atom.z * cos(rotY)
                                val y1 = atom.y

                                val y2 = y1 * cos(rotX) - z1 * sin(rotX)
                                val z2 = y1 * sin(rotX) + z1 * cos(rotX)
                                val x2 = x1

                                val distanceFactor = 320f
                                val scale = distanceFactor / (distanceFactor + z2)

                                ProjectedAtom(
                                    atom = atom,
                                    px = cx + x2 * scale,
                                    py = cy + y2 * scale,
                                    depthZ = z2,
                                    renderSize = atom.sizeDp.dp.toPx() * scale
                                )
                            }

                            var nearest: ProjectedAtom? = null
                            var minDist = Float.MAX_VALUE
                            for (proj in projectedAtomsList) {
                                val dist = sqrt((proj.px - tapLoc.x).pow(2) + (proj.py - tapLoc.y).pow(2))
                                if (dist < minDist) {
                                    minDist = dist
                                    nearest = proj
                                }
                            }

                            if (nearest != null && minDist < hitDistPx) {
                                activeAtomDetail = nearest.atom
                            }
                        }
                    }
            ) {}

            Text(
                text = "⇄ Swipe to Rotate | Tap node to inspect",
                fontSize = 9.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )
        }

        // 5. Active highlight atom info display bottom panel
        AnimatedVisibility(
            visible = activeAtomDetail != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            activeAtomDetail?.let { node ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(node.color, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = node.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            if (node.elementSymbol.isNotEmpty()) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = DarkBg),
                                    border = BorderStroke(0.5.dp, BorderColor)
                                ) {
                                    Text(
                                        text = "SYM: " + node.elementSymbol,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = BioTeal,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = node.desc,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )

                        Divider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = BorderColor.copy(alpha = 0.4f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "COORD VECTORS (X, Y, Z)",
                                    fontSize = 8.sp,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "X: ${"%.1f".format(node.x)} | Y: ${"%.1f".format(node.y)} | Z: ${"%.1f".format(node.z)}",
                                    fontSize = 9.sp,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "SPATIAL EXPOSURE",
                                    fontSize = 8.sp,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                                val distFromOrigin = sqrt(node.x * node.x + node.y * node.y + node.z * node.z)
                                val exposureLabel = when {
                                    distFromOrigin > 100f -> "OUTER SURFACE"
                                    distFromOrigin > 60f -> "MID ENVELOPE"
                                    else -> "INTERNAL CORE"
                                }
                                val exposureColor = when {
                                    distFromOrigin > 100f -> BioCoral
                                    distFromOrigin > 60f -> BioAmber
                                    else -> BioGreen
                                }
                                Text(
                                    text = exposureLabel,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = exposureColor,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 3: SYSTEM ARCHITECTURE BLUEPRINTS
// = =========================================

@Composable
fun ArchitectureBlueprintsScreen() {
    val subsystems = remember {
        listOf(
            ArchSubsystem(
                id = "ingest",
                title = "1. INGESTION MATRIX",
                subtitle = "Biotic sequence FASTA & PDB ingestion",
                description = "Extracts molecular structures from Protein Data Bank (PDB) structural data. Computes spatial vectors for every carbon-alpha residue atom (C-alpha residues) and associates amino-acid codes to layout high-altitude knowledge trees.",
                codeSnippet = """
                    def parse_pdb_coordinates(pdb_file_path):
                        atoms_list = []
                        with open(pdb_file_path, 'r') as file:
                            for line in file:
                                if line.startswith("ATOM") and "CA" in line:
                                    residue_name = line[17:20].strip()
                                    chain_id = line[21].strip()
                                    res_seq_num = int(line[22:26].strip())
                                    x = float(line[30:38].strip())
                                    y = float(line[38:46].strip())
                                    z = float(line[46:54].strip())
                                    atoms_list.append({
                                        'residue': residue_name,
                                        'chain': chain_id,
                                        'sequence': res_seq_num,
                                        'coords': (x, y, z)
                                    })
                        return atoms_list
                """.trimIndent(),
                schemaInfo = """
                    FILES: PDB-6VSB.ent, SARS-MN908947.fasta
                    DATA NODES TYPE: Raw Molecule Sequence Files
                    ATOMS EXTREMUM: 10,000+ atomic coordinate vertices.
                """.trimIndent(),
                icon = Icons.Default.Share
            ),
            ArchSubsystem(
                id = "etl",
                title = "2. SPATIAL RESOLVING PIPELINE",
                subtitle = "Calculates Euclidean binding interfaces",
                description = "Extracts biological relationships by calculating the three-dimensional pairwise distance matrices across protein-to-protein molecular interfaces. Spikes matching distance barriers under 3.5\u00c5 are bound automatically as interactions.",
                codeSnippet = """
                    import numpy as np

                    def compute_euclidean_contact_map(residues_c1, residues_c2, cutoff=3.5):
                        relationships = []
                        for r1 in residues_c1:
                            for r2 in residues_c2:
                                coords1 = np.array(r1['coords'])
                                coords2 = np.array(r2['coords'])
                                dist = np.linalg.norm(coords1 - coords2)
                                if dist < cutoff:
                                    relationships.append({
                                        'source_res': r1['sequence'],
                                        'target_res': r2['sequence'],
                                        'distance': dist,
                                        'binding_interface': True
                                    })
                        return relationships
                """.trimIndent(),
                schemaInfo = """
                    ALGORITHM: 3D Euclidean contact mapping matrices.
                    INTERFACE CAP: 3.5 Ångströms (Max Peptide bounding)
                    SPATIAL ENGINE: Python SciPy + NumPy distance vectorizer
                """.trimIndent(),
                icon = Icons.Default.Build
            ),
            ArchSubsystem(
                id = "neo4j",
                title = "3. NEO4J GRAPH COMPILER",
                subtitle = "Stores high-dim semantic bindings",
                description = "Translates coordinates, variants, biochemical links, and therapies directly into nodes and edges schema structures inside Neo4j clusters. This maps atomic configurations to epidemiological variables.",
                codeSnippet = """
                    MATCH (p:Protein {id: 'Spike_S1'}), (h:HostCell {name: 'ACE2'})
                    MERGE (p)-[r:BINDS_TO {distance_angstrom: 2.8}]->(h)
                    ON CREATE SET r.confidence = 0.98, r.method = 'Cryo-EM'
                    RETURN p, r, h

                    // Connect specific mutations to variants
                    MATCH (v:Variant {name: 'Omicron'}), (s:Protein {id: 'Spike_S1'})
                    MERGE (v)-[:MUTATEST_TO {residue_count: 32}]->(s)
                """.trimIndent(),
                schemaInfo = """
                    DATABASE: Neo4j Aura Graph Cluster v5
                    INDEX CORES: UNIQUE CONSTRAINT ON (p:Protein) ASSERT p.id IS UNIQUE
                    SCHEMA ROOT: Structure (Atom, Residue) -> Macro (Protein, Variant)
                """.trimIndent(),
                icon = Icons.Default.Menu
            ),
            ArchSubsystem(
                id = "front",
                title = "4. GL COORDINATES RENDERER",
                subtitle = "Renders physical nodes canvas",
                description = "Renders and displays spatial structures within an interactive browser-compiled WebGL or Compose Canvas rendering scene. Projecting 3D vector coordinates onto a 2D interactive view.",
                codeSnippet = """
                    // Canvas projecting function
                    fun project3DCoordinate(cx: Float, cy: Float, x: Float, y: Float, z: Float, rotX: Float, rotY: Float): Offset {
                        // Math matrix transformations
                        val xRot = x * cos(rotY) - z * sin(rotY)
                        val zRot = x * sin(rotY) + z * cos(rotY)
                        val yRot = y * cos(rotX) - zRot * sin(rotX)
                        val zFinal = y * sin(rotX) + zRot * cos(rotX)
                        
                        val cameraDist = 320f
                        val perpScale = cameraDist / (cameraDist + zFinal)
                        return Offset(cx + xRot * perpScale, cy + yRot * perpScale)
                    }
                """.trimIndent(),
                schemaInfo = """
                    PIPELINE: Android Jetpack Compose Canvas / Three.js WebGL
                    DRAW ROUTE: Perspective projection matrix
                    Z-DEPTH SORTING: Painters Algorithm (Depth-Z index ordering)
                """.trimIndent(),
                icon = Icons.Default.Refresh
            ),
            ArchSubsystem(
                id = "repo",
                title = "5. OPEN-SOURCE REPOSITORY",
                subtitle = "GitHub core source integration",
                description = "Hosts Nurcholish Adam's entire Python ETL modeling scripts, spatial contact mapping modules, Neo4j Graph database loader scripts, and research manuscript publications.",
                codeSnippet = """
                    // AUTHORITATIVE OPEN-SOURCE CORE SYSTEM
                    // Project: SARS-CoV-2 (COVID-19) 3D Knowledge Graph
                    // Lead Researcher: Nurcholish Adam
                    
                    AUTHORITY REPOSITORY:
                    https://github.com/NurcholishAdam/SARS-CoV-2-3D-Knowledge-Graph
                    
                    PIPELINE COMPONENTS:
                    1. Data Ingestion (PDB coords + FASTA sequences)
                    2. Spatial resolving metrics (Euclidean matrices)
                    3. Neo4j graph generation queries (Cypher scripts)
                    4. 3D Web/Mobile dynamic renderings
                """.trimIndent(),
                schemaInfo = """
                    ORGANIZATION: github.com/NurcholishAdam
                    TARGET REPO: SARS-CoV-2-3D-Knowledge-Graph
                    LICENSE TYPE: Open Science Academic Research
                """.trimIndent(),
                icon = Icons.Default.Star
            )
        )
    }

    var selectedArchId by remember { mutableStateOf("neo4j") }
    val activeSubsc = remember(selectedArchId) { subsystems.find { it.id == selectedArchId } ?: subsystems[2] }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Left Column list selector
        Column(
            modifier = Modifier
                .width(135.dp)
                .fillMaxHeight()
                .background(DarkSurface)
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = "SARCOV STEPS",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            subsystems.forEach { sub ->
                val isSelected = sub.id == selectedArchId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedArchId = sub.id }
                        .background(if (isSelected) DarkCard else Color.Transparent)
                        .drawBehind { 
                            if (isSelected) {
                                drawRect(
                                    color = BioTeal,
                                    size = Size(3.dp.toPx(), size.height)
                                )
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = sub.title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) BioTeal else TextPrimary
                    )
                    Text(
                        text = sub.subtitle,
                        fontSize = 8.sp,
                        color = if (isSelected) TextPrimary else TextMuted,
                        maxLines = 1
                    )
                }
            }
        }
        VerticalDivider(color = BorderColor, modifier = Modifier.fillMaxHeight().width(1.dp))

        // Right Column Detail Inspector
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(12.dp)
        ) {
            // Header Info
            Text(
                text = activeSubsc.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = activeSubsc.subtitle,
                fontSize = 12.sp,
                color = BioGreen,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = activeSubsc.description,
                fontSize = 11.sp,
                color = TextSecondary
            )

            if (activeSubsc.id == "repo") {
                val context = LocalContext.current
                Button(
                    onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/NurcholishAdam/SARS-CoV-2-3D-Knowledge-Graph")
                        )
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BioTeal),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("launch_github_repo_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Open GitHub",
                        modifier = Modifier.size(16.dp).padding(end = 4.dp),
                        tint = Color.Black
                    )
                    Text(
                        text = "OPEN GITHUB REPOSITORY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Technical metadata pill
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "METADATA & SCHEMA SPECS",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = BioTeal,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Text(
                        text = activeSubsc.schemaInfo,
                        fontSize = 9.sp,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 12.sp
                    )
                }
            }

            // Real System Architecture Code Snippets!
            Text(
                text = "ENGINE REPO SOURCE IMPLEMENTATION",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TerminalGreen,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            text = activeSubsc.codeSnippet,
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary,
                                lineHeight = 13.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

// Simple internal helper scroll state since standard Android requires Compose scrolling
@Composable
fun rememberScrollState(): LazyListState = rememberLazyListState()

// ==========================================
// TAB 4: SECURE GEMINI AI RESEARCH ASSISTANT
// ==========================================

@Composable
fun GeminiChatScreen(viewModel: SarcovViewModel) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val listState = rememberLazyListState()

    // Keep chat scrolled automatically to latest response entries
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // API Key Helper Banner if placeholder key set
        val apiKeyExists = remember {
            try {
                val key = BuildConfig.GEMINI_API_KEY
                key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
            } catch (e: Exception) {
                false
            }
        }

        if (!apiKeyExists) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = BioAmber.copy(alpha = 0.1f)),
                border = BorderStroke(1.dp, BioAmber.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "API key alert",
                        tint = BioAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "OFFLINE ASSISTANT PROTOTYPE MODE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BioAmber,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "To enable live Gemini answers, customize your GEMINI_API_KEY inside the secure Secrets tab in AI Studio.",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Messages scrolling log
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            items(messages) { msg ->
                val cardBgColor = if (msg.isUser) BioTeal.copy(alpha = 0.1f) else DarkSurface
                val borderStrokeColor = if (msg.isUser) BioTeal.copy(alpha = 0.4f) else BorderColor
                val alignment = if (msg.isUser) Alignment.End else Alignment.Start

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = alignment
                ) {
                    // Chat Bubble container
                    Card(
                        modifier = Modifier.widthIn(max = 290.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = BorderStroke(1.dp, borderStrokeColor),
                        shape = RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomStart = if (msg.isUser) 12.dp else 0.dp,
                            bottomEnd = if (msg.isUser) 0.dp else 12.dp
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Speaker Badge
                            Text(
                                text = if (msg.isUser) "RESEARCH UNIT (YOU)" else "SARCOV SCIENTIFIC INTEL",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (msg.isUser) BioTeal else BioCoral,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            
                            Text(
                                text = msg.text,
                                fontSize = 12.sp,
                                color = TextPrimary,
                                lineHeight = 16.sp,
                                fontFamily = if (!msg.isUser && msg.text.contains("MATCH")) FontFamily.Monospace else FontFamily.Default
                            )
                        }
                    }
                }
            }

            // Animated live query loader indicator
            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = BioCoral,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Analyzing spatial clusters & fetching databases...",
                            fontSize = 11.sp,
                            color = BioCoral,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Quick bio questions panel
        val quickQuestions = listOf(
            "Show Spike Cypher query" to "Neo4j Cypher Code",
            "Explain Spike PDB:6VSB structure" to "RBD Binding Detail",
            "Detail SARS mutations Omicron" to "Omicron Mutant Variants"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            HorizontalDivider(color = BorderColor, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = "BIOLOGICAL PROMPTS",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickQuestions.forEach { (text, label) ->
                    Button(
                        onClick = { viewModel.sendChatMessage(text) },
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkCard,
                            contentColor = TextPrimary
                        ),
                        border = BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 8.5.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Text Input Form Box
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    textStyle = TextStyle(fontSize = 13.sp, color = Color.White),
                    placeholder = { Text(text = "Inquire on structural networks...", fontSize = 13.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input"),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkBg,
                        unfocusedContainerColor = DarkBg,
                        focusedBorderColor = BioTeal,
                        unfocusedBorderColor = BorderColor,
                        cursorColor = BioTeal,
                        focusedPlaceholderColor = TextSecondary,
                        unfocusedPlaceholderColor = TextSecondary
                    ),
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendChatMessage(inputText)
                                inputText = ""
                                keyboardController?.hide()
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                FloatingActionButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendChatMessage(inputText)
                            inputText = ""
                            keyboardController?.hide()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_button"),
                    containerColor = BioTeal,
                    contentColor = DarkBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Prompt query",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
fun TransparentLogoImage(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 42.dp,
    contentDescription: String = "SARS-CoV-19 Logo"
) {
    val context = LocalContext.current
    val processedLogo = remember(context) {
        try {
            val options = android.graphics.BitmapFactory.Options().apply {
                inMutable = true
            }
            val original = android.graphics.BitmapFactory.decodeResource(
                context.resources,
                R.drawable.sarcov_logo_1779700665394,
                options
            )
            if (original != null) {
                val width = original.width
                val height = original.height
                val pixels = IntArray(width * height)
                original.getPixels(pixels, 0, width, 0, 0, width, height)
                
                val visited = java.util.BitSet(width * height)
                val queue = java.util.LinkedList<Int>()
                
                fun isWhite(idx: Int): Boolean {
                    val color = pixels[idx]
                    val r = (color shr 16) and 0xFF
                    val g = (color shr 8) and 0xFF
                    val b = color and 0xFF
                    return r > 240 && g > 240 && b > 240
                }
                
                for (x in 0 until width) {
                    val topIdx = x
                    if (isWhite(topIdx)) {
                        queue.add(topIdx)
                        visited.set(topIdx)
                    }
                    val botIdx = (height - 1) * width + x
                    if (isWhite(botIdx)) {
                        queue.add(botIdx)
                        visited.set(botIdx)
                    }
                }
                for (y in 0 until height) {
                    val leftIdx = y * width
                    if (isWhite(leftIdx)) {
                        queue.add(leftIdx)
                        visited.set(leftIdx)
                    }
                    val rightIdx = y * width + (width - 1)
                    if (isWhite(rightIdx)) {
                        queue.add(rightIdx)
                        visited.set(rightIdx)
                    }
                }
                
                val dx = intArrayOf(-1, 1, 0, 0)
                val dy = intArrayOf(0, 0, -1, 1)
                
                while (!queue.isEmpty()) {
                    val currIdx = queue.poll()
                    pixels[currIdx] = 0x00000000
                    
                    val cx = currIdx % width
                    val cy = currIdx / width
                    
                    for (i in 0 until 4) {
                        val nx = cx + dx[i]
                        val ny = cy + dy[i]
                        if (nx in 0 until width && ny in 0 until height) {
                            val nIdx = ny * width + nx
                            if (!visited.get(nIdx)) {
                                val color = pixels[nIdx]
                                val r = (color shr 16) and 0xFF
                                val g = (color shr 8) and 0xFF
                                val b = color and 0xFF
                                if (r > 200 && g > 200 && b > 200) {
                                    queue.add(nIdx)
                                    visited.set(nIdx)
                                }
                            }
                        }
                    }
                }
                
                original.setPixels(pixels, 0, width, 0, 0, width, height)
                original.asImageBitmap()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    if (processedLogo != null) {
        Image(
            bitmap = processedLogo,
            contentDescription = contentDescription,
            modifier = modifier.size(size)
        )
    } else {
        Image(
            painter = painterResource(id = R.drawable.sarcov_logo_1779700665394),
            contentDescription = contentDescription,
            modifier = modifier.size(size)
        )
    }
}
