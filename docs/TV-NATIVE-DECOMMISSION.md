# Retiring `tv-native` in favour of Renzo Hub

**For:** the Renzo chat (`/opt/zurg-stack/renzo-ecosystem/renzo`, `renzo-clients/tv-native`).
**Date:** 2026-08-04. Written from a diff of both trees at that date.

Renzo Hub (`hub`) is one APK carrying Renzo and Renzo Shiori. It
takes Renzo's applicationId and Renzo's signing key, so it **upgrades existing
Renzo installs in place**. This is what has to happen before `tv-native` stops
being the shipping client, and what must survive the purge.

---

## 1. The good news first: nothing needs porting back

I diffed `tv-native` against `hub/feature-renzo` file by file. Every change
made in `tv-native` since the Hub forked (2026-08-02) is **already in the Hub**:

- `DownloadJob.progress` → `Double?` — present
- `EpisodeInfo.progress` → `Double?` — present (the Hub found this one)
- `contentLevel` default `"none"` — present

The only remaining differences are the ones the merge deliberately introduced:
Coil 2 → 3 imports, `MainActivity` split into `RenzoBoot`/`RenzoEntry`, and the
new `offline/` package. **There is no outstanding work to salvage from
`tv-native`.** If you change it after today, re-run that diff before purging.

---

## 2. Existing installs keep everything

> **Update, 2026-08-04:** `tv-native` has only ever been installed by its
> author — there is no install base. Everything below still holds and is worth
> keeping for the Play cutover, but the staged rollback in §7 is more caution
> than this needs. The applicationId and signing-key points (§3) still matter
> in full: those bind the Play listing, not the installs.


Better than the Shiori side, which loses its data:

- **applicationId is identical** (`top.levitatemedia.renzo`), so this is an
  in-place upgrade, not a reinstall.
- **The preferences file is identical** — `hub/feature-renzo` still calls
  `getSharedPreferences("renzo_tv", …)`.

So a user upgrading from `tv-native` keeps their **server URL, their session
cookie, their theme, their subtitle language and their content-level setting**.
They will not be asked to sign in again.

One nuance: the content ladder now defaults to `"none"`, but a stored value
wins, so **existing users keep whatever they had** — only fresh installs get the
new default. If you want existing installs forced to `"none"`, that needs an
explicit one-time migration; say so and I will add it.

**versionCode:** Hub is at **45**, `tv-native` at **44**. If `tv-native` ships
anything else before cutover, the Hub's must be raised above it. Play rejects a
lower or equal code permanently.

---

## 3. Signing — the two trees do it differently

`tv-native` builds an **unsigned** release and signs manually:

```
./gradlew :app:assembleRelease      # unsigned
# then apksigner with ../signing/renzo-release.jks
```

The Hub instead has a Gradle `signingConfig` that reads an untracked
`key.properties` at the repo root, **and that file does not exist** — so
`hub`'s release currently falls back to the *debug* key. A debug-signed APK
cannot upgrade a release-signed install and Play will reject it outright.

Pick one before cutover:

- **Gradle signs it** — create `hub/key.properties` with
  `keystoreFile=../signing/renzo-release.jks` plus the three passwords from
  `signing/CREDENTIALS.txt`. It is already gitignored.
- **Keep the manual flow** — leave `key.properties` absent and sign the output
  with `apksigner`, exactly as `tv-native` does today.

Either is fine. What is not fine is shipping the current fallback.

---

## 4. Do NOT delete

- **`renzo-clients/android/`** — despite being the dead WebView client,
  `android/sdk/` is the **shared Android SDK** (android-36, build-tools 36.0.0)
  that both `tv-native/local.properties` and `hub/local.properties` point at.
  Deleting it breaks the Hub build. Only `android/app/` and
  `android/build-apk.sh` are actually dead. (Your README already says this —
  repeating it because "purge tv" is exactly the moment someone deletes it.)
- **`renzo-clients/signing/`** — Play binds the listing to this key forever.
- **`renzo-clients/artifacts/`** — the Play listing assets
  (`feature-graphic-1024x500.png`, `play-icon-512-dark.png`,
  `tv-banner-1280x720.png`) are still needed. `Renzo.apk`/`Renzo.aab` there are
  the last `tv-native` outputs; keep them until the Hub has shipped and stuck.
- **`RELEASE_NOTES_*.md`** — release history.

---

## 5. What changes for a Renzo user

Behaviour the Hub adds or alters, so release notes can say so:

| | |
|---|---|
| Launch | A picker appears every time the app comes to the foreground (Renzo / Renzo Shiori), **including on TV** — see the note below. |
| Switching | "Switch to Renzo Shiori" in the hamburger drawer, on every form factor. |
| Offline | Episodes can be saved to the device ("Save to device" in the episode ⋮ menu), kept distinct from the existing server-side "Download". |
| Playback resume | Position is remembered per episode and now **synced via the server**, so phone/TV/web share it. |
| Boot | A server that is unreachable no longer dumps you on the Connect screen — only a real 401 does. |
| Logged out | A rejected session anywhere returns you to Login instead of showing empty pages. |
| Adult content | Defaults to hidden on fresh installs. |
| App name/icon | "Renzo Hub", new mark. |

**The TV experience changed on 2026-08-04.** It previously went straight into
Renzo, because the manga half had no D-pad support. That half is now
TV-navigable (focus rings, a nav rail, a D-pad reader, voice search), so the TV
gets the picker and the switcher like everything else, and configuration screens
there show the instance's web address instead of an unusable form.

The leanback entry point and launcher banner are unchanged.

---

## 6. Before you flip the switch

1. **Sign the Hub release properly** (§3) and confirm with
   `apksigner verify --print-certs` that it matches the cert on the current
   Play listing. A mismatch here is unrecoverable.
2. **Install the signed Hub release over an existing `tv-native` install** on a
   real device and confirm the session survives — that is the §2 claim, and it
   is the one thing worth proving rather than trusting.
3. **Test on a TV with a real remote.** The whole D-pad surface is
   compile-verified only — nobody has pressed a D-pad against it. Check focus
   traps in dialogs first (getting in is easy, getting out is where these fail),
   then whether the reader's key sink ever swallows an arrow, then grid
   scroll-into-view near a viewport edge.
4. **Renzo device downloads have never been run against a real debrid link.**
   Streaming, `Range` resume, re-resolution between attempts and the
   size-mismatch guard are all built and unproven. Either verify it, or ship the
   Hub with the "Save to device" menu item hidden and enable it in a follow-up.
   With no install base this is lower-stakes than it reads, but it is still the
   least-proven code in the tree.

---

## 7. Suggested sequence

1. Raise the Hub's versionCode if `tv-native` has shipped again.
2. Configure signing; build and verify the release APK.
3. Ship it as the next Renzo release — **from the Hub tree**, in place, no new
   listing.
4. Watch a release cycle. `tv-native` stays on disk, untouched, as the rollback.
5. Once the Hub has stuck, mark `tv-native/` archived in `renzo-clients/README.md`
   (same treatment `capacitor/` got) rather than deleting immediately.
6. Repoint anything that names it — `publish-release.mjs` uploads a file called
   `Renzo.apk` and does not care where it came from, but
   `tv-native/tools/capture-play-shots.ps1` targets that tree and will need
   updating for the Hub's `demo` build.
7. Delete `tv-native/` only after a release has shipped from the Hub and you no
   longer want the rollback.

---

## 8. What the Hub does not replace

`renzo-clients/desktop/` and `renzo-clients/jellyfin/` are untouched by any of
this. The Hub is Android only.
