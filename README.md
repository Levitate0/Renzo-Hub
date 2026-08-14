# Renzo Hub

One Android APK carrying both UIs — Renzo (anime) and Renzo Shiori (manga) —
with a launch picker, per-app server config, and a "Switch to <app>" entry in
the hamburger menu. TV shows Renzo only.

Built against `RENZOHUBHANDOFF.md`. This file records what is actually done and
verified, and where the handoff's assumptions turned out to be wrong.

## Status

| Step | State |
|---|---|
| Toolchain reconciliation | **done, verified by build** |
| Module split (`:app` `:core` `:feature-renzo` `:feature-shiori`) | **done** — `:core` is an empty shell |
| `ServerSession` + credential store | not started |
| Picker + switcher | not started — `HubActivity` is a placeholder |
| TV leanback → Renzo directly | not started |
| Branding / signing / screenshot builds | partly — icons and manifest done, signing not wired |

All 131 Kotlin files (32 Renzo + 99 Shiori) compile unmodified except for the
Coil migration and one resource rename. `assembleDebug`, `assembleRelease` and
`assembleDemo` all succeed.

Neither original tree was modified. `tv-native` and `Rensaio/clients/android`
still build and ship exactly as before, so the handoff's sequencing advice —
ship Renzo's Play listing off the standalone build *before* cutting over — is
still available.

## Module layout

```
:app             launcher, manifest (both intents), branding, signing
:core            (empty) — auth, HTTP, theme, image loading go here
:feature-renzo   anime UI + Media3 player   — from tv-native
:feature-shiori  manga UI + reader          — from Rensaio/clients/android
```

Both feature modules **keep their original package names**
(`top.levitatemedia.renzo.tv`, `app.renzoshiori.client`) as their AGP namespace.
With `android.nonTransitiveRClass=true` each keeps its own `R`, so the 131 files
moved across with zero package or import edits. Do not "tidy" these into a
common package — that trade buys nothing and costs a 131-file diff.

`:feature-renzo` and `:feature-shiori` must never import each other.

## Toolchain

Reconciled to the newer of each side, in `gradle/libs.versions.toml`.

| | tv-native | Shiori client | **Hub** |
|---|---|---|---|
| AGP | 8.13.0 | 8.6.1 | **8.13.0** |
| Kotlin | 2.2.0 | 2.4.10 | **2.4.10** |
| Gradle | 8.14.3 | 8.7 | **8.14.3** |
| compile/targetSdk | 36 | 35 | **36** |
| JDK | 21 | 17 | **21** |
| Compose BOM | 2025.06.00 | 2026.06.00 | **2026.06.01** |
| Coil | 2.7.0 | 3.1.0 | **3.5.0** |
| tv-material | 1.0.1 | — | **1.1.0** |

Media3 is deliberately held at tv-native's **1.7.1** rather than the current
1.10.x. The player is the most fragile thing being moved; a media3 bump is an
independent change with its own regression surface and does not belong inside
the merge.

`local.properties` points at `/opt/zurg-stack/renzo-clients/android/sdk` — **not**
`/opt/android-sdk`, which the Shiori client used. Only the former has
android-36 and build-tools 36.0.0.

## Corrections to the handoff

- **The Coil 2→3 migration was 4 files, not "every AsyncImage call".** Every
  Renzo call site passed only `model` / `contentDescription` / `contentScale` /
  `modifier`, which are identical across Coil 2 and 3, so three UI files needed
  only an import change. The single real API change was
  `ImageRequest.setHeader` → `httpHeaders(NetworkHeaders…)` in `PlayerScreen`,
  plus rewriting the demo interceptor onto `SingletonImageLoader.setSafe` and
  `chain.withRequest(…).proceed()`.
- **`androidx.tv:tv-material 1.1.0` is now stable.** The handoff assumed 1.0.1,
  which predates this Compose BOM by two years. 1.1.0 is what is in use.
- **Kotlin 2.2 → 2.4 needed no source changes at all** on the Renzo half, only
  the plugin version.

## Known gap — the singleton ImageLoader

The handoff's §5 designs `ServerSession` for the REST layer but does not cover
image loading, and the two halves conflict there:

- Coil exposes **one process-wide singleton `ImageLoader`**.
- The Shiori half installs its own via `RenzoApp : SingletonImageLoader.Factory`,
  attaching `Authorization: Bearer`.
- The Renzo half relies on the default loader and needs `Cookie: fsa_session`.

Whichever `Application` wins, the other half's images authenticate wrongly. The
fix belongs in `:core`: one singleton loader whose OkHttp interceptor asks the
**currently active** `ServerSession` to sign the request. Only one half is on
screen at a time, so a single active-session pointer is sufficient. Note this
also decides where `RenzoApp`'s other singletons (TokenStore, NetworkModule,
OfflineRepository) get hosted, since `RenzoApp` cannot remain the `Application`.

## Resources

Branding lives in `:app`; features keep only functional resources.

- Launcher icon is **Shiori's** set (adaptive + monochrome + round + all six
  densities + night variants) per handoff §7.
- TV banner is **Renzo's** — Shiori has no TV story by design.
- `Theme.RenzoHub` merges Renzo's transparent-system-bar chrome with Shiori's
  Android 12+ splash configuration.
- One collision existed: both halves had a *different* image called
  `renzo_banner`. Renzo's is now **`renzo_wordmark`** (3 call sites).
- `Theme.RenzoHub` references `@drawable/splash_icon` and
  `@color/splash_background` out of `:feature-shiori`, which owns them — that
  torii mark is also drawn in-app on three Shiori screens, so it is not purely
  branding and was left with the feature.

## Build

```bash
./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleDemo       # fixture-backed screenshot build, .demo appId
./gradlew :app:assembleRelease    # R8 + resource shrinking
```

`assembleRelease` currently falls back to the **debug** signing key: it looks
for an untracked `key.properties` at the repo root, which is not yet present.
Before any real release it must be pointed at
`renzo-clients/signing/renzo-release.jks` — Play binds a listing to its signing
key permanently, so the Hub has to keep Renzo's.

**The release APK is not yet a meaningful size check.** It is ~1.5 MB with a
single dex because `HubActivity` is a placeholder that references neither
feature, so R8 strips both feature modules as dead code. That result confirms
the R8 pipeline, keep rules and lintVital are healthy; it says nothing about
the shrunk size of the finished app. Re-measure once the picker wires the
features in.

## applicationId

`top.levitatemedia.renzo`, versionCode 45 (above tv-native's 44), versionName
1.4.0. Renzo is the half going onto Google Play and a listing is bound to its
applicationId permanently, so the Hub inherits Renzo's ID and key. Shiori
installs (`app.renzoshiori.client`) cannot upgrade in place and will reinstall,
losing their saved server URL, session and offline chapters.
