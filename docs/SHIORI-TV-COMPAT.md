# Renzo Shiori on TV — compatibility spec

**For:** the Shiori chat (`/opt/zurg-stack/renzo-ecosystem/shiori/clients/android`).
**Date:** 2026-08-04.
**Companion:** `TV-PAIRING-SHIORI.md` (sign-in without typing — build that first).

Make the reading and discovery half of Shiori usable from a D-pad. Explicitly
**not** the configuration half. Scope and reasoning below; the measurements are
from the current tree.

---

## 1. It is closer than it looks

`Modifier.clickable` is **focusable by default** and D-pad centre activates it.
Every one of Shiori's ~356 `clickable`/`onClick` sites is already *reachable*.
The problem is not reachability — it is that:

1. **You cannot see where focus is.** Material3's ripple renders a focus state
   that is legible at arm's length and invisible across a room.
2. **Traversal order** is whatever the layout implies, which is wrong for
   overlays and two-dimensional content.
3. **Focus can scroll off-screen** in containers that don't bring it into view.
4. A few components have no D-pad story at all (§5).

So this is mostly "make focus visible and ordered", not a rewrite.

## 2. The mechanism already exists, and it is 15 lines

`tv-native` solved this once and uses it in 126 places. Copy the pattern
verbatim from `renzo-clients/tv-native/.../ui/components/Common.kt`:

```kotlin
fun Modifier.focusRing(focused: Boolean, radius: Dp = 10.dp): Modifier =
    if (focused) border(3.dp, Accent, RoundedCornerShape(radius)) else this

@Composable
fun Modifier.tvClickable(onFocused: (Boolean) -> Unit, onClick: () -> Unit): Modifier { … }
```

A visible ring plus a focus callback. Applying it is mechanical; the judgement
is in traversal order and scroll-into-view, not in the helper.

**Do not fork the UI.** One composable per screen, branching on a device-class
flag where behaviour genuinely differs (focus ring, tap-zones vs D-pad, sheet vs
full-screen). Two parallel screen trees will diverge within a month.

### 2.1 Focused and selected are two different things — show both

This is the single most common way TV UIs go wrong, and it is not solved by the
focus ring alone. At any moment a screen has:

- **Focused** — where the D-pad cursor is. Moves constantly. Exactly one per
  screen.
- **Selected / active** — what is currently in effect: the open tab, the chapter
  being read, the current reading mode, the chosen background. Does **not** move
  when focus does, and must stay visible while the user navigates elsewhere.

Conflating them means the user loses track of what they have actually chosen the
moment they move the stick. `tv-native` keeps them on separate visual channels
and that is the pattern to copy:

```kotlin
val fg = when {
    selected -> Accent            // colour = what is active
    focused  -> Foreground        // brightness = where the cursor is
    else     -> MutedForeground
}
Row(
    Modifier
        .focusRing(focused, 10.dp)                                   // ring   = focus
        .background(if (focused) Surface else Color.Transparent, …)  // fill   = focus
        .tvClickable(onFocused = { focused = it }, onClick = onClick),
)
```

**Colour carries selection; ring and fill carry focus.** Both can be true at
once and must remain distinguishable when they are.

Apply this anywhere a current value exists: the nav rail, reading-mode and
fit-mode option rows, background choice, the chapter list (current chapter), the
library's active filter, and the browse source tabs. A checkmark or a leading
indicator on the selected row is worth adding on top of colour — colour alone is
a problem for colour-blind users and washes out on a poorly-calibrated TV.

Also: disabled and pressed states need to stay distinct from both. A disabled row
that merely looks unfocused reads as "I haven't got there yet" rather than
"this cannot be used".

## 3. Scope

**In:**

| Area | clicks | fields | Notes |
|---|---|---|---|
| auth | 13 | 7 | Pairing + user-select covers it; see companion doc |
| home | 20 | 2 | shell + account menu |
| library | 12 | 0 | grid — the easy win |
| browse / discover | 32 | 4 | includes add-series; see §4 |
| series detail | 70 | 10 | biggest in-scope item |
| reader | 27 | 2 | the point of the exercise; see §5 |
| updates | 5 | 0 | |
| queue | 10 | 0 | |
| downloads | 4 | 0 | |
| status | 10 | 2 | |
| onboarding | 7 | 0 | |

**Out — deliberately:** `settings` (67 clicks, **16** text fields), `sources`
(36, 4, but those four are extension-repository URLs), `importwizard` (35, 4).

These are configuration: typed, long, error-prone, and done rarely. They are
where TV UX dies, and they are not what someone reaches for a remote to do.

**Instead of porting them**, show the instance's own web address on TV —
"manage sources at `http://your-server/sources`". Any household computer or
tablet works; it does not assume the user owns a phone, which was the flaw in
the obvious advice.

## 4. Discovery and adding a series — D-pad, typing, and voice

This is the one in-scope area that genuinely needs text entry, so it gets the
most attention.

**Typing.** `BasicTextField` with `KeyboardOptions(imeAction = ImeAction.Search)`
and a `KeyboardActions(onSearch = …)` is what `tv-native` uses today
(`ui/screens/SearchScreen.kt`, `ui/components/TopBar.kt`) and it works with the
leanback IME. Copy that shape rather than inventing one: a bordered box, the
field inside a `decorationBox`, submit on the IME Search action.

**Voice.** Not built on either half today — this is new work.

