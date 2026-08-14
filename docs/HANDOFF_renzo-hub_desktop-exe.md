# Handoff → Renzo Hub: the desktop (.exe) variant

**For:** the chat that builds a Windows Hub build.
**Written:** 2026-08-14, from a read of the Hub modules as they exist today.
**Asked for:** an exe Hub that **looks like the web GUI**, **shows Updates**, and
has **the same offline experience the APK has** — offline reusing the online UI
rather than a separate one.

**Read first, in this order:**

| Document | Why |
|---|---|
| `RENZO-HUB-HANDOFF.md` | module layout, applicationId, branding, toolchain |
| `RENZO-DESKTOP-NATIVE-HANDOFF.md` | the framework decision, video, packaging — **written 2026-08-02, before the Hub existed**, so treat its module plan as superseded by §2 below |
| `renzo-clients/hub/docs/OFFLINE-DESIGN.md` | the offline model this must reuse verbatim |
| `HANDOFF_renzo-hub_session-and-auth.md` | auth, including §7 added 2026-08-14 |

---

## 0. The one-paragraph version

**This is not a UI project.** The Hub already contains the entire Compose UI for
both halves, including an `UpdatesScreen.kt` in each. Compose Multiplatform runs
that same code on the desktop JVM. So the work is (a) splitting the Android
platform calls out behind `expect`/`actual` seams, (b) a Windows offline store,
(c) video, and (d) packaging. Anyone who starts by designing screens has
misread the job.

---

## 1. What actually exists — measured, not assumed

Counted on 2026-08-14 in `/opt/zurg-stack/renzo-clients/hub/`.

**The UI is already written.** `feature-shiori/.../ui/` has 17 screen packages:
`auth, browse, components, downloads, home, importwizard, library, onboarding,
queue, reader, series, settings, sources, status, theme, tv, updates, util`.

**Updates already exists, in both halves:**

- `feature-shiori/.../ui/updates/UpdatesScreen.kt` + `data/network/UpdatesApi.kt`
- `feature-renzo/.../tv/ui/screens/UpdatesScreen.kt`

**Platform coupling is small and concentrated.** Of 70 `.kt` files under
`feature-shiori/.../ui/`, **25 import anything from `android.*`** — the other 45
are already platform-free Compose. The entire coupling surface across `ui/` and
`data/`:

| Import | Count | Desktop answer |
|---|---|---|
| `android.net.Uri` | 11 | `java.net.URI` / `java.io.File` behind a typealias |
| `android.app.Application` | 8 | ViewModel construction — the biggest single refactor |
| `android.content.Intent` | 7 | open-in-browser, share → `java.awt.Desktop` |
| `android.content.Context` | 6 | usually only reaching for prefs or files |
| `android.graphics.BitmapFactory` | 3 | Compose `ImageBitmap` / Coil |
| `Toast`, `Log`, `Base64`, `SharedPreferences`, `Configuration`, `PackageManager`, `UiModeManager`, `RecognizerIntent`, `ActivityNotFoundException` | 1 each | snackbar, slf4j, `java.util.Base64`, `Preferences`, window size, version constant, always-desktop, drop voice search, n/a |

That table is the actual size of the port. It is small because the Android
rewrite was already disciplined about keeping platform calls out of screens.

**Toolchain** (`gradle/libs.versions.toml`) — already favourable:

| | Version | Multiplatform? |
|---|---|---|
| Kotlin | 2.4.10 | yes |
| AGP | 8.13.0 | Android-only module concern |
| Compose BOM | 2026.06.01 | yes |
| **Coil** | **3.5.0** | **yes** — Coil 3 is multiplatform. This is the single luckiest fact here; every screen loads images. |
| OkHttp / Retrofit / kotlinx.serialization | 4.12.0 / 3.0.0 / 1.9.0 | all JVM, unchanged |
| **media3** | **1.7.1** | **no** — Android-only. Video is the exception; see §6. |

**What this replaces:** `Rensaio/clients/windows` — WPF + WebView2, ~1,200 lines
of C#, no UI of its own. Details in `RENZO-DESKTOP-NATIVE-HANDOFF.md` §1.

---

## 2. Module layout — this supersedes the older desktop plan

