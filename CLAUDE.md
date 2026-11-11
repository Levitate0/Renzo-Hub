# Renzo Hub — working rules

The only client app of the Renzo ecosystem (Android phones/TV + Windows
desktop): `feature-shiori` (manga, for Renzo Shiori), `feature-renzo` (anime, for
Renzo), `core`, `app`, `hub-desktop`. This repo is PUBLIC: never commit
keystores, `key.properties`, `local.properties` or any secret.

## Owner rules

- **Parity with the web, both ways.** Every Shiori or Renzo web UI change is
  also made here in the same piece of work, without asking (2026-11-08).
  Screens are transliterated from the web component source 1:1, not
  approximated (`docs/UI_PARITY.md`); open gaps and deliberate differences live
  in `docs/PARITY-GAPS.md`.
- **A change only reaches the owner in a release.** When Hub changes ship, cut
  one, without asking:
  1. Bump `versionCode` and `versionName` in `app/build.gradle.kts` and commit.
  2. Build the release APK (`:app:assembleRelease`) and the Windows installer.
     A desktop build before an APK can poison Gradle's transform cache and
     still print BUILD SUCCESSFUL, so grep the log for `^e: `; purge the
     transforms and stop the daemon before retrying.
  3. `git tag -a vX.Y.Z`, push `main` and the tag.
  4. Create the GitHub release page with a plain-language changelog and attach
     the APK, the Windows installer and a `SHA256SUMS`.
- **The servers are released too (2026-11-10).** A matching Renzo Shiori or
  Renzo server change gets its own tag (CI publishes its GHCR image) and
  release page; see those repos' CLAUDE.md.

## Build checks

Fastest sanity check: `./gradlew :feature-shiori:compileKotlinDesktop`
(`:feature-renzo:compileKotlinDesktop` for the anime half,
`:app:compileReleaseKotlin` for Android-only code).
