package app.renzoshiori.client.ui.series

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import top.levitatemedia.renzo.hub.core.HubTarget
import top.levitatemedia.renzo.hub.core.notify.ReleaseAlerts
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.renzoshiori.client.ui.components.DragHandle
import app.renzoshiori.client.ui.components.dragReorderHandle
import app.renzoshiori.client.ui.components.dragReorderItem
import app.renzoshiori.client.ui.components.rememberDragReorderState
import app.renzoshiori.client.resources.Res
import app.renzoshiori.client.resources.*
import app.renzoshiori.client.data.network.absoluteUrl
import app.renzoshiori.client.ui.theme.RenzoColors
import app.renzoshiori.client.ui.tv.LocalIsTv
import app.renzoshiori.client.ui.util.screenWidthDp
import coil3.compose.AsyncImage

/** Tag pills shown before "Show more". Same number on the web (series-hero.tsx). */
private const val TAG_LIMIT = 12

/**
 * Transliteration of series-hero.tsx. The web hero is cover-beside-info on a
 * desktop viewport and stacks on mobile — the phone gets the stacked form:
 * blurred backdrop, centred cover, status pill, title, author, meta row,
 * genre pills, description with Read more, the paused-downloads callout, the
 * icon action row, and the mono storage-path line with its copy button.
 */
