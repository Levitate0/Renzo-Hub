# Renzo (anime) — changes needed for the Hub, and debrid streaming

**For:** whoever works on `/opt/zurg-stack/fullstack-arr` (Node 20 / Express 4 /
TypeScript, port 8787) and the Renzo half of the Android client.
**Written:** 2026-08-03, from a read of the server source at that date.

The Android side is now **Renzo Hub** — one APK carrying both Renzo (anime) and
Renzo Shiori (manga), applicationId `top.levitatemedia.renzo`, in
`/opt/zurg-stack/renzo-clients/hub`. This document covers what changed on the
client, what the server needs, and the debrid-specific risks — which are the
sharp end of all of it.

Nothing here is required for the Hub to *build*; it builds and runs today.
These are the things standing between "downloads exist" and "downloads are
reliable".

---

## 1. What already changed on the client

| Area | Change |
|---|---|
| Hosting | Renzo's `MainActivity` is gone. `RenzoRoot` is a composable hosted by `HubActivity`; window setup and demo hooks moved to `RenzoHost`. |
| Auth | Unchanged on the wire — still the opaque `fsa_session` cookie, replayed as a `Cookie:` header. It is now registered as a `RequestSigner` in `:core`'s `HubSession`. |
| Images | One process-wide Coil loader in `:core` signs for whichever half is on screen. Renzo relied on the default loader before. |
| Boot | On a non-401 failure the app now proceeds signed-in with a cached identity instead of dropping to the Connect screen. Only a real 401 costs the session. |
| Offline | New. `RenzoDownloadSource` + the shared `HubDownloadService`. |
| Player | `PlayerScreen` checks the offline library first and, for a downloaded episode, **skips `/play` entirely** — no resolve, no network. |
| TV | Leanback entry goes straight into Renzo; the picker never appears there. |

The endpoints Renzo uses are otherwise untouched.

---

## 2. Server: what the downloader relies on

Verified against the source, not assumed.

**Range on `/files` — already correct.** `server.ts` mounts it with
`express.static(userRoot(uid), { acceptRanges: true })` and the whitelist branch
uses `res.sendFile(..., { acceptRanges: true })`. Resume works for local files
today. **Do not lose this** — the client resumes a partial episode with
`Range: bytes=<n>-` and treats a `200` (rather than `206`) as "server ignored
Range", discarding the partial file and starting over. Silently dropping
`acceptRanges` would turn every interrupted 2 GB download into a full restart.

**`Content-Length` is needed for progress.** `sendFile` sets it. The client's
progress bar and its "is this file whole?" check both depend on it; a
chunked response with no length still downloads but shows no progress.

**`/api/captions/{id}.vtt`** is fetched as a sidecar per episode and stored
next to the video. No change needed.

---

## 3. Debrid streaming — the risks

This is where offline downloads are genuinely fragile, and none of it applies to
the manga half.

### 3.1 Resolved links expire, and the client already handles it

`resolveStream` caches per `${user}:${anilist}:${episode}` with
`STREAM_TTL_MS = 8 * 60_000`. Eight minutes.

**A 2 GB episode over a slow link takes longer than eight minutes.** The client
therefore resolves **per item, immediately before fetching it** — a queued batch
stores only `titleId:episode`, never a URL. That is the entire reason the
`DownloadSource` seam exists.

What is **not** handled: if the debrid CDN drops the connection mid-file, the
client does not currently re-resolve and resume against a *fresh* link. The item
fails and its partial file is discarded. See §5.

### 3.2 A season download costs one resolve per episode

`resolveStream` can block server-side up to ~45 s while the debrid pipeline
works, and may fail outright with *"Could not get an instant stream (nothing
cached on your debrid service yet)"*.

For a 12-episode season that is up to ~9 minutes of resolving before a byte of
video moves, plus 12 hits on the debrid provider's API.

**Requested: a batch resolve.** Something like
`POST /api/titles/:id/play` with `{ episodes: [1,2,3] }` returning an array of
`ResolvedStream`, resolving concurrently server-side and reporting per-episode
failures instead of failing the batch. The client would call it once per job and
fall back to per-episode resolution when the endpoint is absent.

