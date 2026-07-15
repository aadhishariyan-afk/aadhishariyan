package com.example

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class GlassPreset {
    VISION_PRO,
    LIQUID_MERCURY,
    AURORA_GLOW,
    MAGMA_FLOW,
    EMERALD_DEW,
    CUSTOM
}

enum class InteractionMode {
    VORTEX,
    REPEL,
    ATTRACT,
    SPAWN
}

class FluidViewModel : ViewModel() {

    private val _particles = MutableStateFlow<List<FluidParticle>>(emptyList())
    val particles: StateFlow<List<FluidParticle>> = _particles.asStateFlow()

    // Interactive customizers (exposed to UI)
    private val _selectedPreset = MutableStateFlow(GlassPreset.VISION_PRO)
    val selectedPreset: StateFlow<GlassPreset> = _selectedPreset.asStateFlow()

    private val _interactionMode = MutableStateFlow(InteractionMode.ATTRACT)
    val interactionMode: StateFlow<InteractionMode> = _interactionMode.asStateFlow()

    private val _viscosity = MutableStateFlow(0.97f) // damping: lower is stickier
    val viscosity: StateFlow<Float> = _viscosity.asStateFlow()

    private val _cohesion = MutableStateFlow(0.12f) // surface tension attraction factor
    val cohesion: StateFlow<Float> = _cohesion.asStateFlow()

    private val _gravityScale = MutableStateFlow(0.25f) // gravity intensity
    val gravityScale: StateFlow<Float> = _gravityScale.asStateFlow()

    private val _lightAngle = MutableStateFlow(45f) // light highlight angle in degrees
    val lightAngle: StateFlow<Float> = _lightAngle.asStateFlow()

    private val _gravityX = MutableStateFlow(0f)
    val gravityX: StateFlow<Float> = _gravityX.asStateFlow()

    private val _gravityY = MutableStateFlow(0.4f)
    val gravityY: StateFlow<Float> = _gravityY.asStateFlow()

    private val _fusingEnabled = MutableStateFlow(true)
    val fusingEnabled: StateFlow<Boolean> = _fusingEnabled.asStateFlow()

    // Glass Color Customizers
    private val _bodyColor = MutableStateFlow(Color(0x502A5AEE))
    val bodyColor: StateFlow<Color> = _bodyColor.asStateFlow()

    private val _rimColor = MutableStateFlow(Color(0xB0FFFFFF))
    val rimColor: StateFlow<Color> = _rimColor.asStateFlow()

    private val _highlightColor = MutableStateFlow(Color.White)
    val highlightColor: StateFlow<Color> = _highlightColor.asStateFlow()

    // Trigger haptic vibration in the UI
    private val _hapticTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val hapticTrigger: SharedFlow<Unit> = _hapticTrigger.asSharedFlow()

    private var canvasWidth = 1080f
    private var canvasHeight = 1920f
    private var simJob: Job? = null
    private var nextParticleId = 0

    init {
        applyPreset(GlassPreset.VISION_PRO)
        startSimulation()
    }

    fun updateCanvasSize(width: Float, height: Float) {
        if (width > 0 && height > 0 && (canvasWidth != width || canvasHeight != height)) {
            canvasWidth = width
            canvasHeight = height
            reinitPresetParticles()
        }
    }

    private fun startSimulation() {
        simJob?.cancel()
        simJob = viewModelScope.launch {
            while (true) {
                stepPhysics()
                delay(16) // ~60 FPS update
            }
        }
    }

