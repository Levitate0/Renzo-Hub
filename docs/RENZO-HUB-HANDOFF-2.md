# Renzo Hub → server/web, round 2

**For:** `/opt/zurg-stack/renzo-ecosystem/renzo` (server + Next.js web UI) and `renzo-clients/tv-native`.
**Date:** 2026-08-03. Follows `RENZO-SERVER-HANDOFF.md` and your `RENZO-SERVER-REPLY.md`.

Everything you sent back is **done on the Hub side**. This covers what changed,
one bug you fixed in your tree that is almost certainly still live in a second
place, and the one thing the Hub cannot do alone: **resume position needs to be
server-owned or it will never sync to the web UI.**

---

## 1. Your reply — all actioned

| Your item | Hub |
|---|---|
| §6 `DownloadJob.progress` `Int?` → `Double?` | done |
| §7 content ladder default `"ecchi"` → `"none"` | done |
| §4 captions via `/dl/captions/…?dtoken=` | done, via `/api/titles/:id/offline/:ep` |
| §4 missing sidecar must not kill the item | done — see below |
| §3.5 `.m3u8` guard | done (also `.mpd`) |
| §3.3 branch on 206, not `Accept-Ranges` | already correct |
| §1 batch resolve | adopted |
| §5 don't pattern-match `/files/` | audited — the Hub keys off absolute-vs-relative, nothing matches the literal |

**The sidecar fix was worse than it looked.** The Hub treated *any* failed asset
as fatal and deleted the whole item — so one 404ing `.vtt` would have deleted an
already-downloaded 2 GB episode. Only `VIDEO`/`PAGE` are required now; subtitles
and covers are cosmetic and skip silently. Your 500→404 change made the failure
survivable; this made it harmless.

**How the Hub now calls your endpoints:**

- `POST /api/titles/:id/play` once per job, chunked at 24, purely to warm the
  8-minute cache. Failures and `retriable` are ignored at this stage — the
  per-item resolve is authoritative and benefits from whatever landed in cache.
- `GET /api/titles/:id/offline/:ep` first for each episode; on 409 (debrid-only)
  it falls back to `GET /play/:ep`.

---

## 2. The same decode bug, one class over — check `tv-native`

You fixed `DownloadJob.progress` (`Int?` → `Double?`). **`EpisodeInfo.progress`
has the identical bug**, and it is a wider blast radius.

`types.ts` documents `EpisodeRecord.progress` as *"0..1 while downloading"*, and
`downloader.ts:561` does `ep.progress = frac`. The episode list in
`routes/api.ts:348` emits it as `progress: rec?.progress ?? 0`.

So `0` decodes fine as an Int and `0.42` throws — meaning **the whole title
detail response fails to parse while any episode of that title is downloading.**
Same trigger as the bug you found, different screen: start a season download,
open the series page, and it dies.

Fixed in the Hub. **Very likely still present in `tv-native`** — worth a grep for
`progress: Int?` across that tree.

---

## 3. Resume position — the one thing that needs you

The Hub now resumes playback where you left off. It works for streamed and
downloaded episodes alike, persists across app kills, ignores the first 15s and
the last 60s, and clears itself when an episode ends.

**It is device-local, and it has to be, because there is nowhere to put it.**
`user.progress` is `titleId → last episode watched` — a whole-episode
high-water mark set with `Math.max`. There is no per-episode position in the
schema and no endpoint that carries one.

Consequence: start an episode on your phone, open the web UI, and it starts from
zero. Same in reverse. Which is exactly the "streamlined" part that is missing.

### Requested

Somewhere to store `titleId + episode → position`, plus duration so a client can
decide whether the position is worth offering.

```
GET  /api/titles/:id/resume            → { "12": { "positionMs": 431000, "durationMs": 1420000, "updatedAt": … }, … }
POST /api/titles/:id/resume/:ep        { "positionMs": 431000, "durationMs": 1420000 }
DELETE /api/titles/:id/resume/:ep
```

