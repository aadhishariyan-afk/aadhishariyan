package com.example

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var viewModel: FluidViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setContent {
            MyApplicationTheme {
                val vm: FluidViewModel = viewModel()
                viewModel = vm

                Box(modifier = Modifier.fillMaxSize()) {
                    LiquidGlassApp(
                        viewModel = vm,
                        sensorActive = accelerometer != null
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // event.values[0] is negative when tilted right, positive when tilted left
            // event.values[1] is positive when tilted down/forward
            val rawX = -event.values[0]
            val rawY = event.values[1]

            // Normalize values for our fluid engine
            val gx = rawX * 0.15f
            val gy = rawY * 0.15f

            viewModel?.updateGravityDirection(gx, gy)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@Composable
fun LiquidGlassApp(
    viewModel: FluidViewModel,
    sensorActive: Boolean
) {
    val particles by viewModel.particles.collectAsState()
    val preset by viewModel.selectedPreset.collectAsState()
    val interactionMode by viewModel.interactionMode.collectAsState()
    val viscosity by viewModel.viscosity.collectAsState()
    val cohesion by viewModel.cohesion.collectAsState()
    val gravityScale by viewModel.gravityScale.collectAsState()
    val lightAngle by viewModel.lightAngle.collectAsState()
    val fusingEnabled by viewModel.fusingEnabled.collectAsState()

    val bodyColor by viewModel.bodyColor.collectAsState()
    val rimColor by viewModel.rimColor.collectAsState()
    val highlightColor by viewModel.highlightColor.collectAsState()

    val gravityX by viewModel.gravityX.collectAsState()
    val gravityY by viewModel.gravityY.collectAsState()

    var isPanelExpanded by remember { mutableStateOf(true) }
    val haptic = LocalHapticFeedback.current

    // Trigger haptics when viewModel emits collision events
    LaunchedEffect(key1 = Unit) {
        viewModel.hapticTrigger.collectLatest {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. FULLSCREEN MESH GRADIENT BACKDROP (Visual Asset generated dynamically!)
        Image(
            painter = painterResource(id = R.drawable.img_mesh_gradient_1784050243418),
            contentDescription = "Cosmic Fluid Gradient Wallpaper",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Subtle dark glass overlay to integrate the visual atmosphere beautifully
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
        )

        // 2. THE FLUID PHYSICS CANVAS
        LiquidGlassCanvas(
            particles = particles,
            bodyColor = bodyColor,
            rimColor = rimColor,
            highlightColor = highlightColor,
            lightAngle = lightAngle,
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )

        // 3. TITLE HEADER PANEL
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "LIQUID GLASS",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 5.sp,
                    color = Color.White,
                    modifier = Modifier.testTag("app_title")
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when (preset) {
                                    GlassPreset.VISION_PRO -> Color(0xFFD0BCFF)
                                    GlassPreset.LIQUID_MERCURY -> Color(0xFFD2D7DF)
                                    GlassPreset.AURORA_GLOW -> Color(0xFF2ECC71)
                                    GlassPreset.MAGMA_FLOW -> Color(0xFFD84315)
                                    GlassPreset.EMERALD_DEW -> Color(0xFF2ECC71)
                                    GlassPreset.CUSTOM -> Color(0xFFFFEB3B)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = preset.name.replace("_", " "),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.7f),
                        letterSpacing = 1.5.sp
                    )
                }
            }

            // Quick Clear Sandbox Button
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.clearSandbox()
                },
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    .testTag("clear_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear sandbox",
                    tint = Color.White
                )
            }
        }

        // 4. FLOATING INTERACTIVE GRAVITY MARBLE
        GravityControlMarble(
            gx = gravityX,
            gy = gravityY,
            sensorActive = sensorActive,
            onGravityUpdate = { nx, ny ->
                viewModel.updateGravityDirection(nx, ny)
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        )

        // 5. GLASSMORPHIC CONTROL CENTER PANEL
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            ControlCenterCard(
                isExpanded = isPanelExpanded,
                onToggleExpand = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isPanelExpanded = !isPanelExpanded
                },
                preset = preset,
                onSelectPreset = { viewModel.applyPreset(it) },
                interactionMode = interactionMode,
                onSelectMode = { viewModel.updateInteractionMode(it) },
                viscosity = viscosity,
                onViscosityChange = { viewModel.updateViscosity(it) },
                cohesion = cohesion,
                onCohesionChange = { viewModel.updateCohesion(it) },
                gravityScale = gravityScale,
                onGravityScaleChange = { viewModel.updateGravityScale(it) },
                lightAngle = lightAngle,
                onLightAngleChange = { viewModel.updateLightAngle(it) },
                fusingEnabled = fusingEnabled,
                onFusingChange = { viewModel.updateFusingEnabled(it) },
                bodyColor = bodyColor,
                onColorChange = { viewModel.updateCustomColors(it, rimColor, highlightColor) },
                onExplode = { viewModel.triggerExplosion() },
                onSpawnDrop = {
                    val dropX = (300f + Math.random().toFloat() * 400f)
                    viewModel.spawnDroplet(dropX, 150f, 40f + Math.random().toFloat() * 20f)
                }
            )
        }
    }
}

