# Unified downloads & offline — design

One download/offline system in `:core`, used by both halves. Written against
the code as it stands, not from a blank page: the Shiori half already has a
working implementation and this generalises it rather than replacing it.

## 1. What exists

**Shiori — complete and working.**

| Piece | File | Role |
|---|---|---|
| `RenzoStore` | `RenzoStore.kt` | SAF/File writes, `.nomedia`, doc-URI memo, KV, manifest, job queue |
| `RenzoDownloadService` | `RenzoDownloadService.kt` | Foreground service; drains the queue, fetches pages, updates the manifest, broadcasts progress |
| `OfflineRepository` | `data/offline/` | Read side — list series/chapters, read a page, delete |

Storage is either a user-picked SAF tree (`DocumentFile`, under a `RenzoShiori/`
subfolder) or app-private `getExternalFilesDir("offline")`. The manifest is JSON
v2 in `SharedPreferences`, keyed `series` and `chapters`.

**Renzo — nothing.** No `filesDir`, no Room, no `DocumentFile`, no local
storage of any kind. Its "Downloads" screen is *server-side* job status via
`repo.jobs()`. The player streams.

## 2. The finding that shapes the design

**A Renzo episode is a single progressive file, not HLS.**

`ResolvedStream` from `GET /api/titles/{id}/play/{ep}` carries one `url` —
absolute https for debrid, or a relative `/files/…` for local — plus a list of
`SubtitleRef`, each fetched from `/api/captions/{id}.vtt`. Nothing in the
feature references HLS, `.m3u8`, or `HlsMediaSource`.

So an episode is **one large file plus a few small sidecar files**, which is
structurally identical to a chapter being *many* small files. No segment
manifests, no Media3 `DownloadManager`, no `DownloadService` subclassing. The
same fetch-and-record loop covers both.

> Aside: `media3-exoplayer-hls` appears to be an unused dependency. Verify
> against a debrid URL that actually resolves before dropping it — some
> providers do hand back `.m3u8`.

## 3. The shared model

An item is an ordered set of remote assets plus a metadata record.

| | Shiori | Renzo |
|---|---|---|
| Parent | Series | Title |
| Item | Chapter | Episode |
| Assets | N page images | 1 video + N subtitles |
| Asset count | 15–60 small | 1 huge + 2–5 tiny |
| Item size | 5–40 MB | 300 MB – 3 GB |
| Source URLs | stable server paths | **expiring debrid links** |

Two of those rows are the whole difference, and both are Renzo-side. Everything
else is the existing Shiori pipeline with the nouns widened.

```kotlin
enum class AssetRole { PAGE, VIDEO, SUBTITLE, COVER }

@Serializable data class OfflineAsset(
    val role: AssetRole,
    val index: Int,          // page number / subtitle track order
    val relPath: String,     // path within the store
    val mime: String? = null,
    val label: String? = null, // subtitle language
)

@Serializable data class OfflineItem(
    val target: HubTarget,   // Renzo | Shiori — the namespace
    val parentId: String,    // seriesId / titleId
    val itemKey: String,     // chapterKey / "{titleId}:{ep}"
    val ordinal: Double,     // chapter number / episode number
    val title: String,
    val assets: List<OfflineAsset>,
    val bytes: Long,
    val savedAt: Long,
    val complete: Boolean,   // false while a resumable transfer is mid-flight
)

@Serializable data class OfflineParent(
    val target: HubTarget,
    val parentId: String,
    val title: String,
    val coverPath: String? = null,
    val description: String? = null,
)
```

## 4. `:core` components

### `OfflineStore`
`RenzoStore` generalised. Same SAF/File duality, same `.nomedia` handling, same
doc-URI memoisation — with three changes:

1. **Namespaced by target.** The SAF subfolder becomes `RenzoHub/renzo/…` and
   `RenzoHub/shiori/…` so one picked folder serves both without collision.
2. **`fun uriFor(relPath: String): Uri?`** alongside `readFile`. Non-negotiable
   for video: Media3 needs a URI to stream from, and you cannot read a 2 GB file
   into a `ByteArray`. Shiori's reader keeps using `readFile` for pages.
3. **Streaming writes** — `fun openOutput(relPath, append: Boolean): OutputStream?`.

### `DownloadManifest`
The v2 JSON replaced with kotlinx.serialization over the types above, held as
one blob. **Must migrate the existing Shiori manifest**, see §7.

### `DownloadQueue`
`RenzoStore`'s `enqueueJob`/`takeJob`/`clearJobs`, unchanged in spirit —
persisted rather than passed by Intent, so a large batch cannot blow the Binder
transaction limit and the queue survives a restart.

```kotlin
@Serializable data class DownloadJob(
    val target: HubTarget,
    val parent: OfflineParent,
    val items: List<PendingItem>,
)
@Serializable data class PendingItem(
    val itemKey: String,
    val ordinal: Double,
    val title: String,
    /** Null for Renzo: the URLs expire, so they are resolved at fetch time. */
    val assets: List<PendingAsset>? = null,
)
```

### `HubDownloadService`
One foreground service replacing `RenzoDownloadService`, `dataSync` type as now.
Differences from today's:

- **Auth comes from `HubSession`**, not a token baked into the job payload.
  This is exactly what the signer registry was built for — the service signs
  per-request for `job.target`, so the Renzo half gets its `fsa_session` cookie
  and Shiori its Bearer without the service knowing either scheme. Today's job
  payload carries a raw `token` string, which also expires mid-queue.
- **Streams to disk** with `Range` resume. Today's `httpGet` does
  `inputStream.use { it.readBytes() }` — fine for a page, an OOM for a 2 GB
  episode. This is the single most important change.