    private fun stepPhysics() {
        val currentParticles = _particles.value.map { it.copy() }
        if (currentParticles.isEmpty()) return

        val gx = _gravityX.value * _gravityScale.value
        val gy = _gravityY.value * _gravityScale.value
        val damp = _viscosity.value
        val tension = _cohesion.value
        val limitW = canvasWidth
        val limitH = canvasHeight

        // Apply forces, friction, and integration
        currentParticles.forEach { p ->
            // Gravity
            p.vx += gx
            p.vy += gy

            // Drag / Damping
            p.vx *= damp
            p.vy *= damp

            // Position update
            p.x += p.vx
            p.y += p.vy
        }

        // Particle-to-Particle interactions (Cohesion & Collision Resolution)
        val numParticles = currentParticles.size
        val resolved = BooleanArray(numParticles) { false }
        val toRemove = mutableSetOf<Int>()

        for (i in 0 until numParticles) {
            val pi = currentParticles[i]
            if (toRemove.contains(pi.id)) continue

            for (j in (i + 1) until numParticles) {
                val pj = currentParticles[j]
                if (toRemove.contains(pj.id)) continue

                val dx = pj.x - pi.x
                val dy = pj.y - pi.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist == 0f) continue

                val sumR = pi.radius + pj.radius
                val maxInteractionDist = sumR * 1.8f

                if (dist < maxInteractionDist) {
                    // 1. Surface Tension Attraction (Cohesion force)
                    val overlapIntensity = (maxInteractionDist - dist) / maxInteractionDist
                    val force = tension * overlapIntensity * 2.2f
                    val fx = (dx / dist) * force
                    val fy = (dy / dist) * force

                    pi.vx += fx / pi.mass
                    pi.vy += fy / pi.mass
                    pj.vx -= fx / pj.mass
                    pj.vy -= fy / pj.mass

                    // 2. Overlap / Elastic Collision Bounce
                    if (dist < sumR) {
                        // Deep overlap fusing mode check
                        if (_fusingEnabled.value && dist < sumR * 0.45f) {
                            // Fuse the two particles!
                            val totalMass = pi.mass + pj.mass
                            val newRadius = sqrt(pi.radius * pi.radius + pj.radius * pj.radius)

                            // Conserve momentum
                            pi.vx = (pi.vx * pi.mass + pj.vx * pj.mass) / totalMass
                            pi.vy = (pi.vy * pi.mass + pj.vy * pj.mass) / totalMass
                            pi.radius = newRadius.coerceAtMost(160f) // limit max size to keep sandbox responsive

                            toRemove.add(pj.id)
                            viewModelScope.launch { _hapticTrigger.emit(Unit) }
                            continue
                        }

                        // Push apart to prevent intersection collapse
                        val overlap = sumR - dist
                        val pushX = (dx / dist) * overlap * 0.52f
                        val pushY = (dy / dist) * overlap * 0.52f

                        pi.x -= pushX
                        pi.y -= pushY
                        pj.x += pushX
                        pj.y += pushY

                        // Bounce impulse calculation
                        val rvx = pj.vx - pi.vx
                        val rvy = pj.vy - pi.vy
                        val velAlongNormal = rvx * (dx / dist) + rvy * (dy / dist)

                        if (velAlongNormal < 0) {
                            val restitution = 0.45f // sticky glass-like bounce
                            val impulse = -(1f + restitution) * velAlongNormal / (1f / pi.mass + 1f / pj.mass)
                            val rx = (dx / dist) * impulse
                            val ry = (dy / dist) * impulse

                            pi.vx -= rx / pi.mass
                            pi.vy -= ry / pi.mass
                            pj.vx += rx / pj.mass
                            pj.vy += ry / pj.mass
                        }
                    }
                }
            }
        }

        // Apply boundaries bounding with satisfaction bouncing
        currentParticles.forEach { p ->
            val padding = p.radius
            var hitBoundary = false

            // Left
            if (p.x < padding) {
                p.x = padding
                p.vx = -p.vx * 0.65f
                hitBoundary = true
            }
            // Right
            else if (p.x > limitW - padding) {
                p.x = limitW - padding
                p.vx = -p.vx * 0.65f
                hitBoundary = true
            }

            // Top
            if (p.y < padding) {
                p.y = padding
                p.vy = -p.vy * 0.65f
                hitBoundary = true
            }
            // Bottom
            else if (p.y > limitH - padding) {
                p.y = limitH - padding
                p.vy = -p.vy * 0.65f
                hitBoundary = true
            }

            if (hitBoundary && (sqrt(p.vx * p.vx + p.vy * p.vy) > 3f)) {
                // Large splash splash splits large particles on hard impacts
                if (_fusingEnabled.value && p.radius > 65f && sqrt(p.vx * p.vx + p.vy * p.vy) > 15f) {
                    // Split into 3 smaller ones!
                    val smallerR = p.radius * 0.55f
                    p.radius = smallerR
                    val subParticle1 = FluidParticle(
                        id = ++nextParticleId,
                        x = (p.x + p.radius * 0.5f).coerceIn(smallerR, limitW - smallerR),
                        y = (p.y + p.radius * 0.5f).coerceIn(smallerR, limitH - smallerR),
                        vx = p.vx + (Math.random().toFloat() - 0.5f) * 4f,
                        vy = p.vy + (Math.random().toFloat() - 0.5f) * 4f,
                        radius = smallerR
                    )
                    viewModelScope.launch { _hapticTrigger.emit(Unit) }
                    // Insert dynamically later
                }
            }
        }

