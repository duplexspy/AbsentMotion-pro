package app.absentmotion.pro

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.absentmotion.ui.home.HomeIcons

object ProDesignTokens {
    val Surface0 = Color(0xFF0E1015)
    val Surface1 = Color(0xFF161820)
    val Surface2 = Color(0xFF1F222C)
    val Surface3 = Color(0xFF2A2E3B)

    val TextPrimary = Color(0xFFF6F7FA)
    val TextSecondary = Color(0xFFA6ADB9)
    val TextTertiary = Color(0xFF6F7788)

    val AccentPrimary = Color(0xFFFF6A3D)
    val AccentLit = Color(0xFFFF8E6B)
    val AccentGlow = Color(0xFFFFA688)
    val AccentDeep = Color(0xFF4D1B0A)
    val OnAccent = Color(0xFF0E1015)

    val BorderSubtle = Color(0x1AFFFFFF)

    val Space2: Dp = 2.dp
    val Space4: Dp = 4.dp
    val Space8: Dp = 8.dp
    val Space12: Dp = 12.dp
    val Space16: Dp = 16.dp
    val Space20: Dp = 20.dp
    val Space24: Dp = 24.dp
    val Space32: Dp = 32.dp
    val Space48: Dp = 48.dp

    val TouchTargetMin: Dp = 48.dp

    val RadiusXs: Dp = 4.dp
    val RadiusSmall: Dp = 8.dp
    val RadiusMedium: Dp = 12.dp
    val RadiusLarge: Dp = 16.dp
    val RadiusPill: Dp = 999.dp

    const val DurationFast: Int = 150
    const val DurationStandard: Int = 200
    const val DurationSlow: Int = 250

    fun relativeLuminance(color: Color): Float {
        fun channel(v: Float): Float {
            return if (v <= 0.04045f) {
                v / 12.92f
            } else {
                Math.pow(((v + 0.055) / 1.055), 2.4).toFloat()
            }
        }
        return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
    }

    fun contrastRatio(c1: Color, c2: Color): Float {
        val l1 = relativeLuminance(c1)
        val l2 = relativeLuminance(c2)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}

@Composable
fun BottomSheetDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(ProDesignTokens.RadiusPill))
                .background(Color.White.copy(alpha = 0.35f))
        )
    }
}

@Composable
fun ShimmerSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-pos",
    )

    val colors = listOf(
        ProDesignTokens.Surface1,
        ProDesignTokens.Surface2,
        ProDesignTokens.Surface1,
    )

    val brush = Brush.linearGradient(
        colors = colors,
        start = androidx.compose.ui.geometry.Offset(translateAnim - 500f, 0f),
        end = androidx.compose.ui.geometry.Offset(translateAnim, 0f),
    )

    Box(modifier = modifier.clip(shape).background(brush))
}

@Composable
fun PremiumEmptyState(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    arrow: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 160.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(ProDesignTokens.Surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = ProDesignTokens.AccentPrimary,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
            Text(
                text = title,
                color = ProDesignTokens.TextPrimary,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    color = ProDesignTokens.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (arrow) {
            Icon(
                imageVector = HomeIcons.ArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .size(48.dp),
                tint = ProDesignTokens.TextTertiary,
            )
        }
    }
}