Shape notes, from having just built the client half:

- **Last write wins, no `Math.max`.** Unlike `watchedThrough`, seeking backwards
  is a normal thing to do. A monotonic maximum here would make it impossible to
  rewatch from earlier.
- **The server should clear the entry when the episode is marked watched**, so
  `POST /titles/:id/watched/:ep` implies `DELETE` of that episode's position.
  Otherwise a finished episode reopens at the credits.
- **Suggested policy, applied client-side today:** ignore below 15s, discard
  within 60s of the end. Worth centralising so web and native agree rather than
  each inventing thresholds.
- **Write cadence:** the Hub persists every 10s while playing and on teardown.
  If that is too chatty, say so and it will back off — but note that anything
  slower loses more on a process kill.

Until this exists the Hub keeps its local copy. When it lands, the Hub reads the
server value at player start and treats local as a cache, which is also what
makes offline playback still resume without a network.

---

## 4. Watched status — a question, not a request

The current model is one integer per title: `progress[titleId]` = highest
episode watched, monotonic. That is clean for "up next" and it is what
`upNextFor` needs.

Two things fall out of it that may or may not be intended:

1. **You cannot mark a middle episode unwatched.** `setWatched` takes a `Math.max`,
   so watching ep 12 marks 1–11 watched by implication and nothing can unmark
   ep 5. `POST /titles/:id/progress` sets the value directly, so the *capability*
   exists, but the mental model differs between the two endpoints — worth being
   deliberate about which one clients should use.
2. **There is no per-episode watched set**, so a client cannot show "watched" on
   ep 3 while ep 4 is unwatched. Every native tile derives its tick from
   `ep.number <= watchedThrough`.

If the high-water mark is the intended model, the Hub is already correct and
this is just documentation. If you want per-episode marks, that changes both
clients and is better decided before more UI is built on the current shape.

---

## 5. Web UI parity — what it would need

For the web to match what the Hub does today:

- **Resume position** — §3. Nothing else in this list matters as much.
- **Adult default.** You did this on 2026-08-02; the Hub's Renzo half now agrees
  (`"none"`). No action, noted so all three surfaces stay aligned.
- **Downloads are device-local and web has no equivalent** — a browser cannot
  hold a 2 GB episode usefully, so the offline manifest is deliberately
  native-only. The web should not try to mirror it. What it *could* usefully
  show is which episodes a given device holds, but that needs a device registry
  that does not exist and is probably not worth it.
- **`/api/titles/:id/offline/:ep`** is native-only by design (it mints download
  tokens). No web use.

---

## 6. Still open on your side

Carried forward from your §8, unchanged and still yours:

- Server-side downloads do not resume (`library.downloadTo()` has no `Range`).
  Now known-buildable since both providers answer 206.
- `/jellyfin/api/file`'s hand-rolled Range parser (suffix ranges, negative
  `Content-Length`). Dormant plugin.
- Multi-range on `/files` degrades to a full 200.
- **The Cloudflare caching question.** `/files` sends
  `Cache-Control: public, max-age=0` on non-user-namespaced URLs and the edge
  demonstrably caches from that hostname. This one is worth prioritising — it is
  a potential cross-user content leak, not just a performance wart.

## 7. Still open on the Hub side

- **Nothing has been tested against a real debrid download.** Streaming, the
  206/200 handling, `Range` resume, re-resolution between attempts and the
  size-mismatch guard are all built and reasoned-through, and none of it has
  moved an actual gigabyte. This gates everything else.
- Renzo's Downloads screen shows server jobs only — no device-download list yet.
- Season-level "Save to device" (per-episode only today).
- Wi-Fi-only and the free-space check are enforced in the downloader but have no
  settings UI; Wi-Fi-only defaults to on and is only togglable via the
  `downloads.wifiOnly` key.