@Composable
fun LiquidGlassCanvas(
    particles: List<FluidParticle>,
    bodyColor: Color,
    rimColor: Color,
    highlightColor: Color,
    lightAngle: Float,
    viewModel: FluidViewModel,
    modifier: Modifier = Modifier
) {
    val angleRad = Math.toRadians(lightAngle.toDouble())

    Canvas(
        modifier = modifier
            .testTag("liquid_glass_canvas")
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        viewModel.handleTouchInteraction(offset.x, offset.y, false)
                    },
                    onDrag = { change, _ ->
                        viewModel.handleTouchInteraction(change.position.x, change.position.y, true)
                        change.consume()
                    }
                )
            }
            .onSizeChanged { size ->
                viewModel.updateCanvasSize(size.width.toFloat(), size.height.toFloat())
            }
    ) {
        if (particles.isEmpty()) return@Canvas

        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas

            // ==========================================
            // LAYER 1: THE LIQUID BORDERS (The Glass Rim)
            // ==========================================
            // We use standard saveLayer to apply a customized ColorMatrix thresholding filter on the alpha channel!
            val rimPaint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                // ColorMatrix: alpha' = alpha * 38f - 18.5f * 255.
                // This maps soft blurred edges into a sharp solid profile.
                colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 38f, -18.5f * 255f
                )))
            }

            val rimLayer = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, rimPaint)

            particles.forEach { p ->
                // Draw rim brush: a radial gradient fading out to transparent, representing fuzzy boundary
                val shader = android.graphics.RadialGradient(
                    p.x, p.y, p.radius * 1.55f,
                    intArrayOf(rimColor.toArgb(), rimColor.copy(alpha = 0.5f).toArgb(), android.graphics.Color.TRANSPARENT),
                    floatArrayOf(0f, 0.45f, 1.0f),
                    android.graphics.Shader.TileMode.CLAMP
                )
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    this.shader = shader
                }
                nativeCanvas.drawCircle(p.x, p.y, p.radius * 1.55f, paint)
            }
            nativeCanvas.restoreToCount(rimLayer)

            // ==========================================
            // LAYER 2: THE LIQUID INTERIOR (The Fluid Body)
            // ==========================================
            val bodyPaint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 40f, -19.5f * 255f
                )))
            }

            val bodyLayer = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, bodyPaint)

            particles.forEach { p ->
                val shader = android.graphics.RadialGradient(
                    p.x, p.y, p.radius * 1.42f,
                    intArrayOf(bodyColor.toArgb(), bodyColor.copy(alpha = 0.45f).toArgb(), android.graphics.Color.TRANSPARENT),
                    floatArrayOf(0f, 0.42f, 1.0f),
                    android.graphics.Shader.TileMode.CLAMP
                )
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    this.shader = shader
                }
                nativeCanvas.drawCircle(p.x, p.y, p.radius * 1.42f, paint)
            }
            nativeCanvas.restoreToCount(bodyLayer)

            // ==========================================
            // LAYER 3: SPECULAR HIGHLIGHT REACTION
            // ==========================================
            // Handled directly without saveLayers for peak performance!
            particles.forEach { p ->
                // Calculate 3D offset towards the customized light source angle
                val offsetX = (cos(angleRad) * p.radius * 0.42f).toFloat()
                val offsetY = (-sin(angleRad) * p.radius * 0.42f).toFloat()

                val specX = p.x + offsetX
                val specY = p.y + offsetY
                val specRadius = p.radius * 0.45f

                if (specRadius > 2f) {
                    val specShader = android.graphics.RadialGradient(
                        specX, specY, specRadius,
                        intArrayOf(highlightColor.toArgb(), highlightColor.copy(alpha = 0.4f).toArgb(), android.graphics.Color.TRANSPARENT),
                        floatArrayOf(0f, 0.35f, 1.0f),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    val specPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        shader = specShader
                    }
                    nativeCanvas.drawCircle(specX, specY, specRadius, specPaint)
                }

                // Small secondary glow reflection
                val bounceX = p.x - offsetX * 0.45f
                val bounceY = p.y - offsetY * 0.45f
                val bounceR = p.radius * 0.28f

                if (bounceR > 1.5f) {
                    val bShader = android.graphics.RadialGradient(
                        bounceX, bounceY, bounceR,
                        intArrayOf(highlightColor.copy(alpha = 0.25f).toArgb(), android.graphics.Color.TRANSPARENT),
                        null,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    val bPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        shader = bShader
                    }
                    nativeCanvas.drawCircle(bounceX, bounceY, bounceR, bPaint)
                }
            }
        }
    }
}