@Composable
fun SeriesHeroSection(
    state: SeriesDetailUiState,
    baseUrl: String,
    vm: SeriesDetailViewModel,
    onOpenChapter: (Double) -> Unit,
    onRequestDeleteSeries: () -> Unit,
) {
    // Declared ABOVE the early return below: a `remember` that sits after a
    // `?: return` lives in a conditional region, so the moment the guard trips
    // every value under it is dropped and silently starts over.
    // rememberSaveable, not remember: on the narrow layout this hero is a
    // LazyColumn item, and a plain remember dies when the item scrolls out of
    // view — expand it, scroll past, come back, and it has collapsed itself.
    // Keyed on the series id so opening a DIFFERENT series starts collapsed,
    // while the 10s/20s background re-pulls of the SAME series do not disturb it.
    var descriptionExpanded by rememberSaveable(state.series?.id) { mutableStateOf(false) }
    var tagsExpanded by rememberSaveable(state.series?.id) { mutableStateOf(false) }
    val series = state.series ?: return
    var coverExpanded by remember { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var categoryDialogOpen by remember { mutableStateOf(false) }
    var favoritesOpen by remember { mutableStateOf(false) }
    var trackerOpen by remember { mutableStateOf(false) }

    val coverUrl = state.displayThumbnail.takeIf { it.isNotEmpty() }?.let { absoluteUrl(baseUrl, it) }
    val statusColors = heroStatusColors(state.effectiveStatus)
    val statusLabel = heroStatusLabel(state.effectiveStatus)

    // The category the backend derived, falling back to parsing the storage
    // path ({owner}/{Category}/{leaf}), matched case-insensitively.
    val currentCategory = remember(series.category, series.path, state.categories) {
        val fromServer = series.category
        val direct = state.categories.firstOrNull { it.equals(fromServer, ignoreCase = true) }
        if (direct != null) direct else {
            val parts = (series.path.ifEmpty { series.storagePath }).split("/").filter { it.isNotEmpty() }
            val seg = if (parts.size >= 3) parts[parts.size - 2] else null
            seg?.let { s -> state.categories.firstOrNull { it.equals(s, ignoreCase = true) } }
        }
    }

    Box(Modifier.fillMaxWidth().clipToBounds()) {

        // ── Blurred banner + gradient + pink tint layers ──
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(28.dp, BlurredEdgeTreatment.Unbounded)
                    .alpha(0.4f),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            RenzoColors.Background.copy(alpha = 0.30f),
                            RenzoColors.Background.copy(alpha = 0.70f),
                            RenzoColors.Background,
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        // Web sm+: cover-BESIDE-info inside the centred max-w-7xl container
        // (the old exe's hero); narrow keeps the stacked mobile form. The info
        // stack is written once and placed by whichever container applies.
        val heroWide = !LocalIsTv.current && screenWidthDp() >= 1024.dp

        val coverBox: @Composable (Dp, Dp) -> Unit = { coverW, coverH ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(coverW)
                    .height(coverH)
                    .clip(RoundedCornerShape(12.dp))
                    .background(RenzoColors.Card)
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .clickable { coverExpanded = true },
            ) {
                // Renzō logo shows through whenever the cover is missing or
                // fails to load — the web's /renzo.png onError fallback.
                Image(
                    painter = painterResource(Res.drawable.splash_icon),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp).alpha(0.5f),
                )
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = state.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        val heroInfo: @Composable () -> Unit = {
            // ── Status pill ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(PillShape)
                    .background(statusColors.dot.copy(alpha = 0.12f))
                    .border(1.dp, statusColors.dot.copy(alpha = 0.25f), PillShape)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                if (statusColors.pulse) {
                    val transition = rememberInfiniteTransition(label = "status-pulse")
                    val pulse by transition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.35f,
                        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                        label = "status-pulse-alpha",
                    )
                    StatusDot(statusColors.dot.copy(alpha = pulse))
                } else {
                    StatusDot(statusColors.dot)
                }
                Text(
                    statusLabel.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = statusColors.text,
                )
            }

            // ── Title (web: text-2xl → md:text-[34px]) ──
            // Selectable (user direction 2026-08-19): the title and byline are
            // exactly what people copy out to search elsewhere.
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    state.title,
                    fontSize = if (heroWide) 34.sp else 26.sp,
                    lineHeight = if (heroWide) 40.sp else 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = RenzoColors.Foreground,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ── Author / artist ──
            if (series.author.isNotEmpty() || series.artist.isNotEmpty()) {
                val suffix =
                    if (series.artist.isNotEmpty() && series.artist != series.author)
                        " · illust. ${series.artist}" else ""
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        "by ${series.author}$suffix",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }

            // ── Inline meta row ──
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "Ch. ${series.chapterList.ifEmpty { "—" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Text("·", style = MaterialTheme.typography.bodySmall, color = Muted.copy(alpha = 0.4f))
                Text(
                    "${series.chapterCount} chapter${if (series.chapterCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                if (!series.lastChangeUTC.isNullOrEmpty()) {
                    Text("·", style = MaterialTheme.typography.bodySmall, color = Muted.copy(alpha = 0.4f))
                    Text(
                        "Updated ${formatRelative(series.lastChangeUTC)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }

            // ── Genre pills ──
            // Tag-heavy sources run to 80+ pills and push the action buttons
            // screens down, so past TAG_LIMIT they collapse like the
            // description. Web twin: series-hero.tsx (same limit).
            if (series.genre.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (if (tagsExpanded) series.genre else series.genre.take(TAG_LIMIT)).forEach { g ->
                        Text(
                            g,
                            fontSize = 11.sp,
                            color = RenzoColors.Foreground.copy(alpha = 0.8f),
                            modifier = Modifier
                                .clip(PillShape)
                                .background(ForegroundFaint06)
                                .border(1.dp, Border40, PillShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                if (series.genre.size > TAG_LIMIT) {
                    Text(
                        if (tagsExpanded) "Show less" else "Show more (${series.genre.size - TAG_LIMIT})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { tagsExpanded = !tagsExpanded },
                    )
                }
            }

            // ── Description + Read more (expanded scrolls in its own box) ──
            if (series.description.isNotBlank()) {
                // Web: max-w-[70ch] so desktop lines stay readable.
                Column(modifier = if (heroWide) Modifier.widthIn(max = 640.dp) else Modifier) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            series.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                            maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            // Expanded means expanded. This used to drop the text
                            // into a 256dp box with its own scrollbar, so "Read
                            // more" showed the description in full for a moment
                            // and then clipped it to a few lines more than it had
                            // before — and every later expand went straight to the
                            // clipped version. On the narrow layout that inner
                            // scroller also sat inside the page's own vertical
                            // scroll, so dragging the description fought the list.
                        )
                    }
                    if (series.description.length > 240) {
                        Text(
                            if (descriptionExpanded) "Show less" else "Read more",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { descriptionExpanded = !descriptionExpanded },
                        )
                    }
                }
            }

            // ── Paused-downloads callout ──
            if (state.canManageDownloads && state.pausedDownloads) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Yellow500.copy(alpha = 0.10f))
                        .border(1.dp, Yellow500.copy(alpha = 0.40f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.Pause,
                            contentDescription = null,
                            tint = Yellow300,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "Downloads are paused for this series.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Yellow300,
                        )
                    }
                    androidx.compose.material3.Button(
                        onClick = { vm.togglePausedDownloads() },
                        shape = MaterialTheme.shapes.small,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Yellow500,
                            contentColor = Color.Black,
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Resume Downloads", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ── Action toolbar ──
            val readTarget = remember(state.chapters) { computeReadTarget(state.chapters) }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                if (readTarget != null) {
                    HeroActionButton(
                        icon = Icons.Filled.MenuBook,
                        contentDescription = "${readTarget.label} — chapter ${formatNumber(readTarget.number)}",
                        label = if (heroWide) readTarget.label else null,
                        primary = true,
                        onClick = { onOpenChapter(readTarget.number) },
                    )
                }

                // Collections: an outline folder until the series is in one, a
                // filled one (and the pink tint) once it is — the web's
                // Folder with fill-current.
                HeroActionButton(
                    icon = if (state.isFavorited) Icons.Filled.Folder else Icons.Outlined.Folder,
                    contentDescription = if (state.isFavorited) "In your collections — manage" else "Add to a collection",
                    label = if (!heroWide) null else if (state.isFavorited) "Collected" else "Collect",
                    tint = if (state.isFavorited) Pink500 else RenzoColors.Foreground,
                    borderColor = if (state.isFavorited) Pink500.copy(alpha = 0.6f) else Border60,
                    background = if (state.isFavorited) Pink500.copy(alpha = 0.15f) else Color.Transparent,
                    onClick = { favoritesOpen = true },
                )

                // New-chapter notifications for this series (Android phones
                // only; the switch for the whole library is in Account).
                if (ReleaseAlerts.supported && !LocalIsTv.current) {
                    var notify by remember(series.id) { mutableStateOf(ReleaseAlerts.isOn(HubTarget.Shiori, series.id)) }
                    HeroActionButton(
                        icon = if (notify) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsNone,
                        contentDescription = if (notify) "Notifying you about new chapters — turn off" else "Notify me about new chapters",
                        label = if (!heroWide) null else if (notify) "Notifying" else "Notify",
                        tint = if (notify) MaterialTheme.colorScheme.primary else RenzoColors.Foreground,
                        borderColor = if (notify) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Border60,
                        onClick = {
                            notify = !notify
                            ReleaseAlerts.setSeries(HubTarget.Shiori, series.id, notify)
                            if (notify) ReleaseAlerts.switchedOn()
                        },
                    )
                }

                if (state.hasConnectedTracker) {
                    HeroActionButton(
                        icon = Icons.Filled.Podcasts,
                        contentDescription = "Track this series",
                        label = if (heroWide) "Track" else null,
                        tint = if (state.tracked) MaterialTheme.colorScheme.primary else RenzoColors.Foreground,
                        borderColor = if (state.tracked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Border60,
                        onClick = { trackerOpen = true },
                    )
                }

                if (state.canManageDownloads && !state.pausedDownloads) {
                    HeroActionButton(
                        icon = Icons.Filled.Pause,
                        contentDescription = "Pause Downloads",
                        label = if (heroWide) "Pause Downloads" else null,
                        primary = true,
                        onClick = { vm.togglePausedDownloads() },
                    )
                }

                // Hide decimal chapters — a saved per-series setting, so it reads as
                // an on/off control with visible state rather than a command buried
                // in a menu (it used to be a "More" item, where nothing showed
                // whether it was currently on). Web parity: series-hero.tsx.
                if (state.canEdit) {
                    val hideDecimals = state.series?.hideDecimalChapters == true
                    HeroActionButton(
                        icon = Icons.Filled.Numbers,
                        contentDescription = if (hideDecimals) "Decimal chapters hidden" else "Hide decimal chapters",
                        label = if (heroWide) (if (hideDecimals) "Decimals Hidden" else "Hide Decimals") else null,
                        tint = if (hideDecimals) MaterialTheme.colorScheme.primary else RenzoColors.Foreground,
                        borderColor = if (hideDecimals) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Border60,
                        onClick = { vm.toggleHideDecimalChapters() },
                    )
                }

                // Deliberately a first-class toolbar control rather than a "More"
                // menu item (web parity, series-hero.tsx): it decides which source a
                // chapter comes from, so its state has to be visible at a glance.
                if (state.canManageDownloads) {
                    val preferFree = state.series?.prioritizeFreeChapters == true
                    HeroActionButton(
                        icon = Icons.Filled.LockOpen,
                        contentDescription = if (preferFree) "Prioritizing free chapters" else "Prioritize free chapters",
                        label = if (heroWide) (if (preferFree) "Prioritizing Free" else "Prioritize Free") else null,
                        tint = if (preferFree) MaterialTheme.colorScheme.primary else RenzoColors.Foreground,
                        borderColor = if (preferFree) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Border60,
                        onClick = { vm.togglePrioritizeFreeChapters() },
                    )
                }

                // Auto-buy paid chapters when the reader reaches them: one tap flips
                // it for this series; landing back on the account setting clears the
                // override so the series follows the account again (series-hero.tsx).
                if (state.canManageDownloads) {
                    val autoBuy = state.series?.autoBuyPaidChapters ?: state.autoBuyAccountDefault
                    val next = !autoBuy
                    HeroActionButton(
                        icon = Icons.Filled.MonetizationOn,
                        contentDescription = if (autoBuy) "Auto-buy on" else "Auto-buy off",
                        label = if (heroWide) (if (autoBuy) "Auto-buy On" else "Auto-buy Off") else null,
                        tint = if (autoBuy) MaterialTheme.colorScheme.primary else RenzoColors.Foreground,
                        borderColor = if (autoBuy) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Border60,
                        onClick = { vm.setAutoBuy(if (next == state.autoBuyAccountDefault) null else next) },
                    )
                }

                if (state.canEdit || state.canDelete) {
                    Box {
                        HeroActionButton(
                            icon = Icons.Filled.MoreHoriz,
                            contentDescription = "More actions",
                            label = if (heroWide) "More" else null,
                            onClick = { moreOpen = true },
                        )
                        DropdownMenu(
                            expanded = moreOpen,
                            onDismissRequest = { moreOpen = false },
                        ) {
                            if (state.canEdit) {
                                DropdownMenuItem(
                                    text = { Text("Refresh metadata & chapters") },
                                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                    onClick = { moreOpen = false; vm.refreshMetadata() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Scan for new chapters") },
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                    onClick = { moreOpen = false; vm.scanForNewChapters() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Verify integrity") },
                                    leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                                    onClick = { moreOpen = false; vm.verifyIntegrity() },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (series.nsfw) "Unmark as 18+" else "Mark as 18+") },
                                    leadingIcon = {
                                        Text(
                                            "18+",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (series.nsfw) Red500 else RenzoColors.Foreground,
                                        )
                                    },
                                    onClick = { moreOpen = false; vm.toggleNsfw() },
                                )
                                if (state.categories.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Category" + (currentCategory?.let { ": $it" } ?: "")) },
                                        leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                                        onClick = { moreOpen = false; categoryDialogOpen = true },
                                    )
                                }
                            }
                            if (state.canDelete) {
                                if (state.canEdit) HorizontalDivider(color = Border40)
                                DropdownMenuItem(
                                    text = { Text("Delete series", color = DestructiveText) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = DestructiveText,
                                        )
                                    },
                                    onClick = { moreOpen = false; onRequestDeleteSeries() },
                                )
                            }
                        }
                    }
                }
            }

            // ── Storage path ──
            if (series.path.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = Muted.copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        series.path,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Muted.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable {
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(series.path ?: ""))
                                copied = true
                            },
                    ) {
                        Icon(
                            if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            contentDescription = "Copy storage path",
                            tint = if (copied) Green500 else Muted,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            if (heroWide) {
                // Web sm+: mx-auto max-w-7xl, cover 210×315 left, info right.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier
                        .widthIn(max = 1280.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 40.dp),
                ) {
                    coverBox(210.dp, 315.dp)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        heroInfo()
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // ── Cover (click to expand) ──
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        coverBox(150.dp, 225.dp)
                    }
                    heroInfo()
                }
            }
        }
    }

    // ── Cover lightbox ──
    if (coverExpanded && coverUrl != null) {
        Dialog(onDismissRequest = { coverExpanded = false }) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable { coverExpanded = false },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = state.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                )
            }
        }
    }

    // ── Category picker ──
    if (categoryDialogOpen) {
        RenzoDialog(
            title = "Category",
            description = "Move this series into a category folder.",
            onDismiss = { categoryDialogOpen = false },
            body = {
                CategoryPicker(
                    state = state,
                    vm = vm,
                    currentCategory = currentCategory,
                    onPick = { cat ->
                        categoryDialogOpen = false
                        if (cat != currentCategory) vm.setCategory(cat)
                    },
                )
            },
            footer = { OutlineDialogButton(text = "Close") { categoryDialogOpen = false } },
        )
    }

    // ── Collections picker ──
    if (favoritesOpen) {
        FavoritesDialog(state = state, vm = vm, onDismiss = { favoritesOpen = false })
    }

    // ── Tracker control ──
    if (trackerOpen) {
        TrackerDialog(state = state, vm = vm, onDismiss = { trackerOpen = false })
    }
}