`RENZO-DESKTOP-NATIVE-HANDOFF.md` §3 proposed a module tree written *before* the
Hub consolidation landed. Do not create a parallel tree. The Hub's four modules
are the source of truth; the desktop build is a **fifth module plus source-set
splits**, not a new project:

```
app/            (Android launcher — unchanged)
app-desktop/    (NEW: Compose Desktop entry point, packaging)
core/           → commonMain + androidMain + desktopMain
feature-shiori/ → commonMain + androidMain + desktopMain
feature-renzo/  → commonMain + androidMain + desktopMain
```

The 45 already-clean UI files move to `commonMain` untouched. The 25 in the
table above get an `expect` declaration in `commonMain` and two `actual`s.

**Do not fork the screens.** A desktop copy of `UpdatesScreen.kt` that drifts
from the Android one is the failure mode this whole structure exists to prevent,
and it is the same rule `clients/android/UI_PARITY.md` sets for the web → Compose
direction.

---

## 3. Updates — the specific ask

The screen exists; the desktop job is to **verify parity and let it run**, not
rebuild. Check it against the web page, which is the source of truth:
`Rensaio/RenzoFrontend/src/app/updates/page.tsx` (464 lines).

**Endpoint:** `GET /api/serie/updates?start=&count=[&viewAll=true]`

> The route really is **`/api/serie`, singular** — `SeriesController` declares
> `[Route("api/serie")]`. Anyone "correcting" it to `/api/series` gets a 404.

**`UpdateFeedItemDto`:** `seriesId`, `seriesTitle`, `thumbnailUrl`, `kind`
(`"seriesAdded"` | `"newChapter"`), `chapterNumber`, `chapterName`, `provider`,
`timestamp`, `read`.

**Behaviours the web page has that a naive list will miss:**

- **`FETCH_LIMIT = 1000`.** The feed is pulled deep on purpose, not paged, so a
  batch release *and* older missed updates are both visible at once.
- **`STACK_THRESHOLD = 5`.** Five or more consecutive `newChapter` entries for
  the same series collapse into one expandable stack — otherwise a 20-chapter
  batch release pushes everything else off the page. This is the feature that
  makes the feed usable; it is not decoration.
- **Date buckets** `today / yesterday / this-week / earlier`, shared with the
  queue page via `components/comp/queue/utils`.
- **Read chapters stay in the feed**, greyed and still clickable — they are not
  filtered out.

Desktop earns one addition the phone cannot have: the window is wide, so the
feed can sit beside something else rather than owning the viewport. Match the
web layout first, then decide.

---

## 4. "Matches the web GUI"

The standing rule (`clients/android/UI_PARITY.md`): screens are **transliterated
from the web component source, not approximated** — copy and paste, but in
Kotlin. The desktop build inherits that, with the web app as the reference and
the Android screen as the already-done translation.

Where desktop legitimately differs — and only here:

- **Hover states exist.** The web UI has them; Android dropped them. Restore
  from the web source rather than inventing.
- **Keyboard.** The reader's rebindable hotkey map is a web feature the phone
  has no use for; desktop should have it.
- **Window chrome and resizing.** Nothing in the web UI assumes a phone; it is
  already responsive. Do not add a desktop-specific layout mode.
- **No bottom nav.** Use the web's navigation, which is what the desktop window
  actually resembles.

---

## 5. Offline must be the online UI — as on the APK

This is the part most likely to be got wrong, so it is stated as a rule:

> **There is no offline UI.** There is the ordinary UI, reading from a local
> source when the server is unreachable.

`docs/OFFLINE-DESIGN.md` §4 is the model: `OfflineLibrary` is the read side, and
on Shiori "the reader's `readPage` path is unchanged" — the reader does not know
whether a page came from disk or the server. Reproduce that on desktop; do not
write an "offline mode" screen.

Two related invariants already established elsewhere, both of which the desktop
build can break by accident:

- **A network failure is not a 401** (`HANDOFF_renzo-hub_session-and-auth.md`
  §4). Being offline must never sign anyone out — that is precisely when the
  downloaded library needs to open.
- **Server-side download jobs and device downloads are different things**
  (`OFFLINE-DESIGN.md` §5). The queue screen shows the former; keep them
  visually distinct.

