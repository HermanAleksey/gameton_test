package com.gameton.app.ui.theme

import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.gameton.app.ui.model.IconStyle
import com.gameton.app.ui.model.RiskSeverity

private val AppColors = darkColors(
    primary = Color(0xFF41C69B),
    primaryVariant = Color(0xFF2C8A6D),
    secondary = Color(0xFFFFC857),
    background = Color(0xFF1A1E1F),
    surface = Color(0xFF22282A),
    error = Color(0xFFE76F51),
    onPrimary = Color(0xFF08110D),
    onSecondary = Color(0xFF231A03),
    onBackground = Color(0xFFE9E2D0),
    onSurface = Color(0xFFE9E2D0),
    onError = Color.White
)

object DashboardPalette {
    val Desert = Color(0xFFB88E63)
    val DesertSoft = Color(0xFFD4B18A)
    val Oasis = Color(0xFF4DB58A)
    val Mountain = Color(0xFF677077)
    val Grid = Color(0x33FFF1D3)
    val Boosted = Color(0xFFE2C044)
    val Own = Color(0xFF2DBE8C)
    val Main = Color(0xFF63E1B8)
    val Enemy = Color(0xFFF07F5A)
    val Construction = Color(0xFF7EC8E3)
    val Beaver = Color(0xFFC48A54)
    val Sandstorm = Color(0xFFE7B25B)
    val Earthquake = Color(0xFFE85D5D)
    val Panel = Color(0xFF22282A)
    val PanelAlt = Color(0xFF2A3134)
    val TextMuted = Color(0xFFA9B2A3)
}

fun severityColor(severity: RiskSeverity): Color = when (severity) {
    RiskSeverity.None -> Color.Transparent
    RiskSeverity.Warning -> Color(0xFFF3C969)
    RiskSeverity.High -> Color(0xFFF08C4A)
    RiskSeverity.Critical -> Color(0xFFE35D5D)
}

fun iconStyle(fill: Color, stroke: Color = Color.Transparent, badge: Color? = null): IconStyle =
    IconStyle(fill = fill, stroke = stroke, badge = badge)

@Composable
fun DashboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = AppColors,
        content = content
    )
}

val Colors.panelAlt: Color
    get() = DashboardPalette.PanelAlt
