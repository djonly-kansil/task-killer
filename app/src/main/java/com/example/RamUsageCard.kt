package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GeometricAllow
import com.example.ui.theme.GeometricDeny

private val CacheColor = GeometricAllow
private val SystemColor = Color(0xFFF5C518)
private val FreeColor = Color(0xFF3B82F6)
private val UserAppsColor = GeometricDeny

/**
 * Kartu pemakaian RAM: cincin progres + angka, persentase, kurva riwayat (sparkline),
 * bar bersegmen dan legenda rincian memori.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RamUsageCard(
    state: AppManagerState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    compact: Boolean = false
) {
    val s = LocalStrings.current
    val total = state.totalRamGb
    val ratio = if (total > 0f) (state.usedRamGb / total).coerceIn(0f, 1f) else 0f

    val compactContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight().heightIn(min = 56.dp).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    strokeWidth = 3.dp,
                    strokeCap = StrokeCap.Round
                )
                Text(
                    "${(ratio * 100).toInt()}%",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    s.memoryShort,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        String.format("%.1f GB", state.usedRamGb),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        String.format("/ %.1f GB", total),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Text(
                    String.format("%s %.1f GB", s.ramAvailable, state.ramFreeGb),
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    val fullContent: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Kiri: cincin + angka
                Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        strokeWidth = 4.dp,
                        strokeCap = StrokeCap.Round
                    )
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        s.memoryShort,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            String.format("%.1f GB", state.usedRamGb),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            String.format("/ %.1f GB", total),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp),
                            maxLines = 1
                        )
                    }
                    Row {
                        Text(s.ramUsedLabel, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(s.ramTotalLabel, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Tengah: persentase
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        "${(ratio * 100).toInt()}%",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(s.ramInUseLabel, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Kanan: kurva riwayat
                RamSparkline(
                    history = state.ramHistory,
                    lineColor = MaterialTheme.colorScheme.primary,
                    guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.width(84.dp).height(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            SegmentedRamBar(
                userApps = state.ramUserAppsGb,
                cache = state.ramCacheGb,
                system = state.ramSystemGb,
                free = state.ramFreeGb
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                RamLegendItem(UserAppsColor, s.ramUserAppsLabel, state.ramUserAppsGb, total, Modifier.weight(1f))
                RamLegendItem(CacheColor, s.ramCacheLabel, state.ramCacheGb, total, Modifier.weight(1f))
                RamLegendItem(SystemColor, s.ramSystemLabel, state.ramSystemGb, total, Modifier.weight(1f))
                RamLegendItem(FreeColor, s.ramFreeLabel, state.ramFreeGb, total, Modifier.weight(1f))
            }
        }
    }

    val content: @Composable () -> Unit = if (compact) compactContent else fullContent
    val shape = RoundedCornerShape(if (compact) 18.dp else 24.dp)

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = shape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) { content() }
    } else {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = shape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) { content() }
    }
}

@Composable
private fun RamSparkline(
    history: List<Float>,
    lineColor: Color,
    guideColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
        listOf(0f, 0.5f, 1f).forEach { g ->
            val y = h - (g * h)
            drawLine(
                color = guideColor,
                start = Offset(0f, y.coerceIn(0.5f, h - 0.5f)),
                end = Offset(w, y.coerceIn(0.5f, h - 0.5f)),
                strokeWidth = 1f,
                pathEffect = dash
            )
        }

        val points = if (history.size < 2) List(2) { history.lastOrNull() ?: 0f } else history
        val stepX = w / (points.size - 1).toFloat()
        fun px(i: Int) = i * stepX
        fun py(v: Float) = h - (v.coerceIn(0f, 1f) * (h - 4f)) - 2f

        val line = Path().apply {
            moveTo(px(0), py(points[0]))
            for (i in 1 until points.size) {
                val prevX = px(i - 1)
                val curX = px(i)
                val midX = (prevX + curX) / 2f
                cubicTo(midX, py(points[i - 1]), midX, py(points[i]), curX, py(points[i]))
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(px(points.size - 1), h)
            lineTo(0f, h)
            close()
        }

        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.45f), lineColor.copy(alpha = 0.02f))
            )
        )
        drawPath(path = line, color = lineColor, style = Stroke(width = 2f, cap = StrokeCap.Round))
        drawCircle(
            color = lineColor,
            radius = 3f,
            center = Offset(px(points.size - 1), py(points.last()))
        )
    }
}

@Composable
private fun SegmentedRamBar(userApps: Float, cache: Float, system: Float, free: Float) {
    val sum = (userApps + cache + system + free).takeIf { it > 0f } ?: 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf(
            userApps to UserAppsColor,
            cache to CacheColor,
            system to SystemColor,
            free to FreeColor
        ).forEach { (value, color) ->
            val weight = (value / sum).coerceAtLeast(0.001f)
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun RamLegendItem(
    color: Color,
    label: String,
    valueGb: Float,
    totalGb: Float,
    modifier: Modifier = Modifier
) {
    val percent = if (totalGb > 0f) (valueGb / totalGb * 100f).toInt() else 0
    Column(modifier = modifier.padding(end = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                label,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                String.format("%.1f", valueGb),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text("GB", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
        }
        Text("$percent%", fontSize = 9.sp, color = color)
    }
}
