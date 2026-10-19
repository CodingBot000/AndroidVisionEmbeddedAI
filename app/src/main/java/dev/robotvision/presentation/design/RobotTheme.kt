package dev.robotvision.presentation.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val Background = Color(0xFF080E18)
val Panel = Color(0xFF121C2A)
val Elevated = Color(0xFF1A2839)
val Cyan = Color(0xFF32D4D8)
val Mint = Color(0xFF64DF9B)
val Amber = Color(0xFFFFC66E)
val Rose = Color(0xFFFF7B85)
val Ink = Color(0xFFF1F6FA)
val Muted = Color(0xFF9DB0C4)
@Composable fun RobotTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, onPrimary = Background,
        background = Background, onBackground = Ink, surface = Panel, onSurface = Ink,
        surfaceVariant = Elevated, onSurfaceVariant = Muted, error = Rose),
        shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp)), content = content)
}
@Composable fun Eyebrow(text: String, color: Color = Muted) {
    Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
}
@Composable fun StatusBadge(text: String, color: Color = Cyan) {
    Surface(color = color.copy(alpha = .10f), shape = RoundedCornerShape(6.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = color,
            style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
    }
}
@Composable fun ConsoleCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Panel) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        subtitle?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodyMedium) }
    }
}
@Composable fun SensorGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val center = Offset(size.width/2, size.height/2)
        val radius = size.minDimension * .43f
        for (i in 1..4) drawCircle(Cyan.copy(alpha=.08f + i*.02f), radius*i/4, center, style=Stroke(1.dp.toPx()))
        drawLine(Cyan.copy(alpha=.25f), Offset(center.x-radius,center.y), Offset(center.x+radius,center.y))
        drawLine(Cyan.copy(alpha=.25f), Offset(center.x,center.y-radius), Offset(center.x,center.y+radius))
        drawArc(Cyan, -68f, 100f, false, topLeft=Offset(center.x-radius*.72f,center.y-radius*.72f),
            size=androidx.compose.ui.geometry.Size(radius*1.44f,radius*1.44f), style=Stroke(2.dp.toPx()))
        drawCircle(Cyan, 5.dp.toPx(), center)
        drawCircle(Cyan.copy(alpha=.4f), 2.dp.toPx(), center + Offset(radius*.45f, -radius*.25f))
    }
}
