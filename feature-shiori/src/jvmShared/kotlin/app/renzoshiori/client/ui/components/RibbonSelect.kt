package app.renzoshiori.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.renzoshiori.client.ui.theme.RenzoColors
import kotlinx.coroutines.launch
import app.renzoshiori.client.ui.tv.LocalIsTv
import app.renzoshiori.client.ui.tv.TvSelectedMark
import app.renzoshiori.client.ui.tv.focusRing
import app.renzoshiori.client.ui.tv.rememberFocusState
import app.renzoshiori.client.ui.tv.tvClickable
import app.renzoshiori.client.ui.tv.tvContentColor

data class SelectOption(
    val value: String,
    val label: String,
    /** Colored status dot before the label (web's status Select rows). */
    val dotColor: Color? = null,
    /** Muted count after the label (web's live count badges). */
    val count: Int? = null,
    /** Renders the web's "└" prefix for a favourites sub-list. */
    val indented: Boolean = false,
    /** Leading glyph (the Browse source picker's globe / language flag stand-in). */
    val icon: ImageVector? = null,
)

/**
 * The web app's Select control (ui/select.tsx SelectTrigger), transliterated:
 * a 32dp-high rounded-lg bordered trigger showing the selected option (with
 * its status dot), a chevron, and a Popover-style dark menu of options with
 * dots and muted counts — the Library/Browse ribbon's filter/sort controls.
 */
