# Re: Renzo Shiori on TV — implemented, and what the port needs

**From:** the Shiori chat (`/opt/zurg-stack/renzo-ecosystem/shiori/clients/android`).
**Re:** `SHIORI-TV-COMPAT.md` and `TV-PAIRING-SHIORI.md`.
**Date:** 2026-08-04.
**Status:** both built and committed on `Levitate0/Renzo-Shiori` (`main`).
Pairing is **deployed and tested against the live server**; the D-pad work is
**compile-verified only — no D-pad has touched it** (§6 below).

---

## 1. Companion doc first: TV pairing is live

The server half shipped before this. `POST /api/auth/tv/{code,poll,approve,deny}`
plus `GET /api/auth/tv/pending`, an approval page at `/tv`, and a remembered-
devices list with per-device revoke in account settings.

It returns exactly what a `rememberMe` login returns — access token, user, and
the `refresh_token` cookie — so a paired TV stays signed in for the full
remember-me window, as you specified.

**Two things there that matter to the Hub beyond pairing:**

- **The spec had a blocker underneath it.** A user had exactly ONE refresh token
  (a column on the user row), so a second remembered sign-in silently evicted the
  first — a paired TV would have been kicked off by the next phone login.
  Sessions are now per-device rows (`RefreshSessions`), rotated in place, revoked
  individually. `TvPairingClient` needs no change; this just makes it work.
- **A pre-existing 500.** `ValidateRefreshToken` ran `Convert.FromBase64String`
  on caller input, so a corrupted refresh cookie threw and `/api/auth/refresh`
  answered 500 instead of 401 — triggerable by anyone. Fixed. If the Hub has its
  own equivalent, check it.

Details in `HANDOFF_renzo-hub_session-and-auth.md` (`/opt/zurg-stack/`).

## 2. What landed for D-pad

### Foundation — `ui/tv/`

`TvFocus.kt`: `LocalIsTv`, `isTvDevice()`/`rememberIsTvDevice()`, `focusRing`,
`tvClickable`, `tvFocusable`, `tvContentColor`, `rememberFocusState`.
`TvComponents.kt`: `TvSelectedMark`, `TvOptionRow`, `TvUseAComputerScreen`.

§2.1 is encoded in `tvContentColor` rather than left to call sites: **colour
carries selection, ring and fill carry focus.** Selected rows also get a check —
colour alone fails for colour-blind viewers and washes out on a badly calibrated
panel, as you noted.

`MainActivity` provides `LocalIsTv` around the whole app.

### Manifest

Leanback launcher category on the existing activity, `android.software.leanback`
and `android.hardware.touchscreen` both **not-required**, `android:banner`, and a
`<queries>` entry for `android.speech.action.RECOGNIZE_SPEECH` (§4 — without it
package-visibility filtering makes `resolveActivity` return null on API 30+ and
the mic never appears, even on sets that have a recogniser).

Verified in the built manifest: `leanback-launchable-activity` present, both
features `uses-feature-not-required`.

### Reader — `ui/reader/ReaderTv.kt` (new) + `ReaderScreen/Sheets/Prefs`

- D-pad key sink: continuous scrolls by `tapAdvancePct`; paged turns pages
  RTL-aware; an enlarged page **pans to its edge and then turns**.
- OK reveals the chrome and lands the cursor on **Reader settings** — one press,
  per §7. BACK hides chrome, BACK again exits. Chrome visible ⇔ chrome focused,
  so arrows are never ambiguous.
- Sheets → `ReaderTvPanel`, a focus-trapping full-screen dialog, on TV only;
  touch keeps `ModalBottomSheet`. Chapter list likewise, opening focused on the
  chapter being read.
- **Sliders became steppers**: left/right adjust by the declared step, up/down
  are left unconsumed so focus can leave, value announced in the label.
- `tapNavigation` and `autoClearCache` are **hidden** on TV, not disabled.
- TV control order: scale → fit → page width → mode → background → scroll step.

### Reader scale (§6) — the gap you identified

New `scalePct` in the existing `renzo_reader_settings` blob, 50–300 step 10,
applied in both renderers, **defaulting to 130% on TV** (and fit=height). Device-
local, so a TV keeps its own values.

### Library / browse / add-series — `ui/components/TvSearchBar.kt` (new)

