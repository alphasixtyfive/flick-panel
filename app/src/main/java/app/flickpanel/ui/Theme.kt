package app.flickpanel.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.flickpanel.config.PanelTheme

private val Slate = darkColorScheme(
    primary = Color(0xffb2d5f5), onPrimary = Color(0xff17334c),
    primaryContainer = Color(0xff2c4560), onPrimaryContainer = Color(0xffdfedff),
    secondary = Color(0xffb8cbdf), secondaryContainer = Color(0xff304255),
    onSecondaryContainer = Color(0xffe0eaf4), background = Color(0xff171e27),
    onBackground = Color(0xffedf3fb), surface = Color(0xff171e27), onSurface = Color(0xffedf3fb),
    surfaceContainerLow = Color(0xff1c2530), surfaceContainer = Color(0xff222d3a),
    surfaceContainerHigh = Color(0xff283543), surfaceContainerHighest = Color(0xff324152),
    surfaceVariant = Color(0xff344251), onSurfaceVariant = Color(0xffbdc9d7),
    outline = Color(0xff8796a8), outlineVariant = Color(0xff3a4858)
)

private val Amber = darkColorScheme(
    primary = Color(0xfff2c78b), onPrimary = Color(0xff39270e),
    primaryContainer = Color(0xff513d27), onPrimaryContainer = Color(0xfffae5c6),
    secondary = Color(0xffddcab0), secondaryContainer = Color(0xff483c30),
    onSecondaryContainer = Color(0xfff6ebdd), background = Color(0xff1b1918),
    onBackground = Color(0xfff5eee8), surface = Color(0xff1b1918), onSurface = Color(0xfff5eee8),
    surfaceContainerLow = Color(0xff241f1b), surfaceContainer = Color(0xff2b2622),
    surfaceContainerHigh = Color(0xff332c26), surfaceContainerHighest = Color(0xff40362f),
    surfaceVariant = Color(0xff41372f), onSurfaceVariant = Color(0xffcfc1b3),
    outline = Color(0xff9b8c7f), outlineVariant = Color(0xff4b4138)
)

private val Porcelain = lightColorScheme(
    primary = Color(0xff315b83), onPrimary = Color.White,
    primaryContainer = Color(0xffdbeaff), onPrimaryContainer = Color(0xff153b5f),
    secondary = Color(0xff4d6278), secondaryContainer = Color(0xffdde7f0),
    onSecondaryContainer = Color(0xff22384d), background = Color(0xfff6f6f3),
    onBackground = Color(0xff202832), surface = Color(0xfff6f6f3), onSurface = Color(0xff202832),
    surfaceContainerLow = Color(0xfffaf9f6), surfaceContainer = Color(0xffebedf0),
    surfaceContainerHigh = Color(0xffe4e8ec), surfaceContainerHighest = Color(0xffdce2ea),
    surfaceVariant = Color(0xffdfe5ed), onSurfaceVariant = Color(0xff4b5765),
    outline = Color(0xff727e8e), outlineVariant = Color(0xffc5cdd8)
)

@Composable
internal fun PanelColors(theme: PanelTheme, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        theme == PanelTheme.SLATE -> Slate
        theme == PanelTheme.AMBER -> Amber
        theme == PanelTheme.PORCELAIN -> Porcelain
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Slate
        else -> Porcelain
    }
    MaterialTheme(colorScheme = colors, content = content)
}