        // Rebuild clean particle list omitting fused ones
        val remaining = currentParticles.filter { !toRemove.contains(it.id) }
        _particles.value = remaining
    }

    // Interactive Touch Responses
    fun handleTouchInteraction(tx: Float, ty: Float, isMoving: Boolean) {
        val current = _particles.value
        val mode = _interactionMode.value

        if (mode == InteractionMode.SPAWN) {
            // Spawn new glass droplets
            if (current.size < 90) { // Limit for peak performance
                spawnDroplet(tx, ty)
            }
            return
        }

        _particles.value = current.map { p ->
            val dx = tx - p.x
            val dy = ty - p.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist == 0f) return@map p

            when (mode) {
                InteractionMode.ATTRACT -> {
                    // Gravity attraction force to finger
                    if (dist < 450f) {
                        val force = (450f - dist) / 450f * 1.8f
                        p.vx += (dx / dist) * force
                        p.vy += (dy / dist) * force
                    }
                }
                InteractionMode.REPEL -> {
                    // Push away from finger
                    if (dist < 320f) {
                        val force = (320f - dist) / 320f * 3.5f
                        p.vx -= (dx / dist) * force
                        p.vy -= (dy / dist) * force
                    }
                }
                InteractionMode.VORTEX -> {
                    // Spin around touch point
                    if (dist < 400f) {
                        val force = (400f - dist) / 400f * 2.2f
                        // Perpendicular vector
                        val px = -dy / dist
                        val py = dx / dist
                        p.vx += px * force
                        p.vy += py * force
                    }
                }
                else -> {}
            }
            p
        }
    }

    fun spawnDroplet(x: Float, y: Float, customR: Float? = null) {
        val radius = customR ?: (35f + Math.random().toFloat() * 20f)
        val newParticle = FluidParticle(
            id = ++nextParticleId,
            x = x.coerceIn(radius, canvasWidth - radius),
            y = y.coerceIn(radius, canvasHeight - radius),
            vx = (Math.random().toFloat() - 0.5f) * 6f,
            vy = (Math.random().toFloat() - 0.5f) * 6f,
            radius = radius
        )
        _particles.value = _particles.value + newParticle
        viewModelScope.launch { _hapticTrigger.emit(Unit) }
    }

    fun triggerExplosion() {
        val centerW = canvasWidth / 2f
        val centerH = canvasHeight / 2f

        _particles.value = _particles.value.map { p ->
            val dx = p.x - centerW
            val dy = p.y - centerH
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val force = 35f * (1000f - dist.coerceAtMost(1000f)) / 1000f
            p.vx += (dx / dist) * force
            p.vy += (dy / dist) * force
            p
        }
        viewModelScope.launch { _hapticTrigger.emit(Unit) }
    }

    fun clearSandbox() {
        _particles.value = emptyList()
        viewModelScope.launch { _hapticTrigger.emit(Unit) }
    }

    fun applyPreset(preset: GlassPreset) {
        _selectedPreset.value = preset
        when (preset) {
            GlassPreset.VISION_PRO -> {
                _viscosity.value = 0.98f
                _cohesion.value = 0.14f
                _gravityScale.value = 0.3f
                _bodyColor.value = Color(0x60314FF1) // semi transparent violet-blue
                _rimColor.value = Color(0xBBCEE4FF)
                _highlightColor.value = Color.White
                _fusingEnabled.value = true
            }
            GlassPreset.LIQUID_MERCURY -> {
                _viscosity.value = 0.94f // metallic friction
                _cohesion.value = 0.22f // dense surface tension
                _gravityScale.value = 0.6f // heavy metal
                _bodyColor.value = Color(0xFFC0C5CE) // solid shiny silver
                _rimColor.value = Color(0xFFEBEFF5)
                _highlightColor.value = Color.White
                _fusingEnabled.value = true
            }
            GlassPreset.AURORA_GLOW -> {
                _viscosity.value = 0.99f // hyper floaty
                _cohesion.value = 0.08f
                _gravityScale.value = 0.05f // near weightless
                _bodyColor.value = Color(0x80E040FB) // glowing rose-pink
                _rimColor.value = Color(0xBBA1FF00) // sharp chartreuse rim
                _highlightColor.value = Color(0xFF00E5FF) // neon cyan specula
                _fusingEnabled.value = false // they stay separate and bubble
            }
            GlassPreset.MAGMA_FLOW -> {
                _viscosity.value = 0.91f // thick sluggish
                _cohesion.value = 0.28f // viscous merge
                _gravityScale.value = 0.75f // heavy gravity pull
                _bodyColor.value = Color(0xFFD84315) // dark glowing red
                _rimColor.value = Color(0xFFFFB300) // radiant golden yellow rim
                _highlightColor.value = Color(0xFFFFEB3B)
                _fusingEnabled.value = true
            }
            GlassPreset.EMERALD_DEW -> {
                _viscosity.value = 0.96f
                _cohesion.value = 0.16f
                _gravityScale.value = 0.45f
                _bodyColor.value = Color(0x6500E676) // translucent green
                _rimColor.value = Color(0xCCD7FFD7)
                _highlightColor.value = Color.White
                _fusingEnabled.value = true
            }
            GlassPreset.CUSTOM -> {}
        }
        reinitPresetParticles()
    }

    private fun reinitPresetParticles() {
        // Clear old ones
        nextParticleId = 0
        val count = when (_selectedPreset.value) {
            GlassPreset.LIQUID_MERCURY -> 30 // Heavy blobs look better larger and fewer
            GlassPreset.AURORA_GLOW -> 65 // Floaties look amazing in mass
            GlassPreset.MAGMA_FLOW -> 25 // Giant slow blobs
            else -> 42
        }

        val list = mutableListOf<FluidParticle>()
        for (i in 0 until count) {
            val baseR = when (_selectedPreset.value) {
                GlassPreset.LIQUID_MERCURY -> 55f + Math.random().toFloat() * 30f
                GlassPreset.MAGMA_FLOW -> 60f + Math.random().toFloat() * 35f
                GlassPreset.AURORA_GLOW -> 25f + Math.random().toFloat() * 15f
                else -> 35f + Math.random().toFloat() * 25f
            }
            val rx = baseR + Math.random().toFloat() * (canvasWidth - baseR * 2)
            val ry = baseR + Math.random().toFloat() * (canvasHeight - baseR * 2)

            list.add(
                FluidParticle(
                    id = ++nextParticleId,
                    x = rx,
                    y = ry,
                    vx = (Math.random().toFloat() - 0.5f) * 8f,
                    vy = (Math.random().toFloat() - 0.5f) * 8f,
                    radius = baseR
                )
            )
        }
        _particles.value = list
    }

    // Direct manual setters to support customizing custom preset
    fun updateViscosity(value: Float) {
        _viscosity.value = value
        _selectedPreset.value = GlassPreset.CUSTOM
    }

    fun updateCohesion(value: Float) {
        _cohesion.value = value
        _selectedPreset.value = GlassPreset.CUSTOM
    }

    fun updateGravityScale(value: Float) {
        _gravityScale.value = value
        _selectedPreset.value = GlassPreset.CUSTOM
    }

    fun updateLightAngle(value: Float) {
        _lightAngle.value = value
    }

    fun updateGravityDirection(gx: Float, gy: Float) {
        _gravityX.value = gx
        _gravityY.value = gy
    }

    fun updateInteractionMode(mode: InteractionMode) {
        _interactionMode.value = mode
    }

    fun updateFusingEnabled(enabled: Boolean) {
        _fusingEnabled.value = enabled
        _selectedPreset.value = GlassPreset.CUSTOM
    }

    fun updateCustomColors(body: Color, rim: Color, highlight: Color) {
        _bodyColor.value = body
        _rimColor.value = rim
        _highlightColor.value = highlight
        _selectedPreset.value = GlassPreset.CUSTOM
    }
}
