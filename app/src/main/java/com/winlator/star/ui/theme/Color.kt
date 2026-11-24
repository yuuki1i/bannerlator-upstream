package com.winlator.star.ui.theme

import androidx.compose.ui.graphics.Color

val Background = Color(0xFF070B12)
val Surface = Color(0xFF101824)
val SurfaceVariant = Color(0xFF182536)
val Primary = Color(0xFF65C8FF)
val PrimaryVariant = Color(0xFF329BE8)
val PrimaryDim = Color(0xFF173A59)
val OnPrimary = Color(0xFFFFFFFF)
val OnSurface = Color(0xFFEAF3FC)
val OnSurfaceVariant = Color(0xFF9AAFC4)
val OnBackground = Color(0xFFFFFFFF)
val Error = Color(0xFFCF6679)
/** Destructive actions — delete buttons, selected-for-removal state. */
val DangerRed = Color(0xFFE05C4A)

// ── Store-semantic colours ──────────────────────────────────────────────────────────
// Steam's own price conventions, and the ONLY colours in the storefront that are not taken
// from the live ColorScheme. Same rationale as DangerRed: a discount badge that turns pink
// because the user picked a pink accent stops reading as "on sale", and a "Free to Play"
// line has to say free, not "accent". Chosen to stay legible on the AMOLED black ground.
/** Ink for the discount-percentage chip ("-50%"). */
val StoreDiscountInk = Color(0xFFBEEE11)
/** Fill behind [StoreDiscountInk] on the discount chip. */
val StoreDiscountBg = Color(0xFF2C4210)
/** "Free to Play" price line, and the free-title action button's ink. */
val StoreFreeGreen = Color(0xFF8FD14F)
val Divider = Color(0xFF2A3D51)
val GlowPurple = Primary
val AccentBlue = Primary
