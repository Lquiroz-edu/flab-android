package com.lquiroz.flab.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lquiroz.flab.ui.theme.FLabColors
import com.lquiroz.flab.ui.theme.FLabTokens

/**
 * The F/LAB component set.
 *
 * DoD 41 asks the app to speak the language it is trying to bring to the system: if F/LAB talks
 * about fluidity while its own screens snap between states, nobody believes the rest. So every
 * interactive surface here responds to a press with a spring rather than a ripple, and every state
 * change is animated rather than swapped.
 */

/** The standard F/LAB surface: raised, hairline-outlined, generous corner. */
@Composable
fun FLabCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Int = 20,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
        label = "cardPress",
    )

    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(FLabTokens.RadiusCard))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, FLabColors.outline, RoundedCornerShape(FLabTokens.RadiusCard))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(contentPadding.dp),
        content = content,
    )
}

/**
 * F/LAB's frosted-glass surface, reserved for the single most important call-to-action on a
 * screen. DoD 40 asks for "pocos ajustes visibles simultáneamente" — using this everywhere would
 * cancel out the reason it stands out at all, so it belongs on the thing the user most needs to
 * notice (the guided setup card on Home), not on every card in the app.
 *
 * This is a **static approximation**, not the real backdrop blur `FoldWallpaperService`'s glass
 * cards use, and that difference is deliberate rather than a shortcut. That wallpaper blurs one
 * static bitmap, rebuilt only when the surface size or the theme changes. A card here sits over a
 * *scrolling* screen — blurring what is actually behind it live would mean capturing a fresh
 * snapshot of that content every frame, which is precisely the kind of per-frame cost DoD 22 and 23
 * rule out elsewhere in this project. So this reaches for the same reading — translucency, a soft
 * tinted highlight, a hairline border — through a gradient tint instead of a sampled blur, and does
 * not pretend otherwise.
 */
@Composable
fun FLabGlassCard(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    contentPadding: Int = 22,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = FLabColors.isDark
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(FLabTokens.RadiusCard))
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = if (dark) 0.20f else 0.16f),
                        MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.94f else 0.90f),
                    ),
                ),
            )
            .border(
                1.dp,
                Color.White.copy(alpha = if (dark) 0.14f else 0.55f),
                RoundedCornerShape(FLabTokens.RadiusCard),
            )
            .padding(contentPadding.dp),
        content = content,
    )
}

/** A section heading. Small, wide-tracked, quiet. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = FLabColors.textSecondary,
        modifier = modifier,
    )
}

/**
 * The Home row from DoD 10: a name, a value, and a dot that says whether it is healthy.
 */
@Composable
fun StatusRow(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
) {
    FLabCard(modifier = modifier.fillMaxWidth(), onClick = onClick, contentPadding = 18) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.padding(end = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (detail != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = FLabColors.textSecondary,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(accent)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun Dot(color: Color, size: Int = 8) {
    val animated by animateColorAsState(color, label = "dot")
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(FLabTokens.RadiusPill))
            .background(animated),
    )
}

/** A small capsule used for modes, states and counts. */
@Composable
fun Pill(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val background by animateColorAsState(
        targetValue = if (filled) accent else accent.copy(alpha = 0.12f),
        label = "pillBackground",
    )
    val foreground by animateColorAsState(
        targetValue = if (filled) MaterialTheme.colorScheme.surface else accent,
        label = "pillForeground",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(FLabTokens.RadiusPill))
            .background(background)
            .border(1.dp, accent.copy(alpha = if (filled) 0f else 0.4f), RoundedCornerShape(FLabTokens.RadiusPill))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = foreground)
    }
}

/** The primary action button. Used for the kill switch, so it has to read as deliberate. */
@Composable
fun FLabButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    prominent: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 800f),
        label = "buttonPress",
    )
    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(FLabTokens.RadiusControl))
            .background(if (prominent) accent else Color.Transparent)
            .border(
                1.dp,
                if (prominent) Color.Transparent else accent.copy(alpha = 0.5f),
                RoundedCornerShape(FLabTokens.RadiusControl),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (prominent) MaterialTheme.colorScheme.surface else accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** A labelled key/value line for dense surfaces like Diagnostics. */
@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = FLabColors.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}
