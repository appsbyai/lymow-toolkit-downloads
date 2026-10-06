package com.lymow.toolkit.companion.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lymow.toolkit.companion.data.ApiException
import com.lymow.toolkit.companion.data.MapFeature
import com.lymow.toolkit.companion.data.ToolkitApi
import kotlinx.coroutines.launch

/**
 * Native live map: zones, no-go areas, dock and the mower itself, drawn from
 * /api/geojson — no web view involved. Pinch to zoom, drag to pan.
 */
@Composable
fun MapScreen(api: ToolkitApi, onAuthRequired: () -> Unit) {
    var features by remember { mutableStateOf<List<MapFeature>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            features = api.mapFeatures()
            error = null
        } catch (e: ApiException) {
            if (e.code == 401) onAuthRequired() else error = e.message
        } catch (e: Exception) {
            error = "Could not load the map."
        }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Map", style = MaterialTheme.typography.headlineSmall)
            IconButton(
                onClick = { scope.launch { loading = true; load() } },
                enabled = !loading,
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading map…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
            }
            features.isNullOrEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No map yet — map your zones in the Toolkit first.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                Card(Modifier.weight(1f).fillMaxWidth()) {
                    MapCanvas(
                        features = features!!,
                        scale = scale,
                        offset = offset,
                        onTransform = { pan, zoom ->
                            scale = (scale * zoom).coerceIn(0.3f, 20f)
                            offset += pan
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(Color(0xFF4CAF50), "Zone")
                    LegendDot(Color(0xFFF44336), "No-go")
                    LegendDot(Color(0xFF2196F3), "Mower")
                    LegendDot(Color(0xFF9E9E9E), "Dock")
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Spacer(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MapCanvas(
    features: List<MapFeature>,
    scale: Float,
    offset: Offset,
    onTransform: (pan: Offset, zoom: Float) -> Unit,
) {
    val zoneFill = Color(0x554CAF50)
    val zoneStroke = Color(0xFF388E3C)
    val noGoFill = Color(0x55F44336)
    val noGoStroke = Color(0xFFC62828)
    val robotColor = Color(0xFF2196F3)
    val dockColor = Color(0xFF9E9E9E)
    val rtkColor = Color(0xFFFF9800)

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ -> onTransform(pan, zoom) }
            },
    ) {
        // Bounds over every point we have.
        val allPoints = features.flatMap { f -> f.polygons.flatten() + listOfNotNull(f.point) }
        if (allPoints.isEmpty()) return@Canvas
        val minLat = allPoints.minOf { it.first }
        val maxLat = allPoints.maxOf { it.first }
        val minLng = allPoints.minOf { it.second }
        val maxLng = allPoints.maxOf { it.second }
        val latSpan = (maxLat - minLat).coerceAtLeast(1e-6)
        val lngSpan = (maxLng - minLng).coerceAtLeast(1e-6)

        val pad = 32f
        val fit = minOf(
            (size.width - 2 * pad) / lngSpan.toFloat(),
            (size.height - 2 * pad) / latSpan.toFloat(),
        )
        val centerLat = (minLat + maxLat) / 2
        val centerLng = (minLng + maxLng) / 2

        fun project(lat: Double, lng: Double): Offset {
            val x = ((lng - centerLng) * fit * scale).toFloat() + size.width / 2f + offset.x
            val y = ((centerLat - lat) * fit * scale).toFloat() + size.height / 2f + offset.y
            return Offset(x, y)
        }

        // Polygons first (zones, no-go areas).
        for (f in features) {
            val isNoGo = f.kind.contains("no", ignoreCase = true) &&
                f.kind.contains("go", ignoreCase = true)
            val fill = if (isNoGo) noGoFill else zoneFill
            val stroke = if (isNoGo) noGoStroke else zoneStroke
            for (ring in f.polygons) {
                if (ring.size < 3) continue
                val path = Path().apply {
                    val first = project(ring.first().first, ring.first().second)
                    moveTo(first.x, first.y)
                    for (p in ring.drop(1)) {
                        val o = project(p.first, p.second)
                        lineTo(o.x, o.y)
                    }
                    close()
                }
                drawPath(path, fill, style = Fill)
                drawPath(path, stroke, style = Stroke(width = 2f))
            }
        }

        // Point features on top.
        for (f in features) {
            val pt = f.point ?: continue
            val o = project(pt.first, pt.second)
            when (f.kind) {
                "robot" -> {
                    drawCircle(Color.White, radius = 12f, center = o)
                    drawCircle(robotColor, radius = 9f, center = o)
                }
                "dock" -> {
                    drawCircle(dockColor, radius = 8f, center = o)
                    drawCircle(Color.White, radius = 3f, center = o)
                }
                "rtk_base" -> drawCircle(rtkColor, radius = 6f, center = o)
                else -> drawCircle(dockColor, radius = 5f, center = o)
            }
        }
    }
}
