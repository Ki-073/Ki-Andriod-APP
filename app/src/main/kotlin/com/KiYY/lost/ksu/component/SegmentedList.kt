package com.KiYY.lost.ksu.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SegmentedListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.ListItemColors

/**
 * Ported from KernelSU SegmentedList.kt (tiann/KernelSU)
 * Local shapes provider so SegmentedListItem can render grouped cards.
 */
val LocalListItemShapes = compositionLocalOf<ListItemShapes?> { null }

@Composable
private fun defaultSegmentedColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceBright,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceBright,
    supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun defaultSingleSegmentedShape(index: Int, count: Int): ListItemShapes {
    val base = ListItemDefaults.segmentedShapes(index, count)
    return if (count == 1) {
        base.copy(shape = MaterialTheme.shapes.large)
    } else {
        base
    }
}

@Composable
fun SegmentedColumn(
    modifier: Modifier = Modifier,
    title: String = "",
    content: List<@Composable () -> Unit>,
) {
    if (content.isEmpty()) return

    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            content.forEachIndexed { index, itemContent ->
                CompositionLocalProvider(
                    LocalListItemShapes provides defaultSingleSegmentedShape(
                        index = index,
                        count = content.size
                    ),
                ) {
                    itemContent()
                }
            }
        }
    }
}

@Composable
fun SegmentedItemContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceBright,
        shape = shapes.shape,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun SegmentedListItem(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: ListItemColors = defaultSegmentedColors(),
    headlineContent: @Composable () -> Unit,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    androidx.compose.material3.SegmentedListItem(
        onClick = onClick ?: {},
        enabled = enabled,
        colors = colors,
        shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1),
        modifier = modifier,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        overlineContent = overlineContent,
        supportingContent = supportingContent,
        verticalAlignment = Alignment.CenterVertically,
        content = headlineContent
    )
}

// Ported from KernelSU SegmentedRadioItem
@Composable
fun SegmentedRadioItem(
    title: String,
    summary: String? = null,
    colors: ListItemColors = defaultSegmentedColors(),
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.material3.SegmentedListItem(
        selected = selected,
        onClick = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
            onClick()
        },
        enabled = enabled,
        colors = colors,
        shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1),
        modifier = Modifier,
        leadingContent = {
            androidx.compose.material3.RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled
            )
        },
        supportingContent = summary?.let { { Text(it) } },
        verticalAlignment = Alignment.CenterVertically,
        content = { Text(title) },
    )
}

// Ported from KernelSU SegmentedCheckboxItem
@Composable
fun SegmentedCheckboxItem(
    title: String,
    summary: String? = null,
    colors: ListItemColors = defaultSegmentedColors(),
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    androidx.compose.material3.SegmentedListItem(
        checked = checked,
        onCheckedChange = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
            onCheckedChange(it)
        },
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1),
        modifier = Modifier,
        leadingContent = {
            androidx.compose.material3.Checkbox(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
                interactionSource = interactionSource,
                modifier = Modifier.size(24.dp)
            )
        },
        supportingContent = summary?.let { { Text(it) } },
        verticalAlignment = Alignment.CenterVertically,
        content = { Text(title) },
    )
}