```kotlin
val recognizer = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
) { result ->
    result.data
        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        ?.firstOrNull()
        ?.let { onQuery(it) }
}
```

launched with `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`,
`EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_FREE_FORM`, and an `EXTRA_PROMPT`.

Three things that will bite:

- **Not every TV has a recognizer.** Resolve the intent first and hide the mic
  affordance when nothing handles it, rather than showing a button that throws.
- **Titles are the worst case for speech.** Romanised Japanese comes back
  mangled far more often than English. Treat the transcript as a *query*, never
  as a selection — always land on results the user confirms with the D-pad, and
  keep the transcript editable in the text field so it can be corrected without
  starting over.
- **In the Hub only:** launching the recogniser backgrounds the activity, and
  the Hub returns to its app picker on foreground. Wrap the launch with
  `HubForeground.leavingApp()`. That call does not exist in the standalone
  client and is not needed there — flag it at the port.

**Adding a series** (`browse/AddSeriesSheet.kt`) is in scope and is *not* a
`ModalBottomSheet`, so it needs focus work but no structural change.

## 5. The genuine blockers, and there are only two

Of three `ModalBottomSheet` uses, two are in `settings` and `sources` — out of
scope. In-scope blockers:

1. **`reader/ReaderSheets.kt`** — the reader's settings/chapter-list sheets.
   Sheets do not contain focus properly on TV. Present them as a full-screen
   route or a focus-trapping dialog when on a TV.

   **These are in scope and must be fully usable — see §5.1.** "Settings is out
   of scope" means the `ui/settings/` area (account, appearance, users, server,
   trackers). The reader's own settings are not that, and shipping a TV reader
   whose settings cannot be reached would be worse than not shipping one.
2. **`reader/ReaderScreen.kt`** — the pager and `detectDragGestures`. There is
   no pointer to drag with. Map left/right (or up/down in continuous modes) to
   page turns, and make the tap-zone navigation D-pad-driven. The existing
   `tapAdvancePct` setting is the right knob for continuous scroll steps.

`sources/DefaultPriorityOrderTab.kt`'s drag-reorder is out of scope.

### 5.1 Reader settings on TV

`ReaderSettingsSheet` (`ReaderSheets.kt:270`) is the control panel for the one
thing a TV is actually for, so it gets first-class treatment rather than being
tolerated. Reachable from the reader chrome with a single D-pad press, and every
control operable without a pointer.

Which controls matter most on a television, in order:

| Control | Why it matters at couch distance |
|---|---|
| **Scale** (new — §6) | The whole point. Nothing else compensates for viewing distance. |
| `fit` — width / height / original | Fit-height is usually right on a 16:9 panel; fit-width is a phone default that crops badly here. |
| `maxWidthPct` (20–100, step 5) | Already a slider. **Sliders are the hard part** — see below. |
| `mode` | Webtoon/long-strip behave very differently on a landscape screen. |
| `background` | Black vs grey vs white is a real comfort difference in a dark room. |
| `tapAdvancePct` | Becomes the D-pad scroll step in continuous modes. |

**Sliders need explicit D-pad handling.** `maxWidthPct` and scale are the two
that exist. A slider that only responds to drag is dead on a remote. Give the
row focus, then let left/right adjust by the declared `step` and only let focus
leave on up/down. Announce the value in the label as it changes — the existing
`"Page width — ${maxWidthPct}% of screen"` label is already the right shape.

Controls that are irrelevant or misleading on TV should be **hidden, not
disabled**: `tapNavigation` describes touch zones that do not exist, and
`autoClearCache` is a phone-storage concern. A disabled row invites the user to
keep pressing it.

Every option row here is a selection control, so §2.1 applies throughout — the
current mode, fit and background must stay visibly marked while the cursor moves
over the others.

## 6. Reader scale

Reader settings live in device-local SharedPreferences
(`renzo_reader_settings`) and are **not** server-synced, so a TV already keeps
its own values — nothing needed for per-device separation.

What is missing is that **there is no scale control at all**. `fit`
(width/height/original) and `maxWidthPct` are the only sizing options and both
are viewport-relative, so pages cannot be made larger in absolute terms — which
is exactly what couch distance needs. Add an explicit scale, and default it
higher when the device is a TV.

If reader settings are ever server-synced the way resume position now is, they
must be namespaced by device class or a TV will overwrite a phone.

## 7. What "done" looks like

- Every focusable element shows an unmistakable focus ring, readable at 3 m.
- **Focused and selected are separately legible at 3 m**, including when the
  same row is both. Move the cursor off the current reading mode and it is still
  obvious which mode is active.
- D-pad traversal reaches everything on an in-scope screen and never strands
  focus off-screen.
- Reader settings open in one press and every control — sliders included —
  works from the D-pad alone.
- Back always goes somewhere sensible; nothing traps focus.
- No screen in scope requires typing except search — and search accepts voice.
- The out-of-scope screens are either hidden on TV or show the web address
  instead of a broken form. **Do not leave them reachable and unusable.**

## 8. Porting to the Hub

The Hub carries a fork of this client (`hub/feature-shiori`). Build it in your
tree and I will port, as with the session/auth work. Two notes:

- The Hub does not currently route to Shiori on TV at all — leanback goes
  straight into Renzo. That gate opens once this lands.
- `HubForeground.leavingApp()` (§4) is Hub-only.
