package com.example.ui.theme

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
import com.example.model.AppThemeMode

private val DarkColorScheme = darkColorScheme(
  primary = StreamCyan,
  onPrimary = Color(0xFF021E2C),
  primaryContainer = Color(0xFF0F3A53),
  onPrimaryContainer = Color(0xFFCBEBFF),
  secondary = StreamIndigo,
  onSecondary = Color(0xFF1E1E46),
  secondaryContainer = Color(0xFF2C2F63),
  onSecondaryContainer = Color(0xFFE0E0FF),
  tertiary = StreamEmerald,
  onTertiary = Color(0xFF003822),
  background = DarkBackground,
  onBackground = Color(0xFFF1F5F9),
  surface = DarkSurface,
  onSurface = Color(0xFFF8FAFC),
  surfaceVariant = DarkSurfaceVariant,
  onSurfaceVariant = Color(0xFF94A3B8),
  outline = DarkBorder,
  error = StreamRose,
  onError = Color.White
)

private val LightColorScheme = lightColorScheme(
  primary = StreamCyanDark,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFE0F2FE),
  onPrimaryContainer = Color(0xFF0369A1),
  secondary = Color(0xFF4F46E5),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFEEF2FF),
  onSecondaryContainer = Color(0xFF312E81),
  tertiary = Color(0xFF059669),
  onTertiary = Color.White,
  background = LightBackground,
  onBackground = Color(0xFF0F172A),
  surface = Color.White,
  onSurface = Color(0xFF0F172A),
  surfaceVariant = Color(0xFFF1F5F9),
  onSurfaceVariant = Color(0xFF475569),
  outline = Color(0xFFCBD5E1),
  error = StreamRose,
  onError = Color.White
)

@Composable
fun StreamVaultTheme(
  themeMode: AppThemeMode = AppThemeMode.LIGHT, // Default to clean Light Theme as requested
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val isDark = when (themeMode) {
    AppThemeMode.LIGHT -> false
    AppThemeMode.DARK -> true
    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
  }

  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    isDark -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
