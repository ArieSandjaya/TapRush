package com.pocil.taprush

import androidx.compose.animation.core.withFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.random.Random

// ─── Game State ──────────────────────────────────────────────────────────────

class GameState {
    companion object {
        const val SEGMENTS = 7
        private const val SPEED_INIT = 0.45f          // normalised units / sec
        private const val INTERVAL_INIT = 1500L        // ms between spawns
        private const val SPEED_STEP = 0.04f
        private const val INTERVAL_STEP = 50L
        private const val INTERVAL_MIN = 300L
        private const val SPEED_MAX = 2.0f
    }

    data class Ball(val column: Int, var progress: Float) // 0..1

    // observable state (read by composable)
    var gap by mutableStateOf(SEGMENTS / 2)
    var score by mutableStateOf(0)
    var highScore by mutableStateOf(0)
    var alive by mutableStateOf(true)
    var playing by mutableStateOf(false)

    // internal
    private val _balls = mutableListOf<Ball>()
    private var speed = SPEED_INIT
    private var interval = INTERVAL_INIT
    private var lastSpawn = 0L
    private var lastFrame = 0L
    private var streak = 0

    fun balls() = _balls.toList()

    fun start() {
        gap = SEGMENTS / 2
        score = 0
        alive = true
        playing = true
        _balls.clear()
        speed = SPEED_INIT
        interval = INTERVAL_INIT
        streak = 0
        lastFrame = 0L
        lastSpawn = 0L
    }

    fun moveLeft()  { if (playing && alive && gap > 0) gap-- }
    fun moveRight() { if (playing && alive && gap < SEGMENTS - 1) gap++ }

    fun tick(ms: Long) {
        if (!playing || !alive) return
        if (lastFrame == 0L) { lastFrame = ms; lastSpawn = ms; return }

        val dt = (ms - lastFrame) / 1000f
        lastFrame = ms

        // spawn
        if (ms - lastSpawn >= interval) {
            _balls.add(Ball(Random.nextInt(SEGMENTS), 0f))
            lastSpawn = ms
        }

        // move
        val it = _balls.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.progress += speed * dt
            if (b.progress >= 1f) {
                it.remove()
                if (b.column == gap) {
                    alive = false
                    if (score > highScore) highScore = score
                    return
                }
                score++
                streak++
                // difficulty ramp every 5
                if (streak % 5 == 0) {
                    speed = minOf(speed + SPEED_STEP, SPEED_MAX)
                    interval = maxOf(interval - INTERVAL_STEP, INTERVAL_MIN)
                }
            }
        }
    }
}

// ─── Composable ──────────────────────────────────────────────────────────────

@Composable
fun TapRushGame() {
    val g = remember { GameState() }

    // android Paint for text-on-canvas (one allocation, reused)
    val paint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    // game loop ticks every frame
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { g.tick(it) }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    if (!g.playing || !g.alive) {
                        g.start()
                    } else if (down.position.x < w / 2) {
                        g.moveLeft()
                    } else {
                        g.moveRight()
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val segW = w / GameState.SEGMENTS
        val platH = h * 0.12f          // platform height
        val platY = h - platH          // platform top
        val ballR = segW * 0.35f
        val can = drawContext.canvas.nativeCanvas

        // ── background ──
        drawRect(Color(0xFF0f0f1a), size = size)

        // ── platform segments ──
        for (i in 0 until GameState.SEGMENTS) {
            val left = i * segW + 1f
            val rect = Size(segW - 2f, platH - 2f)
            val pos = Offset(left, platY + 1f)
            if (i == g.gap) {
                // gap – dark hole with red edge
                drawRect(Color(0xFF1a1a2e), pos, rect)
                drawRect(Color(0xFFe74c3c), pos, rect, style = Stroke(3f))
            } else {
                drawRect(Color(0xFF6c63ff), pos, rect)
            }
        }

        // ── score ──
        paint.textSize = 52f * density
        can.drawText("${g.score}", w / 2, 80f * density, paint)

        paint.textSize = 22f * density
        paint.color = android.graphics.Color.argb(140, 255, 255, 255)
        can.drawText("Best: ${g.highScore}", w / 2, 112f * density, paint)
        paint.color = android.graphics.Color.WHITE

        // ── balls ──
        for (b in g.balls()) {
            val cx = b.column * segW + segW / 2
            val cy = platY * b.progress
            // outer glow
            drawCircle(Color(0xFFf39c12).copy(alpha = 0.2f), ballR * 2.2f, Offset(cx, cy))
            // ball body
            drawCircle(Color(0xFFf1c40f), ballR, Offset(cx, cy))
            // highlight
            drawCircle(Color(0xFFfce4a8), ballR * 0.5f, Offset(cx - ballR * 0.2f, cy - ballR * 0.2f))
        }

        // ── overlays ──
        if (!g.playing) {
            paint.textSize = 58f * density
            paint.color = android.graphics.Color.WHITE
            can.drawText("Tap Rush", w / 2, h * 0.35f, paint)

            paint.textSize = 26f * density
            can.drawText("Tap anywhere to start", w / 2, h * 0.44f, paint)

            paint.textSize = 18f * density
            paint.color = android.graphics.Color.argb(160, 255, 255, 255)
            can.drawText("←  Tap left  ·  right  →", w / 2, h * 0.53f, paint)
            can.drawText("Move the gap away from the falling balls!", w / 2, h * 0.58f, paint)
        }

        if (!g.alive) {
            // semi-transparent overlay
            drawRect(Color(0x66000000), size = size)

            paint.textSize = 56f * density
            paint.color = android.graphics.Color.rgb(231, 76, 60)
            can.drawText("Game Over", w / 2, h * 0.33f, paint)

            paint.textSize = 42f * density
            paint.color = android.graphics.Color.WHITE
            can.drawText("Score: ${g.score}", w / 2, h * 0.43f, paint)

            paint.textSize = 22f * density
            paint.color = android.graphics.Color.argb(180, 255, 255, 255)
            can.drawText("High Score: ${g.highScore}", w / 2, h * 0.50f, paint)

            paint.textSize = 26f * density
            paint.color = android.graphics.Color.WHITE
            can.drawText("Tap anywhere to retry", w / 2, h * 0.62f, paint)
        }
    }
}