@Composable
fun RibbonSelect(
    options: List<SelectOption>,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Shown when `value` matches no option (web's SelectValue placeholder). */
    placeholder: String? = null,
    /** Caps the trigger label so a long genre/source name can't stretch the ribbon. */
    maxTriggerWidth: androidx.compose.ui.unit.Dp = 148.dp,
    /**
     * FIXED trigger width — the web wraps each ribbon Select in a w-N div with
     * a w-full trigger, so triggers hold a set width with the caret pinned to
     * the right edge instead of hugging their label. null keeps hug-content
     * for callers with no web width to copy.
     */
    triggerWidth: androidx.compose.ui.unit.Dp? = null,
) {
    var open by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.value == value }
    val isTv = LocalIsTv.current
    val focus = rememberFocusState()

    // TV: the web's fixed trigger widths are sized for a desktop row; at the
    // panel-resolution chrome scale they overflowed the non-scrolling wide
    // ribbon and pushed the sort/size/Track-all cluster clean off a 1080p
    // panel. Widths shrink with the same factor the heights already use.
    val effectiveTriggerWidth = when {
        triggerWidth == null -> null
        isTv -> (triggerWidth.value * top.levitatemedia.renzo.hub.core.tvChromeScale()).dp
        else -> triggerWidth
    }
    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .then(if (effectiveTriggerWidth != null) Modifier.width(effectiveTriggerWidth) else Modifier)
                .height(if (isTv) (40 * top.levitatemedia.renzo.hub.core.tvChromeScale()).dp.coerceAtLeast(32.dp) else 32.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, RenzoColors.Border, RoundedCornerShape(8.dp))
                .background(RenzoColors.Background)
                .then(
                    // TV: ring + fill say "the cursor is here". The trigger's
                    // label already says what's selected, so colour is untouched.
                    if (isTv) {
                        Modifier.tvFocusTarget(
                            focused = focus.focused,
                            onFocused = focus::set,
                            radius = 8.dp,
                            fill = RenzoColors.Card,
                            onClick = { open = true },
                        )
                    } else {
                        Modifier.clickable { open = true }
                    },
                )
                .padding(horizontal = 10.dp),
        ) {
            if (selected?.dotColor != null) {
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(selected.dotColor),
                )
            }
            if (selected?.icon != null) {
                Icon(
                    selected.icon,
                    contentDescription = null,
                    tint = RenzoColors.MutedForeground,
                    modifier = Modifier.padding(end = 6.dp).size(14.dp),
                )
            }
            Text(
                selected?.label ?: placeholder ?: options.firstOrNull()?.label.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected == null && placeholder != null) RenzoColors.MutedForeground else RenzoColors.Foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = maxTriggerWidth),
            )
            // Web SelectValue: the selected option's live count rides in the
            // trigger too ("All 270"), muted.
            if (selected?.count != null && selected.count > 0) {
                Text(
                    selected.count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            // justify-between: at a fixed width the caret sits on the right
            // edge, not against the label.
            if (triggerWidth != null) Spacer(Modifier.weight(1f))
            // Web SelectTrigger: CaretSortIcon (up+down carets) at 50% — not
            // a single down chevron.
            Icon(
                Icons.Filled.UnfoldMore,
                contentDescription = null,
                tint = RenzoColors.Foreground.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp).size(16.dp),
            )
        }
        // A Popup-hosted DropdownMenu doesn't contain D-pad focus properly — the
        // cursor escapes into the screen behind it — so TV gets a focus-trapping
        // dialog with the same options instead. Touch keeps the menu verbatim.
        if (isTv && open) {
            TvSelectDialog(
                options = options,
                value = value,
                onChange = {
                    onChange(it)
                    open = false
                },
                onDismiss = { open = false },
            )
        }
        // Web SelectContent, transliterated: rounded-md bordered popover,
        // max-h-96 (384dp) with chevron scroll hints when the list overflows
        // (the genres menu), p-1 viewport, and COMPACT text-sm items — a
        // Material menu item's 48dp min-height is twice the web row. The
        // selected item carries a check pinned to the RIGHT edge (SelectItem's
        // pr-8 + absolute right-2 CheckIcon).
        DropdownMenu(
            expanded = open && !isTv,
            onDismissRequest = { open = false },
            containerColor = RenzoColors.Popover,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, RenzoColors.Border),
        ) {
            val listScroll = androidx.compose.foundation.rememberScrollState()
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            Column(Modifier.padding(horizontal = 4.dp)) {
                if (listScroll.canScrollBackward) {
                    ScrollHintRow(up = true) {
                        scope.launch { listScroll.animateScrollBy(-SCROLL_STEP_PX) }
                    }
                }
                Column(
                    Modifier
                        .heightIn(max = 384.dp)
                        .verticalScroll(listScroll),
                ) {
                    options.forEach { opt ->
                        val isSelected = opt.value == value
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    onChange(opt.value)
                                    open = false
                                }
                                .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        ) {
                            if (opt.indented) {
                                Text(
                                    "└",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = RenzoColors.MutedForeground.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            }
                            if (opt.dotColor != null) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(opt.dotColor),
                                )
                            }
                            if (opt.icon != null) {
                                Icon(
                                    opt.icon,
                                    contentDescription = null,
                                    tint = RenzoColors.MutedForeground,
                                    modifier = Modifier.padding(end = 8.dp).size(16.dp),
                                )
                            }
                            Text(
                                opt.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = RenzoColors.Foreground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 240.dp),
                            )
                            if (opt.count != null && opt.count > 0) {
                                Text(
                                    opt.count.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            Spacer(Modifier.weight(1f).widthIn(min = 12.dp))
                            Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = RenzoColors.Foreground,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                if (listScroll.canScrollForward) {
                    ScrollHintRow(up = false) {
                        scope.launch { listScroll.animateScrollBy(SCROLL_STEP_PX) }
                    }
                }
            }
        }
    }
}

/** How far a click on a scroll hint moves the list (~6 rows). */
private const val SCROLL_STEP_PX = 200f

