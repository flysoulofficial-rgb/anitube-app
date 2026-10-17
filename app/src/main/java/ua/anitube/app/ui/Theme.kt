package ua.anitube.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object C {
    val Bg = Color(0xFF0E1015)
    val Surface = Color(0xFF171A22)
    val Surface2 = Color(0xFF212530)
    val Accent = Color(0xFFFF5C7A)
    val AccentDim = Color(0x33FF5C7A)
    val Text = Color(0xFFF2F4F8)
    val Text2 = Color(0xFFA9B0C0)
    val Green = Color(0xFF4CD08B)
    val Amber = Color(0xFFFFB84D)
}

@Composable
fun AniTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent, onPrimary = Color.White,
            background = C.Bg, onBackground = C.Text,
            surface = C.Surface, onSurface = C.Text,
            surfaceVariant = C.Surface2, onSurfaceVariant = C.Text2,
            secondaryContainer = C.AccentDim, onSecondaryContainer = C.Text,
            surfaceContainer = C.Surface, surfaceContainerHigh = C.Surface2
        ),
        content = content
    )
}

// ---- Прості намальовані значки (щоб не тягти великий набір іконок) ----

@Composable
fun PauseGlyph(size: Dp = 28.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        drawRect(color, Offset(w * 0.2f, h * 0.15f), Size(w * 0.2f, h * 0.7f))
        drawRect(color, Offset(w * 0.6f, h * 0.15f), Size(w * 0.2f, h * 0.7f))
    }
}

@Composable
fun PlayGlyph(size: Dp = 28.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val p = Path().apply { moveTo(w * 0.25f, h * 0.12f); lineTo(w * 0.88f, h * 0.5f); lineTo(w * 0.25f, h * 0.88f); close() }
        drawPath(p, color)
    }
}

@Composable
fun SkipGlyph(next: Boolean, size: Dp = 26.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val p = Path()
        if (next) {
            p.moveTo(w * 0.15f, h * 0.15f); p.lineTo(w * 0.7f, h * 0.5f); p.lineTo(w * 0.15f, h * 0.85f); p.close()
            drawPath(p, color)
            drawRect(color, Offset(w * 0.76f, h * 0.15f), Size(w * 0.1f, h * 0.7f))
        } else {
            p.moveTo(w * 0.85f, h * 0.15f); p.lineTo(w * 0.3f, h * 0.5f); p.lineTo(w * 0.85f, h * 0.85f); p.close()
            drawPath(p, color)
            drawRect(color, Offset(w * 0.14f, h * 0.15f), Size(w * 0.1f, h * 0.7f))
        }
    }
}

@Composable
fun HistoryGlyph(size: Dp = 24.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = w * 0.1f
        drawCircle(color, w * 0.38f, Offset(w / 2, h / 2), style = Stroke(sw))
        drawLine(color, Offset(w / 2, h / 2), Offset(w / 2, h * 0.28f), sw, StrokeCap.Round)
        drawLine(color, Offset(w / 2, h / 2), Offset(w * 0.68f, h * 0.58f), sw, StrokeCap.Round)
    }
}

@Composable
fun GridGlyph(size: Dp = 24.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val s = Size(w * 0.34f, h * 0.34f)
        for (x in 0..1) for (y in 0..1)
            drawRect(color, Offset(w * (0.12f + x * 0.42f), h * (0.12f + y * 0.42f)), s)
    }
}

@Composable
fun SeekGlyph(forward: Boolean, size: Dp = 30.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = w * 0.09f
        val start = if (forward) -50f else 230f
        drawArc(color, start, if (forward) 280f else -280f, false, Offset(w * 0.15f, h * 0.2f), Size(w * 0.7f, h * 0.7f), style = Stroke(sw, cap = StrokeCap.Round))
        val ax = if (forward) w * 0.62f else w * 0.38f
        val p = Path().apply {
            moveTo(ax, h * 0.02f)
            lineTo(ax + (if (forward) w * 0.18f else -w * 0.18f), h * 0.2f)
            lineTo(ax, h * 0.38f)
            close()
        }
        drawPath(p, color)
    }
}
