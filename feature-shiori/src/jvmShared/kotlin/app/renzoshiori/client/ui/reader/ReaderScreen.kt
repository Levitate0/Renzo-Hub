package app.renzoshiori.client.ui.reader

import app.renzoshiori.client.ui.util.HubBackHandler
import app.renzoshiori.client.ui.util.hubToast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import top.levitatemedia.renzo.hub.core.HubPlatform
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import app.renzoshiori.client.ui.util.screenHeightDp
import app.renzoshiori.client.ui.util.screenWidthDp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.renzoshiori.client.ui.theme.RenzoColors
import app.renzoshiori.client.ui.tv.LocalIsTv
import app.renzoshiori.client.ui.tv.rememberFocusState
import app.renzoshiori.client.ui.tv.rememberIsTvDevice
import app.renzoshiori.client.ui.tv.tvFocusable
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The reader — a transliteration of the web reader (RenzoFrontend
 * src/app/reader/page.tsx): translucent top chrome, a bottom scrubber, the full
 * Reader Settings panel, the chapter list, tap zones, chapter transitions and
 * infinite scroll across chapter boundaries.
 *
 * Layout modes:
 *  - paged / paged-rtl / double: HorizontalPager, one slot per swipe.
 *  - webtoon / longstrip / vertical: a LazyColumn strip. The current page comes
 *    from the item under a probe line a third of the way down the viewport —
 *    the same tracking rule the web reader uses, but Compose hands us
 *    visibility info directly instead of DOM rect math. The end-of-chapter
 *    block is a real list item, so reaching it drives completion exactly like
 *    the web reader's sentinel page.
 */
