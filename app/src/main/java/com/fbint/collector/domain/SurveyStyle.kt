package com.fbint.collector.domain

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fbint.collector.data.remote.dto.SurveyDto

/**
 * Compose-friendly view of a Formbricks survey's `styling` block. The web survey honours
 * brand color, background, roundness, button colors, etc. — we mirror enough of that for
 * the runner to feel visually continuous with the original.
 */
data class SurveyStyle(
    val brandColor: Color,
    val onBrandColor: Color,
    val backgroundColor: Color,
    val cardColor: Color,
    val questionTextColor: Color,
    val inputBackgroundColor: Color,
    val inputTextColor: Color,
    val optionBackgroundColor: Color,
    val cornerRadius: Dp,
    val buttonCornerRadius: Dp,
    val isLogoHidden: Boolean,
) {
    companion object {
        val Default = SurveyStyle(
            brandColor = Color(0xFF0F766E),
            onBrandColor = Color.White,
            backgroundColor = Color(0xFFF8FAFC),
            cardColor = Color.White,
            questionTextColor = Color(0xFF111827),
            inputBackgroundColor = Color(0xFFF3F4F6),
            inputTextColor = Color(0xFF111827),
            optionBackgroundColor = Color(0xFFF3F4F6),
            cornerRadius = 12.dp,
            buttonCornerRadius = 12.dp,
            isLogoHidden = true,
        )
    }
}

fun SurveyDto.computedStyle(): SurveyStyle {
    val s = styling ?: return SurveyStyle.Default
    val brand = s.brandColor?.light?.toComposeColor() ?: SurveyStyle.Default.brandColor
    val background = s.background?.bg?.toComposeColor() ?: SurveyStyle.Default.backgroundColor
    val card = s.cardBgColor?.light?.toComposeColor() ?: SurveyStyle.Default.cardColor
    val roundness = (s.roundness?.coerceAtLeast(0) ?: 12).dp
    return SurveyStyle(
        brandColor = brand,
        onBrandColor = s.buttonTextColor?.light?.toComposeColor() ?: bestForeground(brand),
        backgroundColor = background,
        cardColor = card,
        questionTextColor = s.questionColor?.light?.toComposeColor() ?: SurveyStyle.Default.questionTextColor,
        inputBackgroundColor = s.inputColor?.light?.toComposeColor() ?: SurveyStyle.Default.inputBackgroundColor,
        inputTextColor = s.inputTextColor?.light?.toComposeColor() ?: SurveyStyle.Default.inputTextColor,
        optionBackgroundColor = s.optionBgColor?.light?.toComposeColor() ?: SurveyStyle.Default.optionBackgroundColor,
        cornerRadius = roundness,
        buttonCornerRadius = roundness,
        isLogoHidden = s.isLogoHidden ?: false,
    )
}

private fun String.toComposeColor(): Color? {
    val hex = trim().removePrefix("#")
    val parsed = when (hex.length) {
        6 -> 0xFF000000 or hex.toLongOrNull(16)!!
        8 -> hex.toLongOrNull(16)!!
        3 -> {
            val r = hex[0].toString().repeat(2)
            val g = hex[1].toString().repeat(2)
            val b = hex[2].toString().repeat(2)
            0xFF000000 or "$r$g$b".toLong(16)
        }
        else -> return null
    }
    return Color(parsed)
}

/**
 * Pick black or white for foreground based on perceived luminance of the background — keeps
 * button text readable when the brand color is dark vs light.
 */
private fun bestForeground(bg: Color): Color {
    val r = bg.red; val g = bg.green; val b = bg.blue
    val luminance = 0.299 * r + 0.587 * g + 0.114 * b
    return if (luminance < 0.55) Color.White else Color(0xFF111827)
}
