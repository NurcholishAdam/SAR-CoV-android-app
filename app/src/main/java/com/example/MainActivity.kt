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
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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

data class Spike3D(
    val id: String,
    val name: String,
    val theta: Float, // polar coordinate angle
    val phi: Float,   // polar coordinate angle
    val radius: Float = 140f,
    val pdbId: String,
    val scaleFactor: Float = 1f,
    val desc: String,
    val color: Color
)

// ==========================================
// VIEWMODEL FOR BUSINESS LOGIC & CHAT
// ==========================================

class SarcovViewModel : ViewModel() {
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage("welcome", "Hello! I am your SARCOV AI Scientific Assistant. I am trained on Nurcholish Adam's SARS-CoV-2 3D Knowledge Graph system architecture (https://github.com/NurcholishAdam/SARS-CoV-2-3D-Knowledge-Graph-1).\n\nAsk me anything about Neo4j Cypher queries, Protein structures (Spike, Envelope), variants like Omicron, or how the spatial processing pipeline works!", false)
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    // Interactive custom cypher console simulation query
    private val _currentCypherFilter = MutableStateFlow("MATCH (n) RETURN n")
    val currentCypherFilter: StateFlow<String> = _currentCypherFilter.asStateFlow()

    fun updateCypherFilter(query: String) {
        _currentCypherFilter.value = query
    }

    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        val userMsgId = System.currentTimeMillis().toString()
        _chatMessages.value = _chatMessages.value + ChatMessage(userMsgId, text, true)
        _isChatLoading.value = true