/**
 * The Category picker's list (series-hero.tsx `CategoryPicker`). Pick a row to
 * move the series there. The owner also gets a grip on each row to reorder the
 * categories; a drop saves at once, and the library filter and Settings follow.
 */
@Composable
private fun CategoryPicker(
    state: SeriesDetailUiState,
    vm: SeriesDetailViewModel,
    currentCategory: String?,
    onPick: (String?) -> Unit,
) {
    val drag = rememberDragReorderState<String>()
    val categories = state.categories
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        categories.forEach { cat ->
            key(cat) {
                val selected = cat == currentCategory
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().dragReorderItem(drag, cat),
                ) {
                    if (state.canReorderCategories) {
                        DragHandle(
                            "Drag to reorder $cat",
                            Modifier.dragReorderHandle(
                                drag, cat, categories, 6.dp,
                                onReorder = vm::showCategoryOrder,
                                onDrop = vm::saveCategoryOrder,
                            ),
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        MenuRow(
                            label = cat,
                            icon = Icons.Filled.Check,
                            iconTint = if (selected) RenzoColors.Foreground else Color.Transparent,
                            onClick = { onPick(cat) },
                        )
                    }
                }
            }
        }
        if (currentCategory != null) {
            MenuRow(
                label = "Uncategorized",
                labelColor = Muted,
                onClick = { onPick(null) },
            )
        }
        if (state.canReorderCategories && categories.size > 1) {
            Text(
                "Drag a grip to reorder the categories. The library and Settings use the same order.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * The hero toolbar button. The web renders `px-0 w-9` icon-only on mobile and
 * `sm:w-auto sm:px-4` icon-plus-label on desktop — [label] non-null is the
 * desktop form.
 */
@Composable
private fun HeroActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    label: String? = null,
    primary: Boolean = false,
    tint: Color = if (primary) RenzoColors.PrimaryForeground else RenzoColors.Foreground,
    borderColor: Color = Border60,
    background: Color = if (primary) MaterialTheme.colorScheme.primary else Color.Transparent,
) {
    val shell = Modifier
        .clip(MaterialTheme.shapes.small)
        .background(background)
        .border(1.dp, if (primary) Color.Transparent else borderColor, MaterialTheme.shapes.small)
        .clickable(onClick = onClick)
    if (label == null) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp).then(shell)) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(16.dp))
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(36.dp).then(shell).padding(horizontal = 16.dp),
        ) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(16.dp))
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = tint,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** read-series-button.tsx's target resolution: continue / read / reread. */
private data class ReadTarget(val number: Double, val label: String)

