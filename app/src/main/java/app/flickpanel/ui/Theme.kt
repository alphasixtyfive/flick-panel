package app.flickpanel.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flickpanel.R
import app.flickpanel.config.PanelTheme

internal val panelMaxWidth = 1240.dp
internal val panelWidePadding = 36.dp

@OptIn(ExperimentalTextApi::class)
private val PanelFont = FontFamily(
    Font(R.font.google_sans_flex, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.google_sans_flex, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.google_sans_flex, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

private val PanelType = Typography().run {
    copy(
        displayMedium = displayMedium.copy(fontFamily = PanelFont, fontSize = 48.sp, lineHeight = 54.sp),
        displaySmall = displaySmall.copy(fontFamily = PanelFont, fontSize = 38.sp, lineHeight = 43.sp),
        headlineMedium = headlineMedium.copy(fontFamily = PanelFont, fontSize = 31.sp, lineHeight = 36.sp),
        headlineSmall = headlineSmall.copy(fontFamily = PanelFont),
        titleLarge = titleLarge.copy(fontFamily = PanelFont, fontSize = 23.sp, lineHeight = 29.sp),
        titleMedium = titleMedium.copy(fontFamily = PanelFont, fontSize = 19.sp, lineHeight = 25.sp),
        bodyLarge = bodyLarge.copy(fontFamily = PanelFont, fontSize = 18.sp, lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(fontFamily = PanelFont, fontSize = 16.sp, lineHeight = 22.sp),
        labelLarge = labelLarge.copy(fontFamily = PanelFont, fontSize = 16.sp, lineHeight = 20.sp),
        labelMedium = labelMedium.copy(fontFamily = PanelFont, fontSize = 14.sp, lineHeight = 18.sp),
        labelSmall = labelSmall.copy(fontFamily = PanelFont, fontSize = 13.sp, lineHeight = 17.sp),
    )
}

private val Graphite = darkColorScheme(
    primary = Color(0xffbbd9f5), onPrimary = Color(0xff12283a),
    primaryContainer = Color(0xff2d455d), onPrimaryContainer = Color(0xffeff8ff),
    inversePrimary = Color(0xff315b83),
    secondary = Color(0xffbbc8d5), onSecondary = Color(0xff263440),
    secondaryContainer = Color(0xff354658), onSecondaryContainer = Color(0xffeef5fc),
    tertiary = Color(0xff9dd4cb), onTertiary = Color(0xff123630),
    tertiaryContainer = Color(0xff2a4e48), onTertiaryContainer = Color(0xffc8f0e8),
    background = Color(0xff0d0f12), onBackground = Color(0xfffbfcfd),
    surface = Color(0xff0d0f12), onSurface = Color(0xfffbfcfd),
    surfaceDim = Color(0xff0d0f12), surfaceBright = Color(0xff393f46),
    surfaceContainerLowest = Color(0xff090b0d), surfaceContainerLow = Color(0xff1b1f24),
    surfaceContainer = Color(0xff262a30), surfaceContainerHigh = Color(0xff2d333b),
    surfaceContainerHighest = Color(0xff373f47),
    surfaceVariant = Color(0xff38434d), onSurfaceVariant = Color(0xffdce5ee),
    inverseSurface = Color(0xffebedf0), inverseOnSurface = Color(0xff20262d),
    outline = Color(0xffa7b5c2), outlineVariant = Color(0xff4d5965),
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
        theme == PanelTheme.SLATE -> Graphite
        theme == PanelTheme.AMBER -> Amber
        theme == PanelTheme.PORCELAIN -> Porcelain
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Graphite
        else -> Porcelain
    }
    MaterialTheme(colorScheme = colors, typography = PanelType, content = content)
}