@Composable
fun GravityControlMarble(
    gx: Float,
    gy: Float,
    sensorActive: Boolean,
    onGravityUpdate: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Limits inside container bounds
    val trackRadius = 55.dp
    val marbleSize = 32.dp

    // Smoothly animated marble positions
    val animX by animateFloatAsState(targetValue = gx, label = "gx")
    val animY by animateFloatAsState(targetValue = gy, label = "gy")

    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .testTag("gravity_indicator")
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(22.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "GRAVITY",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.6f),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size(trackRadius * 2)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val localX = change.position.x - size.width / 2
                        val localY = change.position.y - size.height / 2
                        val dist = sqrt(localX * localX + localY * localY)
                        val maxDist = size.width / 2

                        val scale = 1.5f
                        val nx = (localX / maxDist * scale).coerceIn(-scale, scale)
                        val ny = (localY / maxDist * scale).coerceIn(-scale, scale)

                        onGravityUpdate(nx, ny)
                        change.consume()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Reference cross lines
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.05f))
            )
            Box(
                modifier = Modifier
                    .height(1.dp)
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f))
            )

            // Gravity glowing sphere
            val scaleFactor = trackRadius.value - (marbleSize.value / 2f)
            val posX = (animX * scaleFactor * 0.62f).dp
            val posY = (animY * scaleFactor * 0.62f).dp

            Box(
                modifier = Modifier
                    .offset(x = posX, y = posY)
                    .size(marbleSize)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFE2F3FF), Color(0xFF00C6FF), Color(0xFF0072FF)),
                        ),
                        shape = CircleShape
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            ) {
                // Shiny lens reflection
                Box(
                    modifier = Modifier
                        .offset(x = 4.dp, y = 4.dp)
                        .size(8.dp)
                        .background(Color.White.copy(alpha = 0.8f), CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (sensorActive) "TILT PHONE" else "DRAG BALL",
            fontSize = 8.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White.copy(alpha = 0.45f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlCenterCard(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    preset: GlassPreset,
    onSelectPreset: (GlassPreset) -> Unit,
    interactionMode: InteractionMode,
    onSelectMode: (InteractionMode) -> Unit,
    viscosity: Float,
    onViscosityChange: (Float) -> Unit,
    cohesion: Float,
    onCohesionChange: (Float) -> Unit,
    gravityScale: Float,
    onGravityScaleChange: (Float) -> Unit,
    lightAngle: Float,
    onLightAngleChange: (Float) -> Unit,
    fusingEnabled: Boolean,
    onFusingChange: (Boolean) -> Unit,
    bodyColor: Color,
    onColorChange: (Color) -> Unit,
    onExplode: () -> Unit,
    onSpawnDrop: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.11f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("control_center")
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Toggle Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Controls",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "GLASS CONTROLS",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = "Expand/Collapse",
                    tint = Color.White
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(350.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Category: Presets
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.Category, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PRESETS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = 3,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassPreset.values().forEach { pr ->
                            if (pr != GlassPreset.CUSTOM) {
                                val selected = preset == pr
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (selected) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.05f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.1f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onSelectPreset(pr)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .testTag("preset_${pr.name.lowercase()}")
                                ) {
                                    Text(
                                        text = pr.name.replace("_", " "),
                                        color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Category: Interaction Modes
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("TOUCH FORCE ACTION", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        InteractionMode.values().forEach { mode ->
                            val selected = interactionMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (selected) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.05f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.1f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSelectMode(mode)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.name,
                                    color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                                    fontSize = 9.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Category: Custom Color Swatches
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.ColorLens, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CUSTOM COLOR SELECTOR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val colors = listOf(
                            Color(0x70314FF1), // Cosmic Sapphire
                            Color(0x9500E676), // Bright Emerald
                            Color(0x80E040FB), // Psychedelic Violet
                            Color(0xFFC0C5CE), // Platinum Chrome
                            Color(0xFFD84315), // Vulcan Magma
                            Color(0x65FF0055)  // Rose Pearl
                        )
                        colors.forEach { c ->
                            val isSelected = bodyColor.toArgb() == c.toArgb()
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onColorChange(c)
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Category: Fine Physics Parameters
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PHYSICS SIMULATION TUNER", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }

                    // 1. Viscosity (Damping)
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Viscosity (Friction)", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                            Text(String.format("%.2f", viscosity), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = viscosity,
                            onValueChange = onViscosityChange,
                            valueRange = 0.90f..0.995f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White.copy(alpha = 0.7f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                            )
                        )
                    }

                    // 2. Cohesion (Surface Tension)
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Surface Tension Cohesion", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                            Text(String.format("%.2f", cohesion), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = cohesion,
                            onValueChange = onCohesionChange,
                            valueRange = 0.02f..0.35f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White.copy(alpha = 0.7f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                            )
                        )
                    }

                    // 3. Gravity Scale
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Gravity Intensity", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                            Text(String.format("%.2f", gravityScale), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = gravityScale,
                            onValueChange = onGravityScaleChange,
                            valueRange = 0.0f..1.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White.copy(alpha = 0.7f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                            )
                        )
                    }

                    // 4. Specular Light Source Angle
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("3D Light Source Direction", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                            Text("${lightAngle.roundToInt()}°", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = lightAngle,
                            onValueChange = onLightAngleChange,
                            valueRange = 0f..360f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White.copy(alpha = 0.7f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                            )
                        )
                    }

                    // Toggle option: Fusing Fusion Mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Deep Fusing Mode", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Drops merge into giant blobs or split upon impact", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
                        }
                        Switch(
                            checked = fusingEnabled,
                            onCheckedChange = onFusingChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color.White.copy(alpha = 0.45f),
                                uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.08f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Dynamic Splashes Operations Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSpawnDrop()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.22f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RAIN DROP", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onExplode()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.22f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Cached, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("EXPLODE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