private fun computeReadTarget(chapters: List<ChapterRowUi>): ReadTarget? {
    val readable = chapters.filter { it.filename != null }.sortedBy { it.number }
    if (readable.isEmpty()) return null
    readable.firstOrNull { !it.isCompleted && it.progress > 0f }?.let {
        return ReadTarget(it.number, "Continue")
    }
    readable.firstOrNull { !it.isCompleted }?.let {
        val anyRead = readable.any { c -> c.isCompleted }
        return ReadTarget(it.number, if (anyRead) "Continue" else "Read")
    }
    return ReadTarget(readable.first().number, "Reread")
}

/**
 * favorite-button.tsx's picker: tick the collections this series belongs to,
 * plus a "New collection" creator. Sub-collections render indented under their
 * parent, and every row carries Move up / Move down within its own siblings.
 */
@Composable
private fun FavoritesDialog(
    state: SeriesDetailUiState,
    vm: SeriesDetailViewModel,
    onDismiss: () -> Unit,
) {
    var newTabName by remember { mutableStateOf("") }
    val seriesId = state.series?.id ?: ""
    // sortedBy is stable: equal sortOrders keep the server's CreatedAt order,
    // the same order vm.moveFavoriteList computes its moves against.
    val topLevel = state.favoriteLists.filter { it.parentId == null }.sortedBy { it.sortOrder }
    val topDrag = rememberDragReorderState<String>()

    RenzoDialog(
        title = "Collections",
        description = "Tick the collections this series belongs to. Create collections and sub-collections to organize, and drag them or use the arrows to reorder them.",
        onDismiss = onDismiss,
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (topLevel.isEmpty()) {
                    Text(
                        "No collections yet — create your first one below (e.g. “Manhwa”).",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                // Keyed by id (the web's key={list.id}) so a moved row takes its
                // composition with it. Positional, the D-pad focus would stay in
                // the slot and land on whichever collection was swapped in —
                // pressing Move up twice would just swap the pair back.
                // A top-level collection drags with its sub-collections (one
                // Column, so one item); a sub-collection drags only among its
                // parent's other children. Each drop saves its own group.
                val topIds = topLevel.map { it.id }
                topLevel.forEachIndexed { i, list ->
                    key(list.id) {
                        val children = state.favoriteLists.filter { it.parentId == list.id }.sortedBy { it.sortOrder }
                        val childIds = children.map { it.id }
                        val childDrag = rememberDragReorderState<String>()
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.dragReorderItem(topDrag, list.id),
                        ) {
                            FavoriteListRow(
                                list.id, list.name, seriesId in list.seriesIds, list.seriesIds.size,
                                depth = 0, index = i, siblings = topLevel.size, vm = vm,
                                handle = Modifier.dragReorderHandle(
                                    topDrag, list.id, topIds, 4.dp,
                                    onReorder = vm::showFavoriteOrder,
                                    onDrop = { vm.saveFavoriteOrder(null, it) },
                                ),
                            )
                            children.forEachIndexed { j, child ->
                                key(child.id) {
                                    FavoriteListRow(
                                        child.id, child.name, seriesId in child.seriesIds, child.seriesIds.size,
                                        depth = 1, index = j, siblings = children.size, vm = vm,
                                        modifier = Modifier.dragReorderItem(childDrag, child.id),
                                        handle = Modifier.dragReorderHandle(
                                            childDrag, child.id, childIds, 4.dp,
                                            onReorder = vm::showFavoriteOrder,
                                            onDrop = { vm.saveFavoriteOrder(list.id, it) },
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = Border40, modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newTabName,
                        onValueChange = { newTabName = it },
                        placeholder = {
                            Text(
                                "New collection, e.g. Manhwa #2",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryDialogButton(text = "Collection", enabled = newTabName.isNotBlank()) {
                        vm.createFavoriteList(newTabName)
                        newTabName = ""
                    }
                }
            }
        },
        footer = { OutlineDialogButton(text = "Done") { onDismiss() } },
    )
}

/**
 * One collection in the picker: tick to add/remove this series, plus the web's
 * Move up / Move down and delete actions (favorite-button.tsx ListRow).
 * [index] and [siblings] place the row among its own siblings — the top-level
 * collections, or one parent's sub-collections — which is all a move can cross.
 * Delete asks first, inline — "Delete “name” and its sub-collections? Series
 * stay in your library." — the web's window.confirm wording. [handle] goes on
 * the row's drag grip and [modifier] on the row itself (a sub-collection's
 * dragReorderItem; a top-level row's is on the Column it shares with its
 * sub-collections).
 */
@Composable
private fun FavoriteListRow(
    listId: String,
    name: String,
    checked: Boolean,
    count: Int,
    depth: Int,
    index: Int,
    siblings: Int,
    vm: SeriesDetailViewModel,
    handle: Modifier,
    modifier: Modifier = Modifier,
) {
    var confirming by remember(listId) { mutableStateOf(false) }
    val canMoveUp = index > 0
    val canMoveDown = index < siblings - 1
    // D-pad: the arrow that moves a row onto an end disables itself (the
    // series page's own convention, the source-priority arrows), and a
    // disabled clickable gives up focus, which would drop a TV user back to
    // the top of the dialog. So when a D-pad press is about to do that, the
    // focus goes to the row's other arrow once the row has moved. Only a
    // FOCUSED arrow's press counts, so a phone tap (touch mode never focuses
    // a clickable) doesn't pull focus onto a control nobody was on.
    val upFocus = remember { FocusRequester() }
    val downFocus = remember { FocusRequester() }
    var upFocused by remember { mutableStateOf(false) }
    var downFocused by remember { mutableStateOf(false) }
    // (requester, the index the row lands on). Any index change clears it, so
    // a move that never happened (offline) can't fire later on another one.
    var refocus by remember { mutableStateOf<Pair<FocusRequester, Int>?>(null) }
    LaunchedEffect(index) {
        refocus?.let { (target, at) -> if (at == index) runCatching { target.requestFocus() } }
        refocus = null
    }
    if (confirming) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier
                .fillMaxWidth()
                .padding(start = (depth * 22).dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(RenzoColors.Destructive.copy(alpha = 0.08f))
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Text(
                "Delete “$name”${if (depth == 0) " and its sub-collections" else ""}? Series stay in your library.",
                style = MaterialTheme.typography.bodySmall,
                color = RenzoColors.Foreground,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineDialogButton(text = "Cancel") { confirming = false }
                DestructiveDialogButton(text = "Delete") {
                    confirming = false
                    vm.deleteFavoriteList(listId)
                }
            }
        }
        return
    }
    // The grip sits outside the row's tap target (the web's is its own
    // button), so a tap meant for the grip doesn't tick the collection.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = (depth * 22).dp),
    ) {
        DragHandle("Drag to reorder $name", handle)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable { vm.toggleFavoriteList(listId, checked) }
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(18.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(1.dp, if (checked) Color.Transparent else Border60, MaterialTheme.shapes.extraSmall),
            ) {
                if (checked) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = RenzoColors.PrimaryForeground,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            // favorite-button.tsx: the name with its count as an inline
            // `ml-1.5 text-[11px] tabular-nums` span, in one flex-1 block. As two
            // Texts, the name took its full width and squeezed the count into a
            // one-digit column on a phone, wrapping "49" onto two lines. The web
            // truncates to one line; a phone gets two, because one cut "Manhwa/
            // Manhua Favourites #1" and "#2" to the same "Manhwa/Manhua Favourites…".
            // A figure space (no-break, ~ml-1.5) keeps the count with the name's
            // last word, and lines break between whole words only, never with a
            // hyphen inside one.
            Text(
                buildAnnotatedString {
                    append(name)
                    append("\u2007")
                    withStyle(SpanStyle(fontSize = 11.sp, color = Muted.copy(alpha = 0.6f), fontFeatureSettings = "tnum")) {
                        append("$count")
                    }
                },
                style = MaterialTheme.typography.bodyMedium.copy(hyphens = Hyphens.None, lineBreak = LineBreak.Paragraph),
                color = RenzoColors.Foreground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // The web's row actions sit together (`gap-0.5`), apart from the name.
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .focusRequester(upFocus)
                        .onFocusChanged { upFocused = it.isFocused }
                        .clickable(enabled = canMoveUp) {
                            if (upFocused && index == 1) refocus = downFocus to 0
                            vm.moveFavoriteList(listId, -1)
                        }
                        .then(if (canMoveUp) Modifier else Modifier.alphaHalf()),
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Move up",
                        tint = Muted,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .focusRequester(downFocus)
                        .onFocusChanged { downFocused = it.isFocused }
                        .clickable(enabled = canMoveDown) {
                            if (downFocused && index == siblings - 2) refocus = upFocus to siblings - 1
                            vm.moveFavoriteList(listId, 1)
                        }
                        .then(if (canMoveDown) Modifier else Modifier.alphaHalf()),
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Move down",
                        tint = Muted,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable { confirming = true },
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete collection",
                        tint = RenzoColors.Destructive,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * series-tracking-button.tsx: one "Track this series" switch plus a per-tracker
 * list. Connecting a tracker itself still happens on Account → Trackers.
 */
@Composable
private fun TrackerDialog(
    state: SeriesDetailUiState,
    vm: SeriesDetailViewModel,
    onDismiss: () -> Unit,
) {
    RenzoDialog(
        title = "Track",
        onDismiss = onDismiss,
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(
                        "Track this series",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = RenzoColors.Foreground,
                        modifier = Modifier.weight(1f),
                    )
                    RenzoSwitch(
                        checked = state.tracked,
                        onCheckedChange = { vm.setTracking(it) },
                        enabled = !state.trackerBusy,
                    )
                }
                HorizontalDivider(color = Border40)
                Text(
                    "Per tracker",
                    fontSize = 11.sp,
                    color = Muted,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
                state.trackerConfigs.forEach { c ->
                    val link = state.trackerMatches.firstOrNull {
                        it.seriesId.equals(state.series?.id ?: "", ignoreCase = true) &&
                            it.provider == c.provider &&
                            (it.mappingStatus == 1 || it.mappingStatus == 2)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        Text(
                            c.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (c.isConnected) RenzoColors.Foreground else Muted,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            when {
                                !c.isConnected -> "Not connected"
                                link != null -> link.externalSeriesTitle ?: "Linked"
                                else -> "Not linked"
                            },
                            fontSize = 11.sp,
                            color = if (link != null) MaterialTheme.colorScheme.primary else Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        footer = { OutlineDialogButton(text = "Done") { onDismiss() } },
    )
}