**What genuinely changes on Windows:** no SAF folder picker (use a native
directory chooser and a plain path), no `StatFs` (use `java.nio.file.FileStore`
for the free-space precheck), and no Wi-Fi-only toggle to enforce — hide it
rather than showing a dead control. The existing WPF client's `RenzoStore.cs` /
`RenzoDownloader.cs` are the prior art for where files land on Windows; read
them before choosing a directory layout, then delete them.

---

## 6. Video — the one real blocker, already answered

media3 is Android-only, so the **Renzo (anime) half cannot simply be
recompiled**. `RENZO-DESKTOP-NATIVE-HANDOFF.md` §6 works this through in detail
— VLCJ for embedded playback, VTT rendered by us rather than by the player,
external-player support, and why the subtitle question is already settled. It
predates the Hub but the reasoning is unaffected.

**Recommendation: ship the Shiori half first.** Everything in §1–§5 applies to
it with no unsolved problems, and it is the half with an existing exe to
replace. The Renzo half is gated behind a player integration that is the single
most fragile piece in either client. Shipping a Hub exe whose picker offers one
working half beats delaying both on VLCJ.

That is a scope decision for the user, not for the implementing chat — flag it
early rather than discovering it at packaging time.

---

## 7. Packaging and release

The existing pipeline is not Compose-shaped, but it is proven, and its outputs
are what the release process expects:

- **NSIS** installer (`renzoshiori-installer.nsi`) → `/export/Main/Renzo-out/RenzoShiori-Setup.exe`
- **Authenticode-signed** with `osslsigncode` using
  `/export/Main/Renzo-Apps/signing/codesign.pfx`

Compose Desktop's own `packageExe`/`jpackage` path produces a different artifact
shape (a bundled JRE). Decide deliberately which one survives, and keep signing
either way — an unsigned exe gets a SmartScreen warning on every install.

**Release policy** (see the `release-policy-auto-publish` note): as of
2026-08-04 releases are **Windows installer only** — no APKs, because Android
ships from the Hub. So this exe *is* the release artifact, and a new build is
expected to be published as a GitHub release with a changelog, assets = exe +
`SHA256SUMS`.

---

## 8. Suggested sequencing

1. `app-desktop` module that launches an empty Compose window against the Hub's
   existing `:core`. Proves the toolchain before anything is refactored.
2. Split `:core` into `commonMain`/`androidMain`/`desktopMain`; make auth,
   networking and settings compile for desktop. **Android must still build at
   every step** — if it doesn't, the fork has already happened.
3. Move the 45 platform-free Shiori screens to `commonMain`. Expect this to be
   dull, which is the point.
4. Seams for the 25 remaining files, in the order of the §1 table.
5. Updates, library, series, reader running against a live server.
6. Offline store on Windows, per §5.
7. Packaging and signing.
8. Renzo half + VLCJ, only if scoped in.

---

## 9. Facts worth re-checking before trusting this

Every number here was measured on 2026-08-14 and will drift:

- The 25/70 platform-coupling count — re-run
  `grep -rlE "^import android\." feature-shiori/src/main/java/.../ui/ --include=*.kt`.
  Note the `\.` — plain `^import android` also matches `androidx` and reports
  near-total coupling, which is wrong and alarming.
- Whether `feature-renzo` has a comparably clean split. **It was not measured**;
  only the Shiori half was. Do this before committing to a Renzo-half estimate.
- Compose Multiplatform's support for the specific Compose BOM in use — the Hub
  is on a recent BOM and CMP tracks it with a lag.
- `renzo-clients/hub/` **is not a git repository** as of this writing. Anything
  built there is unprotected; `git init` before starting.

---

## Related documents

| Document | For |
|---|---|
| `RENZO-HUB-HANDOFF.md` | the chat building the Android APK (Renzo Hub) |
| `RENZO-DESKTOP-NATIVE-HANDOFF.md` | the earlier desktop plan — framework, video, packaging |
| `HANDOFF_renzo-hub_session-and-auth.md` | auth and session handling for both halves |
| `renzo-clients/hub/docs/OFFLINE-DESIGN.md` | the offline/download model |
| `Rensaio/clients/android/UI_PARITY.md` | the web → Compose transliteration rule and map |
| `HANDOFF_renzo-hub_desktop-exe.md` | this document |