Not blocking — the client works without it — but it is the difference between a
season download starting in seconds and starting in minutes.

### 3.3 Range support on debrid links is unverified

Local files support Range. **Whether Real-Debrid and AllDebrid direct links
honour `Range` has not been tested**, and resume for debrid-sourced episodes
depends entirely on it.

Worth an actual check with `curl -r 0-1023 -I "<resolved url>"` against both
providers and confirming a `206` plus `Content-Range`. If a provider answers
`200`, resume is impossible there and the client will restart the file — which
should then be surfaced in the UI rather than silently retried.

### 3.4 The session cookie must never reach a debrid host

`ResolvedStream.url` is an **absolute** third-party URL for debrid sources and a
**relative** `/files/...` path for local ones. The player has always scoped the
cookie by host for exactly this reason.

**This was a real bug in the Hub's downloader**, found while writing this doc and
fixed 2026-08-03: `HubDownloadService` signed *every* request, so a debrid CDN
would have received `Cookie: fsa_session=…`. It now signs only when the request
host matches the configured server host, and fails closed on an unparseable URL.

Anything else that fetches a `ResolvedStream.url` — a future web downloader, a
cast target — needs the same rule. It is not a client-local concern.

### 3.5 `.m3u8` is unconfirmed

Nothing in the client references HLS, and `media3-exoplayer-hls` looks like an
unused dependency. If any debrid provider can return an HLS manifest, the
downloader is wrong for it — a single-file fetch would save the playlist rather
than the media.

Someone should confirm whether `ResolvedStream.url` is ever `.m3u8`. If it can
be, segment downloading is a separate feature and the client should refuse to
"download" such an episode rather than store a broken file.

---

## 4. Content rating — needs a decision

The handoff's Play Store section (§8) asks whether Renzo's catalogue can hide
adult content by default. The client has a device-local ladder in `Prefs`
(`contentLevel`, default **`"ecchi"`**, cycled from the account menu) and
`TitleDetail.isAdult` exists server-side.

**Shiori now hides adult content by default; Renzo does not.** Before a Play
submission the two should agree, and the defensible default is hidden. That is a
product decision, not a technical one, which is why it is flagged rather than
changed.

---

## 5. Client-side work still outstanding

Listed here because it is Renzo-half work and belongs with this handoff.

1. **Re-resolve and resume on a mid-file drop.** Today a dropped connection
   fails the item and deletes the partial. It should re-resolve (the old link is
   likely expired), then resume with `Range` against the fresh URL.
2. **Wi-Fi-only toggle**, default on. A 3 GB episode on cellular is a bad day.
3. **Free-space precheck.** `OfflineStore.usableSpaceBytes()` exists and nothing
   calls it. Refuse the enqueue rather than filling the device.
4. **An offline list in Renzo's Downloads screen.** It currently shows only
   server-side jobs. Keep the two visually distinct — a server download and a
   device download are different things.
5. **Season-level "Save to device".** Only per-episode exists.
6. **End-to-end verification against a real debrid link.** The streaming,
   resume and 206/200 handling are reasoned-through but have never run against
   an actual multi-gigabyte debrid download.

Item 6 gates the rest: until a real episode has been downloaded, killed
mid-transfer and resumed successfully, the whole feature is unproven.

---

## 6. Things not to change without telling the client

- **`acceptRanges` on `/files`** — resume depends on it (§2).
- **`ResolvedStream.url` staying absolute-for-debrid / relative-for-local** —
  the host check that protects the session cookie keys off this shape.
- **`STREAM_TTL_MS`** — shortening it below a typical episode download makes
  §3.1 worse; the client compensates by resolving late, but a much shorter TTL
  would start expiring links mid-fetch.
- **The 402 `realdebrid_required` contract** — the player surfaces it as
  "Connect a debrid service in the web app first", and the downloader treats it
  as a permanent per-item failure rather than something to retry.