@Composable
fun ReaderScreen(
    seriesId: String,
    chapterNumber: Double,
    onExit: () -> Unit,
    /** Non-null = preview a Browse item live from the source (nothing stored). */
    previewMihonId: String? = null,
    previewTitle: String = "",
    vm: ReaderViewModel = viewModel(
        key = if (previewMihonId != null) {
            "preview-$previewMihonId-$chapterNumber"
        } else {
            "reader-$seriesId-$chapterNumber"
        },
        factory = if (previewMihonId != null) {
            ReaderViewModel.previewFactory(previewMihonId, chapterNumber, previewTitle)
        } else {
            ReaderViewModel.factory(seriesId, chapterNumber)
        },
    ),
) {
    val state by vm.state.collectAsState()
    val settings = state.settings
    val mode = state.resolvedMode()
    // The app provides LocalIsTv; the device check is a fallback so the reader
    // is never left pointer-only if something above it forgets to. Read
    // unconditionally — a composable call behind `||` would appear and
    // disappear with the local's value.
    val deviceIsTv = rememberIsTvDevice()
    val isTv = LocalIsTv.current || deviceIsTv

    var chromeVisible by remember { mutableStateOf(true) }
    var settingsOpen by remember { mutableStateOf(false) }
    var chaptersOpen by remember { mutableStateOf(false) }
    // Page the bottom scrubber asked for; the active renderer scrolls to it and clears it.
    var seekTarget by remember { mutableStateOf<Int?>(null) }
    // Live thumb position while dragging, so the scrubber doesn't snap back to
    // the tracked page between frames.
    var scrubbing by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(state.toast) {
        val message = state.toast
        if (message != null) {
            hubToast(message)
            vm.consumeToast()
        }
    }

    // ── D-pad plumbing (TV only; none of it is reached on a touch device) ──
    //
    // The reading surface owns its own scrolling, so it publishes what an arrow
    // means to it (page turn, scroll step, pan) through this handle and the key
    // sink below calls into it.
    val nav = rememberReaderNavHandle()
    val contentFocus = remember { FocusRequester() }
    val chromeFocus = remember { FocusRequester() }
    val overlayOpen = settingsOpen || chaptersOpen
    // Only the reading surface gets the key sink. The locked and error screens
    // are ordinary button layouts — swallowing their arrows would make them
    // unreachable, which is the exact failure this is meant to prevent.
    val dpadReading = !state.loading && !state.locked && state.error == null

    // ── Rebindable hotkeys (web parity; keyboards, not the TV remote) ──────
    // TV keeps its own D-pad key sink above — this path is real keyboards on
    // desktop (or one plugged into a phone). Actions run through the same
    // seams the chrome uses: the nav handle for page/scroll, the scrubber's
    // seekTarget for first/last, the VM for chapters and bookmark.
    val runHotkey: (HotkeyAction) -> Unit = { action ->
        when (action) {
            // Logical forward/back: the nav handle takes PHYSICAL directions
            // and resolves RTL itself, so forward is LEFT in paged-RTL.
            HotkeyAction.NEXT_PAGE, HotkeyAction.SCROLL_DOWN ->
                nav.onDpad?.invoke(
                    when {
                        mode.continuous -> DpadDir.DOWN
                        mode.rtl -> DpadDir.LEFT
                        else -> DpadDir.RIGHT
                    },
                )
            HotkeyAction.PREV_PAGE, HotkeyAction.SCROLL_UP ->
                nav.onDpad?.invoke(
                    when {
                        mode.continuous -> DpadDir.UP
                        mode.rtl -> DpadDir.RIGHT
                        else -> DpadDir.LEFT
                    },
                )
            HotkeyAction.NEXT_CHAPTER -> vm.goToChapter(1)
            HotkeyAction.PREV_CHAPTER -> vm.goToChapter(-1)
            HotkeyAction.FIRST_PAGE -> seekTarget = 0
            HotkeyAction.LAST_PAGE -> seekTarget = (state.activePageCount - 1).coerceAtLeast(0)
            HotkeyAction.TOGGLE_CHROME -> chromeVisible = !chromeVisible
            HotkeyAction.TOGGLE_CHAPTERS -> chaptersOpen = !chaptersOpen
            HotkeyAction.TOGGLE_SETTINGS -> settingsOpen = !settingsOpen
            HotkeyAction.BOOKMARK -> vm.toggleBookmark()
            HotkeyAction.EXIT -> onExit()
        }
    }
    val hotkeyFocus = remember { FocusRequester() }
    val hotkeyModifier = if (isTv) {
        Modifier
    } else {
        Modifier
            .focusRequester(hotkeyFocus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val token = hotkeyToken(event) ?: return@onKeyEvent false
                // Web: physical arrows swap in RTL paged mode so ← always
                // reads forward (manga convention); custom bindings are
                // taken literally.
                val lookup = if (!mode.continuous && mode.rtl) {
                    when (token) {
                        "ArrowLeft" -> "ArrowRight"
                        "ArrowRight" -> "ArrowLeft"
                        else -> token
                    }
                } else {
                    token
                }
                val hk = state.settings.hotkeys
                // Space / PageDown / PageUp keep paging even when unbound, so
                // clearing the arrows never loses basic navigation.
                val action = HotkeyAction.entries.firstOrNull { hk[it] == lookup }
                    ?: when (token) {
                        "Space", "PageDown" -> HotkeyAction.NEXT_PAGE
                        "PageUp" -> HotkeyAction.PREV_PAGE
                        else -> null
                    }
                if (action == null) {
                    false
                } else {
                    runHotkey(action)
                    true
                }
            }
    }
    LaunchedEffect(isTv, overlayOpen, state.loading) {
        // The panels are focusable popups/sheets; take the keys back when
        // they close (and once loading settles on entry).
        if (!isTv && !overlayOpen) {
            withFrameNanos { }
            runCatching { hotkeyFocus.requestFocus() }
        }
    }

    // On a television, chrome visible ⇔ the chrome holds focus. That is how
    // every TV video player behaves and it keeps the arrows unambiguous: while
    // the controls are up they move between controls, and while they are down
    // they read. Nothing here can strand focus — one of the two is always
    // focusable, and Back always leads out.
    LaunchedEffect(isTv, chromeVisible, overlayOpen, state.loading, state.locked, state.error) {
        if (!isTv || overlayOpen) return@LaunchedEffect
        // The target has to be laid out before it can take focus.
        withFrameNanos { }
        runCatching {
            if (chromeVisible && !state.loading) chromeFocus.requestFocus()
            else if (dpadReading) contentFocus.requestFocus()
        }
    }
    HubBackHandler(enabled = isTv && !overlayOpen) {
        if (chromeVisible) chromeVisible = false else onExit()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(settings.background.argb)).then(hotkeyModifier)) {
        if (isTv) {
            // A key sink rather than a focusable wrapper around the content: it
            // has no children, so it can never swallow the chrome's focus, and
            // it consumes every arrow while reading so a stray focus search
            // can't walk the cursor somewhere invisible. It stops being
            // focusable while the chrome is up, which is what makes traversal
            // between the top bar and the scrubber unambiguous.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .focusRequester(contentFocus)
                    .dpadKeys(
                        onCenter = {
                            chromeVisible = !chromeVisible
                            true
                        },
                        onDir = { dir ->
                            nav.onDpad?.invoke(dir)
                            true
                        },
                    )
                    .focusable(enabled = dpadReading && !chromeVisible),
            )
        }
        when {
            state.error != null -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.error ?: "", color = ReaderPalette.Text80, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onExit,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RenzoColors.Secondary,
                        contentColor = ReaderPalette.Text,
                    ),
                ) { Text("Go back") }
            }

            state.loading -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = ReaderPalette.Text70, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(16.dp))
                val opening = state.openingLabel
                if (opening != null) {
                    Text(
                        "OPENING",
                        fontSize = 12.sp,
                        letterSpacing = 1.8.sp,
                        color = ReaderPalette.Text40,
                    )
                    Text(
                        opening,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = ReaderPalette.Text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
                    )
                } else {
                    Text("Loading chapter…", fontSize = 14.sp, color = ReaderPalette.Text50)
                }
            }

            state.locked -> LockedChapterScreen(
                chapterLabel = state.chapters.firstOrNull { it.number == state.chapterNumber }
                    ?.let { state.nameOf(it) } ?: "Chapter ${trimNumber(state.chapterNumber)}",
                url = state.lockedUrl,
                hasPrev = state.hasPrev,
                hasNext = state.hasNext,
                checking = state.unlockChecking,
                onCheckNow = vm::checkUnlock,
                onPrev = { vm.goToChapter(-1) },
                onNext = { vm.goToChapter(1) },
                onExit = onExit,
            )

            else -> key(state.chapterNumber) {
                if (mode.continuous) {
                    ContinuousReader(
                        state = state,
                        mode = mode,
                        nav = nav,
                        seekTarget = seekTarget,
                        onSeekHandled = { seekTarget = null },
                        onPosition = vm::onPosition,
                        onImageLoaded = vm::onImageLoaded,
                        onNearEnd = vm::maybeAppendNext,
                        onNearStart = vm::maybePrependPrev,
                        onRetryAppend = vm::retryAppend,
                        onToggleChrome = { chromeVisible = !chromeVisible },
                        onNextChapter = { vm.goToChapter(1) },
                        onPrevChapter = { vm.goToChapter(-1) },
                        onExit = onExit,
                    )
                } else {
                    PagedReader(
                        state = state,
                        mode = mode,
                        isTv = isTv,
                        nav = nav,
                        seekTarget = seekTarget,
                        onSeekHandled = { seekTarget = null },
                        onPosition = vm::onPosition,
                        onImageLoaded = vm::onImageLoaded,
                        onPrefetchNext = { vm.neighborChapter(1)?.number?.let(vm::prefetchChapter) },
                        onToggleChrome = { chromeVisible = !chromeVisible },
                        onNextChapter = { vm.goToChapter(1) },
                        onPrevChapter = { vm.goToChapter(-1) },
                        onExit = onExit,
                    )
                }
            }
        }

        // "Loading next chapter…" (infinite scroll)
        if (state.appending) {
            Pill(
                text = "Loading next chapter…",
                spinner = true,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
            )
        }
        // Brief confirmation that a new chapter opened
        val arrived = state.arrivedLabel
        if (arrived != null && !state.loading) {
            Pill(
                text = arrived,
                spinner = false,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
            )
        }

        // ── Top chrome ──
        if (chromeVisible && !state.loading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(ReaderPalette.Chrome)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                ChromeIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = ReaderPalette.Text,
                    isTv = isTv,
                    onClick = onExit,
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        state.seriesTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = ReaderPalette.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val next = state.nextChapterName
                    Text(
                        state.activeLabel + (if (next != null) " · next: $next" else ""),
                        fontSize = 12.sp,
                        color = ReaderPalette.Text60,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Preview reads store nothing, so there is nothing to bookmark.
                val bookmarkable = state.activeChapter?.takeIf { !state.preview }
                if (bookmarkable != null) {
                    ChromeIconButton(
                        icon = if (bookmarkable.bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (bookmarkable.bookmarked) "Remove bookmark" else "Bookmark chapter",
                        // Colour already carries state here, so focus stays on
                        // the ring — the two must not use the same channel.
                        tint = if (bookmarkable.bookmarked) ReaderPalette.Pink400 else ReaderPalette.Text,
                        isTv = isTv,
                        onClick = vm::toggleBookmark,
                    )
                }
                ChromeIconButton(
                    icon = Icons.AutoMirrored.Filled.List,
                    contentDescription = "Chapters",
                    tint = ReaderPalette.Text,
                    isTv = isTv,
                    onClick = { chaptersOpen = true },
                )
                // Reader settings is where the cursor lands when the chrome
                // comes up: on a TV it is the control panel that matters, and
                // it should never be more than one press away.
                ChromeIconButton(
                    icon = Icons.Filled.Tune,
                    contentDescription = "Reader settings",
                    tint = ReaderPalette.Text,
                    isTv = isTv,
                    modifier = if (isTv) Modifier.focusRequester(chromeFocus) else Modifier,
                    onClick = { settingsOpen = true },
                )
            }
        }

        // ── Bottom chrome ──
        if (chromeVisible && !state.loading && state.error == null && !state.locked && state.activePageCount > 0) {
            val pageCount = state.activePageCount
            val current = state.currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(ReaderPalette.Chrome)
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ChromeIconButton(
                        icon = Icons.Filled.ChevronLeft,
                        contentDescription = "Previous chapter",
                        tint = ReaderPalette.Text,
                        isTv = isTv,
                        onClick = { vm.goToChapter(-1) },
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (isTv) {
                            // Material's Slider only understands drag, so on a
                            // remote it would be a focus stop that cannot be
                            // moved. Left/right step a page here.
                            ReaderTvScrubber(
                                page = current,
                                pageCount = pageCount,
                                showPageNumber = settings.showPageNumber,
                                onSeek = { seekTarget = it },
                            )
                        } else {
                            // Right-to-left paged reading mirrors the scrubber, as on the web.
                            CompositionLocalProvider(
                                LocalLayoutDirection provides
                                    if (mode.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                            ) {
                                Slider(
                                    value = scrubbing ?: current.toFloat(),
                                    onValueChange = { raw ->
                                        scrubbing = raw
                                        seekTarget = raw.roundToInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                                    },
                                    onValueChangeFinished = { scrubbing = null },
                                    valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = RenzoColors.Primary,
                                        activeTrackColor = RenzoColors.Primary,
                                        inactiveTrackColor = Color(0x33FFFFFF),
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    if (settings.showPageNumber && !isTv) {
                        val shown = (scrubbing?.roundToInt() ?: current).coerceIn(0, pageCount - 1)
                        Text(
                            "${(shown + 1).coerceAtMost(pageCount)} / $pageCount",
                            fontSize = 12.sp,
                            color = ReaderPalette.Text70,
                        )
                    }
                    ChromeIconButton(
                        icon = Icons.Filled.ChevronRight,
                        contentDescription = "Next chapter",
                        tint = ReaderPalette.Text,
                        isTv = isTv,
                        onClick = { vm.goToChapter(1) },
                    )
                }
                if (isTv) {
                    // The one thing that isn't discoverable by looking: the
                    // arrows read the chapter once these controls are out of
                    // the way.
                    Text(
                        "OK shows these controls · BACK hides them · arrows turn the page",
                        fontSize = 11.sp,
                        color = ReaderPalette.Text40,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
                    )
                }
            }
        }
    }

    if (settingsOpen) {
        ReaderSettingsSheet(
            state = state,
            mode = mode,
            isTv = isTv,
            onDismiss = { settingsOpen = false },
            onModeChange = vm::setMode,
            onSettingsChange = vm::updateSettings,
            onClearCache = vm::clearStreamCache,
            onToggleChapterRead = vm::toggleChapterRead,
        )
    }
    if (chaptersOpen) {
        ReaderChapterListSheet(
            state = state,
            isTv = isTv,
            onDismiss = { chaptersOpen = false },
            onPick = { number ->
                chaptersOpen = false
                vm.jumpToChapter(number)
            },
        )
    }
}

/**
 * A chrome button that can be seen from the sofa.
 *
 * Material's ripple is a focus indicator you can read at arm's length and not
 * at three metres, so on a TV the button carries the 3dp accent ring instead.
 * The tint is passed through untouched: on the bookmark button colour already
 * means "bookmarked", and focus must not borrow that channel.
 */
@Composable
private fun ChromeIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (!isTv) {
        IconButton(onClick = onClick, modifier = modifier) {
            Icon(icon, contentDescription = contentDescription, tint = tint)
        }
        return
    }
    val focus = rememberFocusState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(46.dp)
            .tvFocusable(
                focused = focus.focused,
                onFocused = focus::set,
                radius = 23.dp,
                onClick = onClick,
            ),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

/** The web's floating status pills (bg-black/70, rounded-full, backdrop blur). */
@Composable
private fun Pill(text: String, spinner: Boolean, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(ReaderPalette.Chrome)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        if (spinner) {
            CircularProgressIndicator(
                color = ReaderPalette.Text,
                strokeWidth = 1.5.dp,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 13.sp, color = ReaderPalette.Text)
    }
}

// ── Continuous (webtoon / longstrip / vertical) ───────────────────────────

/**
 * How close to the end the reader must be before the next chapter is fetched.
 *
 * The two modes are measured differently on purpose. Continuous (longstrip,
 * webtoon, vertical) is measured in SCREENS: a "page" there can be a banner or
 * a metre-long strip, so a page count says nothing about how much scrolling is
 * actually left. Paged mode is measured in PAGES, where one page is one turn.
 */
private const val CONTINUOUS_PREFETCH_SCREENS = 1.5f
private const val PAGED_PREFETCH_PAGES = 2

private const val KIND_PAGE = 0
private const val KIND_DIVIDER = 1
private const val KIND_END = 2

private data class StripItem(val segIndex: Int, val pageIndex: Int, val kind: Int)

@Composable
private fun ContinuousReader(
    state: ReaderUiState,
    mode: ResolvedMode,
    nav: ReaderNavHandle,
    seekTarget: Int?,
    onSeekHandled: () -> Unit,
    onPosition: (Int, Int) -> Unit,
    onImageLoaded: (Int, Int) -> Unit,
    onNearEnd: (Int) -> Unit,
    onNearStart: () -> Unit,
    onRetryAppend: () -> Unit,
    onToggleChrome: () -> Unit,
    onNextChapter: () -> Unit,
    onPrevChapter: () -> Unit,
    onExit: () -> Unit,
) {
    val settings = state.settings
    val segments = state.segments
    val listState: LazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Real aspect ratios as images decode, so a page box stops being a guess
    // (the web reader's loadedDimsRef — without it, boxes leave gaps).
    val loadedAspect = remember { mutableStateMapOf<String, Float>() }
    // Natural page width in px — the clamp that stops upscaling past native
    // resolution (reader-image-quality handoff correction: 720px sources were
    // being magnified into a ~900px column; detail can't be invented).
    val loadedWidth = remember { mutableStateMapOf<String, Int>() }
    // Pages whose decoded height exceeded the texture-safe limit — rendered
    // sliced (TallPages.kt) instead of as one over-limit bitmap.
    val tallPages = remember { mutableStateMapOf<String, Boolean>() }
    // A page whose image never arrives used to leave an empty box with no
    // explanation and no way out — indistinguishable from the reader hanging.
    // These track per-page failure and a manual retry counter (bumping it
    // changes the request key, so Coil refetches instead of replaying its
    // cached failure).
    val loadFailed = remember { mutableStateMapOf<String, Boolean>() }
    val retryTick = remember { mutableStateMapOf<String, Int>() }
    val placeholderHeight = screenHeightDp() * 0.7f

    val strip = remember(segments) {
        buildList {
            segments.forEachIndexed { si, seg ->
                if (si > 0) add(StripItem(si, -1, KIND_DIVIDER))
                for (p in 0 until seg.pageCount) add(StripItem(si, p, KIND_PAGE))
            }
            add(StripItem(segments.lastIndex.coerceAtLeast(0), -1, KIND_END))
        }
    }
    // Item index of the first page of each segment — used for seeking.
    val segStart = remember(strip) {
        val out = HashMap<Int, Int>()
        strip.forEachIndexed { index, item ->
            if (item.kind == KIND_PAGE && !out.containsKey(item.segIndex)) out[item.segIndex] = index
        }
        out
    }

    // Land on the resume page when re-opening a partially-read chapter — ONCE
    // per chapter. This used to key on the first segment, which any PREPEND
    // replaces — refiring the scroll and yanking the view back to an old
    // position mid-read.
    var resumed by remember(state.chapterNumber) { mutableStateOf(false) }
    LaunchedEffect(state.chapterNumber, segments.isNotEmpty()) {
        if (!resumed && segments.isNotEmpty()) {
            resumed = true
            if (state.resumePage > 0) listState.scrollToItem(state.resumePage)
        }
    }

    // Scrubber seek — relative to the segment currently on screen.
    LaunchedEffect(seekTarget) {
        val target = seekTarget ?: return@LaunchedEffect
        val base = segStart[state.activeSegIndex] ?: 0
        listState.scrollToItem((base + target).coerceIn(0, (strip.size - 1).coerceAtLeast(0)))
        onSeekHandled()
    }

    // Page tracking against a probe line a third of the way down the viewport.
    val currentItem by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val probe = info.viewportStartOffset + info.viewportSize.height / 3
            info.visibleItemsInfo.lastOrNull { it.offset <= probe }?.index ?: 0
        }
    }
    LaunchedEffect(strip) {
        snapshotFlow { currentItem }.collect { index ->
            val item = strip.getOrNull(index) ?: return@collect
            when (item.kind) {
                KIND_PAGE -> onPosition(item.segIndex, item.pageIndex)
                // Reaching the end block is unambiguous proof the chapter is
                // done — resolve progress to exactly 100%.
                KIND_END -> {
                    val last = segments.lastIndex
                    val count = segments.lastOrNull()?.pageCount ?: 0
                    if (last >= 0 && count > 0) onPosition(last, count - 1)
                }
                else -> Unit // divider: leave the tracked position alone
            }
        }
    }
    // Infinite scroll: pull the next chapter as the bottom approaches.
    //
    // This has to be a PIXEL test, like the web's ("append when less than
    // 0.75 viewports of scrolling remain"). An item-index test — "the last
    // visible item is within N of the end" — is satisfied again the instant a
    // short chapter is appended, because the new pages are unmeasured
    // placeholders that haven't been laid out yet. That fires another append,
    // and another, walking the whole series in seconds.
    //
    // The decision also needs to know WHICH chapter the reader is in, and the
    // honest answer is the chapter owning the last real page above the
    // boundary block — not the tracked "active" segment, which an insert can
    // shift onto the new chapter without the reader moving.
    //
    // Re-evaluated on every layout change rather than on the rising edge: an
    // edge only fires once, so if a guard (cooldown, a still-loading append)
    // rejected it, nothing fired again until the condition went false and true
    // — i.e. until you scrolled up and back down.
    LaunchedEffect(strip) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            // Within CONTINUOUS_PREFETCH_SCREENS of the end of what's loaded.
            // Anything below the last VISIBLE item is unmeasured, so when the
            // final page is on screen, allow for the end block beneath it —
            // it is deliberately one viewport tall.
            val viewport = info.viewportEndOffset
            val near = when {
                last == null || viewport <= 0 -> false
                last.index == strip.size - 1 ->
                    (last.offset + last.size) - viewport < viewport * CONTINUOUS_PREFETCH_SCREENS
                last.index == strip.size - 2 ->
                    (last.offset + last.size) < viewport * CONTINUOUS_PREFETCH_SCREENS
                else -> false
            }
            // Segment of the last page above the boundary — "what chapter does
            // the page above the next-chapter marker belong to".
            val pageSeg = info.visibleItemsInfo
                .asReversed()
                .firstNotNullOfOrNull { item ->
                    strip.getOrNull(item.index)?.takeIf { it.kind == KIND_PAGE }?.segIndex
                }
            near to pageSeg
        }.collect { (near, pageSeg) ->
            if (near && pageSeg != null) onNearEnd(pageSeg)
        }
    }
    // Infinite scroll upward (web parity): once the reader has genuinely
    // scrolled UP, nearing the top of what's loaded prepends the previous
    // chapter. Requiring upward intent keeps a fresh chapter open — which
    // starts at the very top — from instantly pulling chapter N-1. Unlike the
    // downward pixel test, an index test can't chain-fire here: a prepend
    // pushes this index deep into the strip, so the condition self-clears.
    // The strip's item KEYS keep the viewport anchored through the insert.
    LaunchedEffect(strip) {
        var lastIndex = -1
        var lastOffset = 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val up = lastIndex != -1 &&
                    (index < lastIndex || (index == lastIndex && offset < lastOffset))
                lastIndex = index
                lastOffset = offset
                if (!up) return@collect
                val item = strip.getOrNull(index) ?: return@collect
                if (item.segIndex == 0 && (item.kind != KIND_PAGE || item.pageIndex <= 1)) {
                    onNearStart()
                }
            }
    }

    val widthFraction = (settings.maxWidthPct / 100f).coerceIn(0.2f, 1f)
    // Page width is a fraction of the viewport, so on its own it can never make
    // the strip bigger than the screen. Scale multiplies on top and is allowed
    // to overflow — past 100% of the viewport the strip pans sideways instead
    // of being clipped, which is what reading at a distance actually needs.
    val scale = (settings.scalePct / 100f).coerceIn(0.5f, 3f)
    val screenWidth = screenWidthDp()
    val contentWidth = screenWidth * (widthFraction * scale)
    val overflowing = widthFraction * scale > 1.001f
    // Decode target for the strip's pages: the drawn column width in PHYSICAL
    // px (containerSize is px — no dp/density conversion to get wrong).
    val stripTargetPx = quantizePagePx(
        (androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width * widthFraction * scale).toInt(),
    )
    val hScroll = rememberScrollState()
    val panStepPx = with(LocalDensity.current) { (screenWidth * 0.35f).toPx() }

    // D-pad: up/down are the scroll step (the same `tapAdvancePct` a tap uses),
    // left/right pan when the strip is wider than the screen and otherwise do
    // the same thing as up/down — on a remote every arrow should move you
    // forward through the chapter rather than doing nothing.
    SideEffect {
        nav.onDpad = { dir ->
            val viewport = listState.layoutInfo.viewportSize.height
            val amount = (viewport * (settings.tapAdvancePct / 100f)).coerceAtLeast(1f)
            when (dir) {
                DpadDir.DOWN -> scope.launch { listState.animateScrollBy(amount) }
                DpadDir.UP -> scope.launch { listState.animateScrollBy(-amount) }
                DpadDir.RIGHT ->
                    if (overflowing && hScroll.canScrollForward) scope.launch { hScroll.animateScrollBy(panStepPx) }
                    else scope.launch { listState.animateScrollBy(amount) }
                DpadDir.LEFT ->
                    if (overflowing && hScroll.canScrollBackward) scope.launch { hScroll.animateScrollBy(-panStepPx) }
                    else scope.launch { listState.animateScrollBy(-amount) }
            }
            Unit
        }
    }
    DisposableEffect(nav) { onDispose { nav.onDpad = null } }

    // The tap zones move up to this wrapper: the strip itself is now only as
    // wide as the content, and a tap in the margin beside it has to keep
    // working exactly as it did when the list filled the screen.
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(settings.tapNavigation, settings.tapAdvancePct) {
                detectTapGestures { offset ->
                    if (!settings.tapNavigation) {
                        onToggleChrome()
                        return@detectTapGestures
                    }
                    val x = offset.x / size.width.toFloat()
                    val amount =
                        listState.layoutInfo.viewportSize.height * (settings.tapAdvancePct / 100f)
                    when {
                        x > 0.7f -> scope.launch { listState.animateScrollBy(amount) }
                        x < 0.3f -> scope.launch { listState.animateScrollBy(-amount) }
                        else -> onToggleChrome()
                    }
                }
            }
            .then(if (overflowing) Modifier.horizontalScroll(hScroll) else Modifier),
    ) {
        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement =
                if (mode == ResolvedMode.VERTICAL) Arrangement.spacedBy(settings.gapPx.dp) else Arrangement.Top,
            modifier = Modifier.fillMaxHeight().width(contentWidth),
        ) {
            items(
                count = strip.size,
                key = { index ->
                    val item = strip[index]
                    "${segments.getOrNull(item.segIndex)?.key ?: item.segIndex}:${item.kind}:${item.pageIndex}"
                },
            ) { index ->
                val item = strip[index]
                val seg = segments.getOrNull(item.segIndex)
                when {
                    item.kind == KIND_PAGE && seg != null -> {
                        val cacheKey = "${seg.key}:${item.pageIndex}"
                        val serverAspect = seg.dims.getOrNull(item.pageIndex)?.let { dims ->
                            dims.first.toFloat() / dims.second.toFloat()
                        }
                        val aspect = loadedAspect[cacheKey] ?: serverAspect
                        val attempt = retryTick[cacheKey] ?: 0
                        val failed = loadFailed[cacheKey] == true
                        var settled by remember(cacheKey, attempt) { mutableStateOf(false) }
                        // Tall-page detection (TallPages.kt): branch on the
                        // DECODED height, never on format — plenty of manhwa
                        // sit under the texture limit and are unaffected.
                        // Server dims flag it up front; streamed pages flag on
                        // first decode.
                        val serverTall = (seg.dims.getOrNull(item.pageIndex)?.second ?: 0) > TEXTURE_SAFE_HEIGHT_PX
                        val isTall = HubPlatform.isDesktop && (serverTall || tallPages[cacheKey] == true)
                        // Web parity: the page fills the CONFIGURED column
                        // (settings width × zoom) even past its native pixels
                        // — the width setting must visibly do what it says,
                        // and sliced tall pages already fill the column, so a
                        // native cap here left mixed widths in one chapter.
                        // Crispness is handled where the real defect was:
                        // sized decodes + texture-limit slicing.
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (aspect != null && aspect > 0f) Modifier.aspectRatio(aspect)
                                    else Modifier.height(placeholderHeight),
                                ),
                        ) {
                            val rawModel = seg.pages.getOrNull(item.pageIndex)
                            val effectiveModel = if (attempt > 0 && rawModel is String) {
                                rawModel + (if (rawModel.contains('?')) "&" else "?") + "retry=" + attempt
                            } else {
                                rawModel
                            }
                            if (isTall) {
                                // Over the GPU texture limit: a single bitmap
                                // would be power-of-two downsampled at upload
                                // (720 wide becomes 360). CPU-decode once,
                                // slice, and stack — every slice uploads as an
                                // ordinary texture (Mihon's webtoon approach).
                                var pageSlices by remember(cacheKey, attempt) {
                                    mutableStateOf<PageSlices?>(null)
                                }
                                LaunchedEffect(cacheKey, attempt) {
                                    val sliced = TallPageLoader.load("$cacheKey:$attempt", effectiveModel)
                                    settled = true
                                    if (sliced != null) {
                                        loadFailed[cacheKey] = false
                                        loadedAspect[cacheKey] = sliced.width.toFloat() / sliced.height.toFloat()
                                        loadedWidth[cacheKey] = sliced.width
                                        onImageLoaded(sliced.width, sliced.height)
                                        pageSlices = sliced
                                    } else {
                                        loadFailed[cacheKey] = true
                                    }
                                }
                                val sliced = pageSlices
                                if (sliced != null) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        sliced.slices.forEach { slice ->
                                            androidx.compose.foundation.Image(
                                                bitmap = slice,
                                                contentDescription = "Page ${item.pageIndex + 1}",
                                                contentScale = ContentScale.FillWidth,
                                                filterQuality = FilterQuality.Medium,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            } else {
                            AsyncImage(
                                model = pageModel(effectiveModel, targetWidthPx = stripTargetPx),
                                contentDescription = "Page ${item.pageIndex + 1}",
                                contentScale = ContentScale.FillWidth,
                                // Web-parity sharpness: Skia's default Low
                                // filtering (linear, no mipmaps) pixelates any
                                // scaled page; the browser resamples properly.
                                filterQuality = FilterQuality.Medium,
                                onSuccess = { success ->
                                    settled = true
                                    loadFailed[cacheKey] = false
                                    val w = success.result.image.width
                                    val h = success.result.image.height
                                    if (w > 0 && h > 0) {
                                        if (HubPlatform.isDesktop && h > TEXTURE_SAFE_HEIGHT_PX) {
                                            // Reported dims can already be the
                                            // halved ones — don't feed them to
                                            // the clamp; the slicer measures
                                            // the real size on the CPU.
                                            tallPages[cacheKey] = true
                                        } else {
                                            loadedAspect[cacheKey] = w.toFloat() / h.toFloat()
                                            loadedWidth[cacheKey] = w
                                            onImageLoaded(w, h)
                                        }
                                    }
                                },
                                onError = {
                                    settled = true
                                    loadFailed[cacheKey] = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            }
                            if (failed) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable {
                                        loadFailed[cacheKey] = false
                                        retryTick[cacheKey] = attempt + 1
                                    },
                                ) {
                                    Text(
                                        "Page ${item.pageIndex + 1} didn't load",
                                        fontSize = 13.sp,
                                        color = ReaderPalette.Text70,
                                    )
                                    Text(
                                        "Tap to retry",
                                        fontSize = 12.sp,
                                        color = RenzoColors.Primary,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            } else if (!settled) {
                                CircularProgressIndicator(
                                    color = ReaderPalette.Text35,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        }
                    }

                    // The strip is only as wide as the pages (native cap ×
                    // zoom); the divider and end cells are viewport-wide
                    // sections on the web, and squeezing their button rows
                    // into a narrow strip wraps the labels one letter per
                    // line — requiredWidth breaks out of the strip's
                    // constraint and centers them on the screen.
                    item.kind == KIND_DIVIDER && seg != null -> ChapterDivider(
                        finishedLabel = segments.getOrNull(item.segIndex - 1)?.name ?: "",
                        nextLabel = seg.name,
                        modifier = Modifier.requiredWidth(screenWidth),
                    )

                    else -> {
                        val last = segments.lastOrNull()
                        // Full-screen like the web's sentinel page: guaranteed scroll
                        // distance below the final image is what lets the tracker
                        // register the last page and mark the chapter read.
                        Box(modifier = Modifier.requiredWidth(screenWidth).fillParentMaxHeight()) {
                            EndOfChapter(
                                chapterLabel = last?.name ?: "",
                                nextLabel = state.nextChapterName,
                                hasNext = state.hasNext,
                                hasPrev = state.hasPrev,
                                infinite = settings.infiniteScroll,
                                onNext = onNextChapter,
                                onPrev = onPrevChapter,
                                onExit = onExit,
                                appendError = state.appendError,
                                appending = state.appending,
                                onRetryAppend = onRetryAppend,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Paged (paged / paged-rtl / double) ────────────────────────────────────

@Composable
private fun PagedReader(
    state: ReaderUiState,
    mode: ResolvedMode,
    isTv: Boolean,
    nav: ReaderNavHandle,
    seekTarget: Int?,
    onSeekHandled: () -> Unit,
    onPosition: (Int, Int) -> Unit,
    onImageLoaded: (Int, Int) -> Unit,
    onPrefetchNext: () -> Unit,
    onToggleChrome: () -> Unit,
    onNextChapter: () -> Unit,
    onPrevChapter: () -> Unit,
    onExit: () -> Unit,
) {
    val settings = state.settings
    val seg = state.segments.firstOrNull() ?: return
    val pageCount = seg.pageCount
    val doubled = mode == ResolvedMode.DOUBLE
    val contentSlots = if (doubled) (pageCount + 1) / 2 else pageCount
    val slotCount = contentSlots + if (settings.chapterTransition) 1 else 0
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = if (doubled) state.resumePage / 2 else state.resumePage,
    ) { slotCount.coerceAtLeast(1) }

    // Report the page under the reader; the transition slot isn't a real page.
    LaunchedEffect(pagerState, contentSlots) {
        snapshotFlow { pagerState.currentPage }.collect { slot ->
            if (slot < contentSlots) {
                val page = if (doubled) (slot * 2 + 1).coerceAtMost(pageCount - 1) else slot
                onPosition(0, page)
                // Two pages from the end, start resolving the next chapter so
                // the turn is instant instead of a spinner. Same distance as
                // the continuous reader, and equally nothing is fetched for a
                // reader who stops before then.
                if (pageCount - 1 - page <= PAGED_PREFETCH_PAGES) onPrefetchNext()
            }
        }
    }
    LaunchedEffect(seekTarget) {
        val target = seekTarget ?: return@LaunchedEffect
        pagerState.scrollToPage((if (doubled) target / 2 else target).coerceIn(0, slotCount - 1))
        onSeekHandled()
    }

    // Preload upcoming pages (web: an invisible <img> per preload slot).
    val platformContext = LocalPlatformContext.current
    val imageLoader = SingletonImageLoader.get(platformContext)
    LaunchedEffect(pagerState.currentPage, settings.preload, seg.key) {
        val from = if (doubled) pagerState.currentPage * 2 + 1 else pagerState.currentPage
        for (i in 1..settings.preload) {
            val next = from + i
            if (next in 0 until pageCount) {
                val model = seg.pages.getOrNull(next) ?: continue
                imageLoader.enqueue(ImageRequest.Builder(platformContext).data(model).build())
            }
        }
    }

    /** The web's `advance`: page turns stay within the chapter; the ends step chapters. */
    fun advance(direction: Int) {
        val slot = pagerState.currentPage
        if (direction > 0) {
            if (slot >= slotCount - 1) {
                // On the last slot: the transition screen carries into the next
                // chapter, and without one the last page does it directly.
                onNextChapter()
                return
            }
            scope.launch { pagerState.animateScrollToPage(slot + 1) }
        } else {
            if (slot <= 0) return
            scope.launch { pagerState.animateScrollToPage(slot - 1) }
        }
    }

    // Scale can make a page larger than the screen, so each slot's panning has
    // to be reachable from outside the pager — the D-pad drives it. Hoisted per
    // slot rather than remembered inside the page: the arrows act on the page
    // that is on screen, and the next page must start at its own top-left.
    // Deliberately a plain map, not a snapshot map: it is written to during
    // composition, and an observable write there invalidates the scope that
    // just read it — which is a recomposition loop, not a cache.
    val pageScrolls = remember(seg.key) { mutableMapOf<String, ScrollState>() }
    fun scrollFor(id: String): ScrollState = pageScrolls.getOrPut(id) { ScrollState(0) }
    val scale = (settings.scalePct / 100f).coerceIn(0.5f, 3f)
    val density = LocalDensity.current
    val screenH = screenHeightDp()
    val vStepPx = with(density) {
        (screenH * (settings.tapAdvancePct / 100f)).toPx()
    }
    val screenW = screenWidthDp()
    val hStepPx = with(density) { (screenW * 0.35f).toPx() }

    // Whether the page on screen actually has a scroller in each axis — see
    // PageImage for which fit attaches what. This has to be decided from the
    // settings rather than from the state: a ScrollState that was never
    // attached to a layout still reports maxValue = Int.MAX_VALUE, so asking
    // it "can you scroll?" answers yes and the page would never turn.
    val vScrollable = !doubled && when (settings.fit) {
        FitMode.HEIGHT -> scale > 1.001f || scale < 0.999f
        FitMode.WIDTH, FitMode.ORIGINAL -> true
    }
    val hScrollable = !doubled && when (settings.fit) {
        FitMode.HEIGHT -> scale > 1.001f || scale < 0.999f
        FitMode.WIDTH -> scale > 1.001f
        FitMode.ORIGINAL -> true
    }

    // D-pad: pan the enlarged page to its edge first, then turn. Anything else
    // makes half of a scaled page unreachable, and "the page moved" is a much
    // better answer to an arrow press than nothing happening.
    SideEffect {
        nav.onDpad = { dir ->
            val slot = pagerState.currentPage
            val v = scrollFor("$slot:v")
            val h = scrollFor("$slot:h")
            // The chapter-transition slot holds no page, so nothing there pans.
            val onPage = slot < contentSlots
            when (dir) {
                DpadDir.DOWN ->
                    if (onPage && vScrollable && v.canScrollForward) scope.launch { v.animateScrollBy(vStepPx) }
                    else advance(1)
                DpadDir.UP ->
                    if (onPage && vScrollable && v.canScrollBackward) scope.launch { v.animateScrollBy(-vStepPx) }
                    else advance(-1)
                DpadDir.RIGHT ->
                    if (onPage && hScrollable && h.canScrollForward) scope.launch { h.animateScrollBy(hStepPx) }
                    else advance(if (mode.rtl) -1 else 1)
                DpadDir.LEFT ->
                    if (onPage && hScrollable && h.canScrollBackward) scope.launch { h.animateScrollBy(-hStepPx) }
                    else advance(if (mode.rtl) 1 else -1)
            }
            Unit
        }
    }
    DisposableEffect(nav) { onDispose { nav.onDpad = null } }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (mode.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { slot ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(settings.tapNavigation, mode, slotCount) {
                        detectTapGestures { offset ->
                            if (!settings.tapNavigation) {
                                onToggleChrome()
                                return@detectTapGestures
                            }
                            val x = offset.x / size.width.toFloat()
                            when {
                                x < 0.3f -> advance(if (mode.rtl) 1 else -1)
                                x > 0.7f -> advance(if (mode.rtl) -1 else 1)
                                else -> onToggleChrome()
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (slot >= contentSlots) {
                    // Chapter transition — its own page.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        ChapterTransition(
                            finishedLabel = seg.name,
                            nextLabel = state.nextChapterName,
                            hasNext = state.hasNext,
                            hasPrev = state.hasPrev,
                            onNext = onNextChapter,
                            onPrevPage = {
                                scope.launch {
                                    pagerState.animateScrollToPage((contentSlots - 1).coerceAtLeast(0))
                                }
                            },
                            onPrevChapter = onPrevChapter,
                            onExit = onExit,
                        )
                    }
                } else if (doubled) {
                    // Desktop shows the pair side by side; on a phone it stacks.
                    // A television is a desktop-shaped screen — stacking two
                    // pages down a 16:9 panel is the worst of both.
                    if (isTv) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            for (p in slot * 2..(slot * 2 + 1)) {
                                if (p >= pageCount) continue
                                Box(modifier = Modifier.fillMaxHeight().weight(1f)) {
                                    PageImage(
                                        model = seg.pages.getOrNull(p),
                                        index = p,
                                        fit = settings.fit,
                                        onImageLoaded = onImageLoaded,
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            for (p in slot * 2..(slot * 2 + 1)) {
                                if (p >= pageCount) continue
                                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                    PageImage(
                                        model = seg.pages.getOrNull(p),
                                        index = p,
                                        fit = settings.fit,
                                        onImageLoaded = onImageLoaded,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    PageImage(
                        model = seg.pages.getOrNull(slot),
                        index = slot,
                        fit = settings.fit,
                        scale = scale,
                        vScroll = scrollFor("$slot:v"),
                        hScroll = scrollFor("$slot:h"),
                        onImageLoaded = onImageLoaded,
                    )
                }
            }
        }
    }
}

/**
 * Desktop page decodes are sized to what is actually DRAWN, in PHYSICAL
 * pixels (HANDOFF_renzo-hub_reader-image-quality.md). Two prior attempts both
 * pixelated for opposite reasons: Coil's default layout-bounds sizing is in
 * dp-derived px that undershoot a scaled Windows display (soft), and a
 * Size.ORIGINAL decode made Skia minify a ~1500px page into a ~600px column
 * every frame — cubic (High) minification without mipmaps is aliasing. With
 * decode ≈ drawn size nothing is resampled in the common case. Targets are
 * quantised (ceil to 128px, ~1.25× headroom) so zoom steps reuse cache
 * entries instead of re-decoding per step. null/null = Size.ORIGINAL, which
 * only the original-size fit mode wants — intrinsic pixels are its point.
 * Android keeps Coil's own sizing (drawn ~1:1 there already).
 */
@Composable
private fun pageModel(
    model: Any?,
    targetWidthPx: Int? = null,
    targetHeightPx: Int? = null,
): Any? {
    if (model == null || !HubPlatform.isDesktop) return model
    val ctx = LocalPlatformContext.current
    return remember(model, targetWidthPx, targetHeightPx) {
        val builder = ImageRequest.Builder(ctx).data(model)
        when {
            targetWidthPx != null -> builder.size(
                coil3.size.Size(coil3.size.Dimension.Pixels(targetWidthPx), coil3.size.Dimension.Undefined),
            )
            targetHeightPx != null -> builder.size(
                coil3.size.Size(coil3.size.Dimension.Undefined, coil3.size.Dimension.Pixels(targetHeightPx)),
            )
            else -> builder.size(coil3.size.Size.ORIGINAL)
        }
        builder.build()
    }
}

/** Headroom (~1.25×) then ceil to a 128px step — see [pageModel]. */
private fun quantizePagePx(px: Int): Int =
    (((px * 1.25f).toInt() + 127) / 128) * 128

/**
 * One page honouring the Page fit setting (fit width / fit height / original
 * size) and the page scale.
 *
 * Scale is applied by making the page's LAYOUT bigger rather than by drawing it
 * transformed: a `graphicsLayer` scale would clip at the viewport with no way
 * to reach what fell outside it, where a larger layout inside a scroller pans.
 * At 100% every branch collapses to exactly what it was before scale existed,
 * which is what keeps touch untouched.
 *
 * [vScroll] / [hScroll] are hoisted so the D-pad can pan from outside; passing
 * nothing keeps the page's panning private, which is what the double-page
 * layout wants (two pages per slot, each scrolling on its own).
 */
@Composable
private fun PageImage(
    model: Any?,
    index: Int,
    fit: FitMode,
    scale: Float = 1f,
    vScroll: ScrollState? = null,
    hScroll: ScrollState? = null,
    onImageLoaded: (Int, Int) -> Unit,
) {
    // Always remembered, never conditionally: a composable call that appears
    // and disappears with a parameter corrupts the slot table.
    val ownV = rememberScrollState()
    val ownH = rememberScrollState()
    val v = vScroll ?: ownV
    val h = hScroll ?: ownH
    val enlarged = scale > 1.001f
    val resized = enlarged || scale < 0.999f
    val density = LocalDensity.current
    val screenSize = app.renzoshiori.client.ui.util.screenSizeDp()
    val screenWidth = screenSize.width
    val screenHeight = screenSize.height
    // Decode targets in PHYSICAL px for the fit modes that draw at a viewport
    // fraction — see pageModel. Original-size mode keeps intrinsic pixels.
    val containerPx = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val fitWidthTargetPx = quantizePagePx((containerPx.width * scale).toInt())
    val fitHeightTargetPx = quantizePagePx((containerPx.height * scale).toInt())
    // Captured once for onImageLoaded's aspect bookkeeping. No native-size
    // clamp here any more: the fit modes must visibly match the reader
    // settings (web parity — the browser scales pages to the fit box), and
    // the actual quality defect was fixed at its cause (sized decodes +
    // texture-limit slicing), not by refusing to scale.
    var natural by remember(model) { mutableStateOf<Pair<Int, Int>?>(null) }

    val onSuccess: (AsyncImagePainter.State.Success) -> Unit = { success ->
        val w = success.result.image.width
        val h2 = success.result.image.height
        if (w > 0 && h2 > 0) {
            // Captured once, deliberately. Coil sizes its request from the
            // layout bounds, so a second capture would report the size we just
            // asked for and scale would compound on itself every load.
            if (natural == null) natural = w to h2
            onImageLoaded(w, h2)
        }
    }
    when (fit) {
        FitMode.HEIGHT -> if (!resized) {
            AsyncImage(
                model = pageModel(model, targetHeightPx = fitHeightTargetPx),
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.Medium,
                onSuccess = onSuccess,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(v)
                    .horizontalScroll(h),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = pageModel(model, targetHeightPx = fitHeightTargetPx),
                    contentDescription = "Page ${index + 1}",
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.Medium,
                    onSuccess = onSuccess,
                    modifier = Modifier.size(screenWidth * scale, screenHeight * scale),
                )
            }
        }

        FitMode.WIDTH -> Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(v)
                .then(if (enlarged) Modifier.horizontalScroll(h) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = pageModel(model, targetWidthPx = fitWidthTargetPx),
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.FillWidth,
                filterQuality = FilterQuality.Medium,
                onSuccess = onSuccess,
                modifier = if (resized) Modifier.width(screenWidth * scale) else Modifier.fillMaxWidth(),
            )
        }

        FitMode.ORIGINAL -> Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(v)
                .horizontalScroll(h),
            contentAlignment = Alignment.Center,
        ) {
            // "Original size" means intrinsic pixels, so there is no viewport
            // fraction to scale — the size has to come from the decoded image,
            // which is only known once it has loaded.
            val dims = natural
            val sized = resized && dims != null
            AsyncImage(
                model = pageModel(model),
                contentDescription = "Page ${index + 1}",
                contentScale = if (sized) ContentScale.Fit else ContentScale.None,
                filterQuality = FilterQuality.Medium,
                onSuccess = onSuccess,
                modifier = if (sized && dims != null) {
                    with(density) {
                        Modifier.size((dims.first * scale).toDp(), (dims.second * scale).toDp())
                    }
                } else {
                    Modifier
                },
            )
        }
    }
}
