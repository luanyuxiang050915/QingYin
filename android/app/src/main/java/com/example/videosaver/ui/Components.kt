package com.example.videosaver.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.videosaver.ui.theme.BrandGradient
import com.example.videosaver.ui.theme.BrandPurple

// ------------------------------------------------------------------
// 封面（Coil 加载 + 淡入）
// ------------------------------------------------------------------

@Composable
fun CoverImage(url: String, modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    val context = LocalContext.current
    Box(modifier.clip(RoundedCornerShape(corner)).background(Color(0x22_3A3F55))) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = "封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

// ------------------------------------------------------------------
// 圆环进度
// ------------------------------------------------------------------

@Composable
fun RingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 5.dp,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "ringProgress")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2f
            val arcSize = androidx.compose.ui.geometry.Size(
                this.size.width - strokePx,
                this.size.height - strokePx,
            )
            drawArc(
                color = Color(0x33_4A7CFF),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(strokePx, cap = StrokeCap.Round),
            )
            if (animated > 0.001f) {
                drawArc(
                    brush = BrandGradient,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(strokePx, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = "${(progress * 100).toInt()}%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ------------------------------------------------------------------
// 骨架屏闪烁（解析中）
// ------------------------------------------------------------------

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, corner: Dp = 12.dp) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable<Float>(tween(1300, easing = LinearEasing)),
        label = "shimmerShift",
    )
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .drawBehind {
                drawRect(Color(0x22_3A3F55))
                val brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color(0x40_FFFFFF), Color.Transparent),
                    start = androidx.compose.ui.geometry.Offset(shift, 0f),
                    end = androidx.compose.ui.geometry.Offset(shift + 400f, size.height),
                )
                drawRect(brush)
            }
    )
}

@Composable
fun SkeletonCard() {
    Column(Modifier.fillMaxWidth().padding(4.dp)) {
        Row {
            ShimmerBox(Modifier.size(120.dp, 76.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                ShimmerBox(Modifier.fillMaxWidth(0.9f).height(16.dp))
                Spacer(Modifier.height(10.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.6f).height(13.dp))
                Spacer(Modifier.height(10.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.4f).height(13.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        ShimmerBox(Modifier.fillMaxWidth().height(46.dp), corner = 14.dp)
    }
}

// ------------------------------------------------------------------
// 平台徽章 / 胶囊
// ------------------------------------------------------------------

@Composable
fun PlatformBadge(platform: String, modifier: Modifier = Modifier) {
    val (color, label) = when (platform) {
        "B站" -> Color(0xFF00A1D6) to "B站"
        "快手" -> Color(0xFFFF4D2E) to "快手"
        "X(推特)" -> Color(0xFF1D1D1F) to "X"
        "小红书" -> Color(0xFFFF2442) to "小红书"
        "微博" -> Color(0xFFE6162D) to "微博"
        "通用" -> BrandPurple to "通用"
        else -> Color(0xFF8A90A5) to platform
    }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, tint: Color = Color(0xFF8A90A5)) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ------------------------------------------------------------------
// 渐变主按钮
// ------------------------------------------------------------------

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 46.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered && enabled) 1.02f else 1f, label = "btnScale")
    val backgroundBrush: Brush = if (enabled) BrandGradient else Brush.linearGradient(listOf(Color(0x33_8A90A5)))
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundBrush)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) { onClick() }
            .height(height)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = Color.White)
                Spacer(Modifier.width(7.dp))
            }
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

// ------------------------------------------------------------------
// 成功打勾
// ------------------------------------------------------------------

@Composable
fun SuccessCheck(modifier: Modifier = Modifier, size: Dp = 26.dp) {
    val scale by animateFloatAsState(1f, label = "checkScale")
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val r = w / 2f
            drawCircle(color = Color(0xFF2ECC71), radius = r)
            val path = Path().apply {
                moveTo(w * 0.26f, h * 0.52f)
                lineTo(w * 0.45f, h * 0.7f)
                lineTo(w * 0.76f, h * 0.3f)
            }
            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(width = h * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

// ------------------------------------------------------------------
// 空状态
// ------------------------------------------------------------------

@Composable
fun EmptyState(
    icon: ImageVector = Icons.Outlined.Download,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(52.dp), tint = Color(0x55_8A90A5))
        Spacer(Modifier.height(10.dp))
        Text(title, color = Color(0x99_8A90A5), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color(0x66_8A90A5), fontSize = 12.sp)
        }
    }
}

// ------------------------------------------------------------------
// 格式化
// ------------------------------------------------------------------

fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec >= 1024 * 1024 -> "%.1f MB/s".format(bytesPerSec / 1024f / 1024f)
    bytesPerSec >= 1024 -> "%.0f KB/s".format(bytesPerSec / 1024f)
    else -> "${bytesPerSec} B/s"
}

fun formatEta(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "剩余 %d:%02d:%02d".format(h, m, s)
        else -> "剩余 %02d:%02d".format(m, s)
    }
}