`rememberVoiceSearch(prompt, onTranscript)` returns **null when no recogniser
resolves**, so the mic affordance is absent rather than throwing. Transcript
fills the field, runs the search, and **stays editable** — treated as a query,
never a selection, exactly as §4 requires. `TvSearchBar` is `BasicTextField` in a
`decorationBox` with `ImeAction.Search`.

Focus rings across grids, ribbon controls, tag dialog, spotlight, and the whole
two-stage Add Series flow. On TV `RibbonSelect`'s `DropdownMenu` becomes a
focus-trapping dialog. Library and browse default to card size **L** on TV.

### Series detail

Its ~70 targets funnel through five shared primitives (`RenzoChip`,
`PillToggle`, `SquareIconButton`, plus the row and dialog helpers), so focus went
in once rather than seventy times. `PillToggle` also gained a tick on TV — its
6dp state dot is unreadable across a room.

### Shell and small screens — `ui/home/TvChrome.kt` (new)

Persistent **232dp nav rail** replaces the drawer on TV (active section keeps
accent + bar + check while the cursor moves). Auth autofocuses its first field
with proper IME actions; user-select is the no-typing path and gets first focus.
Updates/queue/downloads/status/onboarding rows and chips ring; queue's filter and
status's Sources|Series stay marked as selection.

**Out-of-scope screens are replaced, not disabled** (§3): settings, sources,
users, appearance, trackers, account and the import wizard render
`TvUseAComputerScreen` with the instance's own address on TV.

## 3. Things the port must carry over

1. **`HubForeground.leavingApp()` is deliberately absent.** It doesn't exist in
   this client. There is a comment at the launch site in `TvSearchBar.kt`; wrap
   `launcher.launch(intent)` with it in the Hub.
2. **The manifest `<queries>` block** must exist in the Hub's manifest too, or
   voice search silently never appears.
3. **`ui/components/TvFocusTarget.kt` is now just an alias** for
   `tv.tvFocusable` and can be deleted once its call sites migrate.
4. **Two search entry points exist on TV**: the shell's command-bar field and the
   in-screen `TvSearchBar` on library/browse. They share `LibraryViewModel` and
   stay in sync. If the Hub prefers one, drop the shell's on TV — nothing else
   changes.

## 4. Bugs found while integrating

- **`tvFocusable` hid its own focus ring.** It applied the opaque fill *after*
  the ring; modifiers draw outermost-first, so the fill painted over the 3dp
  border and focus was invisible on exactly the row that had it. Fixed centrally.
  Worth checking `tv-native`'s equivalent for the same ordering.
- **A detached `ScrollState` reports `maxValue = Int.MAX_VALUE`**, so
  `canScrollForward` answers true and a paged reader would never turn the page.
  The paged D-pad path decides scrollability from fit/scale settings first and
  only then asks the state.
- **The downloads folder picker could crash on TV** — many sets have no
  document-tree picker. Now guarded.

## 5. Deliberately left out

- **Scale in double-page mode** — the two halves share a slot and the mode's
  premise is "fit two pages on screen"; it gets a side-by-side TV layout instead.
- **Buttons inside the reading surface** (end-of-chapter "Next chapter", a failed
  page's retry) are not D-pad focusable, because the key sink consumes arrows
  while reading. Both have chrome equivalents.
- **Back at the top of a page** doesn't step into the previous chapter — it
  stops, matching the touch tap zone.
- **Sources' drag-reorder** — out of scope per §5, and the arrows already work.
- **No APK was built.** Android delivery is the Hub's; these are sources to port.

## 6. What "done" honestly means here

Against your §7 checklist: focus rings, focus-vs-selection separation, one-press
reader settings, D-pad-operable sliders, hidden-not-disabled irrelevant controls,
no typing except search, search accepts voice, and out-of-scope screens showing
the web address — **all implemented and compiling**.

**Not verified:** anything requiring a real remote. Nobody has pressed a D-pad
against this. The things I'd test first, in order:

1. Focus traps in dialogs (approve/deny, delete confirms, the reader panels) —
   getting in is easy, getting out is where these fail.
2. Whether the reader's key sink ever swallows an arrow the user wanted for
   navigation.
3. Grid scroll-into-view: it relies on `focusable()`'s built-in behaviour rather
   than an explicit `BringIntoViewRequester` (still experimental in this Compose
   BOM), so a focused tile near a viewport edge is the case to watch.
4. Whether 130% is actually the right TV scale default, or whether it should be
   higher.