- **Asks a `DownloadSource` for the assets** of each item, immediately before
  fetching it.

### `DownloadSource` — the per-feature seam

```kotlin
interface DownloadSource {
    /** Resolve the assets for one item, called immediately before fetching. */
    suspend fun resolveAssets(item: PendingItem): List<PendingAsset>
}
```

- **Shiori** returns the page paths it already has — no work.
- **Renzo** calls `repo.play(titleId, ep)` *at download time* and returns the
  video URL plus `/api/captions/{id}.vtt` for each subtitle.

That late resolution is the point. **Debrid links expire.** A job queued at
09:00 and reached at 11:00 must not carry a stale URL, so Renzo's jobs store
`titleId`/`episode` and nothing else. Shiori has no such constraint, which is
why today's design bakes URLs into the payload and gets away with it.

### `OfflineLibrary`
The read side. `OfflineRepository`'s API, plus a `target` filter, plus
`uriFor`. Both halves list only their own items.

## 5. Feature-side work

**Shiori** — mostly re-pointing at `:core`. `OfflineRepository` becomes a thin
wrapper over `OfflineLibrary`; `RenzoDownloadService` is deleted; the SAF folder
picker moves to shared settings. The reader's `readPage` path is unchanged.

**Renzo** — all new:
- Episode/season download buttons on `TitleScreen`
- An offline section on `DownloadsScreen`, which today shows only server jobs.
  Keep them distinct — a *server-side* download job and a *device* download are
  different things and conflating them will confuse.
- **`PlayerScreen` must prefer local.** It currently always builds
  `MediaItem.setUri(client.absolute(s.url))`. It needs to check
  `OfflineLibrary` first and use `uriFor(video)` plus local subtitle URIs.
  Without this the feature is invisible — you download an episode and it still
  streams.

## 6. Renzo-only concerns that Shiori never had

These come from file size, and none are optional:

- **Resume.** A 2 GB transfer that dies at 90% must resume via `Range`, not
  restart. Hence `complete: Boolean` on `OfflineItem` and append-mode writes.
- **Free-space precheck** before enqueueing, using
  `StatFs`/`getAllocatableBytes`. Filling a user's phone is a bad first
  impression.
- **Wi-Fi-only toggle**, default on. Nobody wants a 3 GB episode on cellular.
  Shiori's chapters are small enough that this never mattered.
- **One item at a time.** Shiori fetches 5 pages concurrently because each is
  small and latency-bound. Parallel video downloads just thrash the disk and
  saturate the link — keep `PAGE_CONCURRENCY` for page-shaped assets, force
  serial for `VIDEO`.

## 7. Migration

> **Correction, 2026-08-03.** The migration below is implemented and tested, but
> it is **unreachable for real users** and must not be described as a safety net.
>
> The Hub's applicationId is `top.levitatemedia.renzo`; the standalone Shiori
> client's is `app.renzoshiori.client`. Different package means a different data
> directory, so the Hub cannot read that app's SharedPreferences *or* its
> app-private downloads. The v2 manifest is simply never present.
>
> This is the cost the handoff already priced in at §2 — Shiori users reinstall
> and lose their saved server URL, session and downloaded chapters. Keeping the
> applicationId was the right call for the Play listing; it just means offline
> data does not survive the cutover.
>
> The code stays because it is the working half of any future import path: a
> user-picked SAF folder full of chapters IS readable across packages, so a
> "restore from folder" feature would reuse it directly. Until that exists,
> treat migration as dormant.

Shiori users have real downloaded chapters recorded as manifest v2 under the
`renzo_offline` prefs, with files at `offline/{chapterKey}/…` inside a
`RenzoShiori/` SAF subfolder.

On first run of the Hub's store:

1. Read v2 if v3 is absent.
2. Map each `chapters` entry to `OfflineItem(target = Shiori, …)` with
   `pagePaths` → `assets(role = PAGE, index = ordinal)`.
3. Map `series` → `OfflineParent(target = Shiori, …)`.
4. Write v3 and keep v2 in place, unread, for one release as a rollback.

Leave the existing files exactly where they are — `RenzoShiori/` stays a valid
subtree; only *new* Shiori downloads go to `RenzoHub/shiori/`. Rewriting paths
would mean moving gigabytes for no benefit.

## 8. Order of work

1. `OfflineStore` + `DownloadManifest` + migration, with the Shiori half still
   on its own service. Verifiable in isolation: existing downloads must still
   list and open.
2. `HubDownloadService` + `DownloadQueue` + `DownloadSource`; port Shiori onto
   it and delete `RenzoDownloadService`. No user-visible change — that is the
   point, and it is the regression gate.
3. Renzo's `DownloadSource` (late resolution) + streaming/resume fetch. Test on
   a real episode, on a real debrid link, over a killed connection.
4. Renzo UI: download buttons, offline list, **and the `PlayerScreen` local
   preference**, which is what makes it real.
5. Wi-Fi-only, free-space precheck, storage settings shared by both halves.

Steps 1–2 are refactors with an existing correctness oracle. Steps 3–4 are the
new feature. Do not merge them.

## 9. Play Store note

Downloaded video sharpens the §8 posture in the handoff: the app now stores
copyrighted material on device. It remains a client for a server the user runs,
ships no sources and no catalogue — but the data-safety form must declare local
storage of user-selected media, and no new permission should be added to get
there. SAF needs none, which is why the Shiori half picked it, and Renzo should
inherit that rather than reaching for `WRITE_EXTERNAL_STORAGE`.