        viewModelScope.launch {
            val systemInstructions = """
                You are "SARCOV AI Assistant", an expert bio-informatics agent designed to explain the system architecture of the SARS-CoV-2 3D Knowledge Graph project (created by researcher Nurcholish Adam).
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
                val responseSimulated = simulateScientificAnswer(text)
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
                val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "No scientific response could be resolved by the engine."
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
    var selectedTab by remember { mutableStateOf(0) }
    
    val tabs = listOf(
        "🌐 Network Graph",
        "🦠 3D Virion",
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
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Sarcov Core Logo",
                        tint = BioCoral,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(end = 6.dp)
                    )
                    Column {
                        Text(
                            text = "SARCOV",
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        Text(
                            text = "SARS-CoV-2 3D Knowledge Graph System",
                            fontSize = 11.sp,
                            color = BioTeal,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Status Lights
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
                    onClick = { selectedTab = index },
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
                0 -> NetworkGraphScreen(viewModel)
                1 -> Virion3DScreen()
                2 -> ArchitectureBlueprintsScreen()
                3 -> GeminiChatScreen(viewModel)
            }
        }
    }
}

// ==========================================
// TAB 1: PHYSICS NETWORK GRAPH EXPLORER
// ==========================================

@Composable
fun NetworkGraphScreen(viewModel: SarcovViewModel) {
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

@Composable
fun Virion3DScreen() {
    // 3D rotation states for the virus sphere
    var rotX by remember { mutableStateOf(-0.3f) } // Rad angles
    var rotY by remember { mutableStateOf(0.4f) }

    // Resolve pre-allocated scale factors to avoid coroutine suspension resolving bugs
    val density = LocalDensity.current
    val hitDistPx = remember(density) { with(density) { 30.dp.toPx() } }

    // List of structural elements protruding from the nucleocapsid
    val spikes = remember {
        listOf(
            Spike3D("S1", "Spike 1 (Pre-fusion)", 0.2f, 0.4f, pdbId = "6VSB", desc = "Active receptor binding domain (RBD) pointing UP.", color = BioCoral),
            Spike3D("S2", "Spike 2 (RBD down)", -0.4f, 1.2f, pdbId = "6VSB", desc = "RBD domain in DOWN conformation, avoiding host antibodies.", color = BioCoral),
            Spike3D("S3", "Spike 3 (Active fusion)", 1.1f, -0.6f, pdbId = "6VSB", desc = "Engaging target human tissue cells.", color = BioCoral),
            Spike3D("S4", "Spike 4 (Target bound)", -1.3f, -0.8f, pdbId = "6VSB", desc = "Bound with high affinity to host ACE2 protein.", color = BioTeal),
            Spike3D("E1", "Envelope E-Channel", 0.5f, 2.3f, pdbId = "5X29", desc = "Pentameric channel membrane protein assisting virion formation.", color = BioGreen),
            Spike3D("E2", "Envelope E-Channel", -0.8f, 2.8f, pdbId = "5X29", desc = "Pentameric pore. Targets of active therapeutic research.", color = BioGreen),
            Spike3D("M1", "M-Protein Matrix", 1.8f, -2.1f, pdbId = "7MGS", desc = "Gives the SARS virus envelope its canonical oval frame.", color = BioBlue),
            Spike3D("M2", "M-Protein Matrix", -1.9f, 0.5f, pdbId = "7MGS", desc = "Binds secondary envelope sections together.", color = BioBlue),
            Spike3D("RNA_CORE", "Encapsulated Nucleocapsid RNA", 0f, 0f, radius = 0f, pdbId = "6M3M", desc = "Internal single-stranded RNA string wrapped in Nucleocapsid polymers.", color = BioGreen)
        )
    }

    var activeSpikeDetail by remember { mutableStateOf<Spike3D?>(spikes[0]) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Explanatory label
        Text(
            text = "3D VIRAL MOLECULAR ASSEMBLY",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
        Text(
            text = "Animate, pan, or swipe to rotate the coordinates of the SARS-CoV-2 protein capsid structures. Click individual proteins to check atomic configurations.",
            fontSize = 11.sp,
            color = TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            textAlign = TextAlign.Start
        )

        // 3D Canvas Box container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.2f))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        // Convert drag displacement to 3D rotation angles
                        rotY += dragAmount.x * 0.006f
                        rotX -= dragAmount.y * 0.006f
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                
                // Draw ambient background space grid
                drawCircle(
                    color = BorderColor.copy(alpha = 0.05f),
                    radius = 200.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 1f)
                )

                // 1. Draw Main Viral Envelope Membrane Circle (underlay)
                val coreRadius = 80.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(DarkSurface, DarkCard, BorderColor.copy(alpha = 0.3f)),
                        center = Offset(cx - 20f, cy - 20f),
                        radius = coreRadius
                    ),
                    radius = coreRadius,
                    center = Offset(cx, cy)
                )
                // Glossy outline trimer border
                drawCircle(
                    color = BorderColor.copy(alpha = 0.6f),
                    radius = coreRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.dp.toPx())
                )

                // Draw secondary nucleocapsid core inside envelope (represented by floating points)
                val corePointsCount = 30
                for (p in 0..corePointsCount) {
                    val pAngle = p * (2 * PI / corePointsCount).toFloat()
                    val coreDist = 45.dp.toPx() * sin(p * 2.3f).absoluteValue
                    val px = cx + coreDist * cos(pAngle + rotY * 0.8f)
                    val py = cy + coreDist * sin(pAngle + rotX * 0.8f)
                    drawCircle(
                        color = BioGreen.copy(alpha = 0.4f),
                        radius = 2.dp.toPx(),
                        center = Offset(px, py)
                    )
                }

                // 2. Rotate, project, and draw 3D Spikes and proteins
                // We sort proteins by depth 'z' coordinates (back-to-front rendering) to ensure authentic spatial depth overlap!
                val projectedSpikes = spikes.map { spike ->
                    // Transform polar coordinate to 3D Cartesian vectors around sphere origin
                    val x0 = spike.radius * sin(spike.theta) * cos(spike.phi)
                    val y0 = spike.radius * sin(spike.theta) * sin(spike.phi)
                    val z0 = spike.radius * cos(spike.theta)

                    // Apply rotation 3D transformation matrices
                    val x1 = x0 * cos(rotY) - z0 * sin(rotY)
                    val z1 = x0 * sin(rotY) + z0 * cos(rotY)
                    val y1 = y0

                    val y2 = y1 * cos(rotX) - z1 * sin(rotX)
                    val z2 = y1 * sin(rotX) + z1 * cos(rotX)
                    val x2 = x1

                    // Final Perspective projection variables
                    val distanceFactor = 320f
                    val scale = distanceFactor / (distanceFactor + z2)
                    
                    val projX = cx + x2 * scale
                    val projY = cy + y2 * scale
                    
                    object {
                        val base = spike
                        val px = projX
                        val py = projY
                        val depthZ = z2
                        val renderSize = 14.dp.toPx() * scale
                    }
                }.sortedBy { it.depthZ } // Sort back to front (low Z coordinate to high Z)

                // Render the sorted elements
                projectedSpikes.forEach { proj ->
                    val spikeColor = proj.base.color
                    val centerAlpha = if (proj.depthZ < 0) 0.35f else 1.0f

                    // Draw Spike connector stalk extending outwards from main envelope boundary
                    if (proj.base.radius > 0f) {
                        val wallX = cx + (proj.px - cx) * (coreRadius / (coreRadius + (proj.base.radius - coreRadius)))
                        val wallY = cy + (proj.py - cy) * (coreRadius / (coreRadius + (proj.base.radius - coreRadius)))

                        drawLine(
                            color = if (proj.depthZ < 0) BorderColor.copy(alpha = 0.2f) else spikeColor.copy(alpha = 0.6f),
                            start = Offset(wallX, wallY),
                            end = Offset(proj.px, proj.py),
                            strokeWidth = if (proj.depthZ > 0) 6f else 2.5f
                        )
                    }

                    // Draw actual structural node representation
                    if (proj.base.radius == 0f) {
                        drawCircle(
                            color = BioGreen.copy(alpha = 0.7f),
                            radius = 20.dp.toPx(),
                            center = Offset(proj.px, proj.py),
                            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f))
                        )
                    } else {
                        drawCircle(
                            color = if (proj.depthZ < 0) DarkSurface.copy(alpha = 0.3f) else DarkSurface,
                            radius = proj.renderSize,
                            center = Offset(proj.px, proj.py)
                        )

                        drawCircle(
                            color = spikeColor,
                            radius = proj.renderSize,
                            center = Offset(proj.px, proj.py),
                            style = Stroke(width = if (activeSpikeDetail?.id == proj.base.id) 4.dp.toPx() else 1.5.dp.toPx()),
                            alpha = centerAlpha
                        )

                        drawCircle(
                            color = spikeColor.copy(alpha = 0.3f),
                            radius = proj.renderSize - 3.dp.toPx(),
                            center = Offset(proj.px, proj.py),
                            alpha = centerAlpha
                        )
                    }
                }
            }

            // Click detector layer logic
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(rotX, rotY) {
                        detectTapGestures { tapLoc ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f

                            // Project spikes coordinates to evaluate nearest touch coordinate
                            val clickedSpike = spikes.minByOrNull { spike ->
                                val x0 = spike.radius * sin(spike.theta) * cos(spike.phi)
                                val y0 = spike.radius * sin(spike.theta) * sin(spike.phi)
                                val z0 = spike.radius * cos(spike.theta)

                                var x1 = x0 * cos(rotY) - z0 * sin(rotY)
                                var z1 = x0 * sin(rotY) + z0 * cos(rotY)
                                var y1 = y0

                                val y2 = y1 * cos(rotX) - z1 * sin(rotX)
                                val z2 = y1 * sin(rotX) + z1 * cos(rotX)
                                val x2 = x1

                                val distanceFactor = 320f
                                val scale = distanceFactor / (distanceFactor + z2)
                                val px = cx + x2 * scale
                                val py = cy + y2 * scale

                                sqrt((px - tapLoc.x).pow(2) + (py - tapLoc.y).pow(2))
                            }

                            if (clickedSpike != null) {
                                val x0 = clickedSpike.radius * sin(clickedSpike.theta) * cos(clickedSpike.phi)
                                val y0 = clickedSpike.radius * sin(clickedSpike.theta) * sin(clickedSpike.phi)
                                val z0 = clickedSpike.radius * cos(clickedSpike.theta)

                                var x1 = x0 * cos(rotY) - z0 * sin(rotY)
                                var z1 = x0 * sin(rotY) + z0 * cos(rotY)
                                var y1 = y0

                                val y2 = y1 * cos(rotX) - z1 * sin(rotX)
                                val z2 = y1 * sin(rotX) + z1 * cos(rotX)
                                val x2 = x1

                                val distanceFactor = 320f
                                val scale = distanceFactor / (distanceFactor + z2)
                                val px = cx + x2 * scale
                                val py = cy + y2 * scale

                                val hitDist = sqrt((px - tapLoc.x).pow(2) + (py - tapLoc.y).pow(2))
                                if (hitDist < hitDistPx) {
                                    activeSpikeDetail = clickedSpike
                                }
                            }
                        }
                    }
            ) {}
        }

        // Active highlighted structural details drawer
        AnimatedVisibility(
            visible = activeSpikeDetail != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier.fillMaxWidth()
        ) {
            activeSpikeDetail?.let { spike ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(spike.color, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = spike.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            
                            Text(
                                text = "PDB ID: ${spike.pdbId}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = BioTeal
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = spike.desc,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Divider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = BorderColor.copy(alpha = 0.5f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = "POLAR COORDINATE", fontSize = 8.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                                Text(
                                    text = "θ: ${"%.2f".format(spike.theta)} rad | φ: ${"%.2f".format(spike.phi)} rad",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "SURFACE EXPOSURE", fontSize = 8.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                                Text(
                                    text = if (spike.radius > 100) "HIGHLY EXPOSED" else "INTERNAL CAPSID",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (spike.radius > 100) BioCoral else BioGreen,
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