/** SelectScrollUp/DownButton: a centred chevron row bounding an overflowing list. */
@Composable
private fun ScrollHintRow(up: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        Icon(
            if (up) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (up) "Scroll up" else "Scroll down",
            tint = RenzoColors.Foreground,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * The TV stand-in for the dropdown: a focus-trapping dialog whose rows carry
 * selection on colour + a leading check, and focus on the ring + fill. Both stay
 * legible at once, so moving the cursor never loses track of the active filter.
 */
@Composable
private fun TvSelectDialog(
    options: List<SelectOption>,
    value: String,
    onChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                // 60% of a TV's ~1270dp effective width was a huge sheet;
                // a select menu is a narrow list (2026-08-21).
                .widthIn(max = 440.dp)
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, RenzoColors.Border, RoundedCornerShape(12.dp))
                .background(RenzoColors.Popover)
                .padding(vertical = 8.dp),
        ) {
            LazyColumn {
                // No item key: an option list can legitimately repeat a value
                // (two sources with the same id), and a duplicate key crashes
                // a lazy list at runtime.
                items(options) { opt ->
                    val isSelected = opt.value == value
                    val focus = rememberFocusState()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .tvFocusTarget(
                                focused = focus.focused,
                                onFocused = focus::set,
                                radius = 8.dp,
                                fill = RenzoColors.Card,
                                onClick = { onChange(opt.value) },
                            )
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        TvSelectedMark(isSelected)
                        if (opt.indented) {
                            Text(
                                "└",
                                style = MaterialTheme.typography.bodyLarge,
                                color = RenzoColors.MutedForeground.copy(alpha = 0.6f),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        if (opt.dotColor != null) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(opt.dotColor),
                            )
                        }
                        if (opt.icon != null) {
                            Icon(
                                opt.icon,
                                contentDescription = null,
                                tint = tvContentColor(isSelected, focus.focused),
                                modifier = Modifier.padding(start = 8.dp).size(18.dp),
                            )
                        }
                        Text(
                            opt.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = tvContentColor(isSelected, focus.focused),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 10.dp).weight(1f),
                        )
                        if (opt.count != null && opt.count > 0) {
                            Text(
                                opt.count.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = RenzoColors.MutedForeground,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The web ribbon's small pill toggle — "My library" / "All libraries",
 * "Track all". Border + faint fill when off, primary-tinted when on. Never
 * dimmed: the callers hide it entirely when it doesn't apply.
 */
@Composable
fun RibbonToggleChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val isTv = LocalIsTv.current
    val focus = rememberFocusState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(if (isTv) (40 * top.levitatemedia.renzo.hub.core.tvChromeScale()).dp.coerceAtLeast(32.dp) else 32.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (active) RenzoColors.Primary.copy(alpha = 0.15f)
                else RenzoColors.Foreground.copy(alpha = 0.04f),
            )
            .border(
                1.dp,
                if (active) RenzoColors.Primary.copy(alpha = 0.40f) else RenzoColors.Border.copy(alpha = 0.40f),
                RoundedCornerShape(50),
            )
            .then(
                if (isTv) {
                    Modifier
                        .focusRing(focus.focused, 50.dp)
                        .tvClickable(onFocused = focus::set, onClick = onClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .padding(horizontal = 12.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (active) RenzoColors.Primary else RenzoColors.MutedForeground,
                modifier = Modifier.padding(end = 6.dp).size(14.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) RenzoColors.Primary else RenzoColors.MutedForeground,
            maxLines = 1,
        )
    }
}

/**
 * The Queue ribbon's segmented control (queue/page.tsx FilterPills): a rounded
 * track with hairline border, the active pill filled with the primary color
 * and primary-foreground text, inactive pills muted.
 */
@Composable
fun SegmentedPills(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, RenzoColors.Foreground.copy(alpha = 0.06f), RoundedCornerShape(50))
            .background(RenzoColors.Foreground.copy(alpha = 0.015f))
            .padding(2.dp),
    ) {
        val isTv = LocalIsTv.current
        labels.forEachIndexed { index, label ->
            val isActive = index == selectedIndex
            val focus = rememberFocusState()
            if (index > 0) Spacer(Modifier.size(2.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isActive) RenzoColors.Primary else Color.Transparent)
                    .then(
                        if (isTv) {
                            Modifier
                                .focusRing(focus.focused, 50.dp)
                                .tvClickable(onFocused = focus::set, onClick = { onSelect(index) })
                        } else {
                            Modifier.clickable { onSelect(index) }
                        },
                    )
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isActive) RenzoColors.PrimaryForeground else RenzoColors.MutedForeground,
                    maxLines = 1,
                )
            }
        }
    }
}
