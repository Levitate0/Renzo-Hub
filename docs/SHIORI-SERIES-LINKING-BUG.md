# Unrelated series are being merged, and adding one silently edits the other

**For:** the Shiori server chat (`/opt/zurg-stack/renzo-ecosystem/shiori`).
**Date:** 2026-08-10.
**Severity:** destructive — it writes foreign sources into an existing series,
and the user has to notice and undo it by hand.

Reported from the Android client, but reproduced against the database. **This is
not a client bug** — every client sends the same augment payload and gets the
same group back.

---

## 1. What happened

Adding **"The Villainess Reverses the Hourglass"** did not create a series. It
attached that title's source to the *existing* **"Backstabbed in a Backwater
Dungeon"** record instead.

Straight from `renzo.db`, sources on Backstabbed:

```
094C9ECB | Backstabbed in a Backwater Dungeon…  | …en.allanime    | 220
2ED425AC | Backstabbed in a Backwater Dungeon…  | …en.comicasura  | 228
30355EB3 | Backstabbed in a Backwater Dungeon…  | …all.mangafire  | 216
76EFA11B | Backstabbed in a Backwater Dungeon…  | …en.weebcentral | 209
83595B3B | Backstabbed in a Backwater Dungeon…  | …en.mangahubio  | 213
01581CC0 | The Villainess Reverses The Hourglass|  (no provider)  | 133   ← foreign
```

`SELECT … WHERE Title LIKE '%Hourglass%'` on `Series` returns **nothing** — no
series was ever created for it. The add merged into Backstabbed.

The user's confirm screen showed both a genuine Hourglass source (Lunar Manga,
ch 1–125, completed) and a Backstabbed source (Comic Asura, ch 0–209) as members
of the *same* series. Earlier, the search result for "The Villainess Reverses the
Hourglass" rendered with **Backstabbed's cover art and its 209 chapter count**.

## 2. Why

`Services/Import/ImportExtensions.cs` → `MergeSimilarSeries(threshold = 0.1)`.

Sources are grouped by title similarity —
`Extensions/StringExtensions.cs::AreStringSimilar`, normalized Levenshtein with
`distance / maxLength <= 0.1`. That pairwise test is strict and fine on its own:
"Hourglass" and "Backwater Dungeon" cannot pass it.

The problem is the pass that follows it (`ImportExtensions.cs`, the
`consolidatedLinks` loop):

```csharp
foreach (var linkedId in series.LinkedIds.ToList())
    if (idToSeriesMap.TryGetValue(linkedId, out var linkedSeries2))
        foreach (var transitiveId in linkedSeries2.LinkedIds)
            consolidatedLinks.Add(transitiveId);      // never re-checked
```

**Transitive closure with no re-verification.** If A resembles B and B resembles
C, then A, B and C become one series — even when A and C share nothing. One bad
row bridges two unrelated works, and the merge is unbounded: the chain can keep
growing.

The bridging row is visible in the report: a source returned an entry whose
**title** is "The Villainess Reverses the Hourglass" while its cover and chapter
count are Backstabbed's. That row matches the real Hourglass on title, and is
matched to Backstabbed by its other members. Nothing checks that the two ends of
the chain resemble each other.

## 3. Why it is worse than a display glitch

- **Adding a new series silently edited an existing one.** Because the group
  already contained a library series, the add resolved to "update Backstabbed"
  rather than "create Hourglass". No error, nothing created.
- **The damage persists.** Foreign sources sit on the record until someone spots
  them and deletes them one by one.
- **It is silent at every layer.** The client can only submit the group the
  server returned; it has no basis to say "these two are not the same work".

## 3a. Update 2026-08-10 — still reproduces after the mutual-similarity fix

The `MergeSimilarSeries` rewrite is deployed and correct (source 03:41, image
03:47, container restarted). The orphaned source row was also deleted from the
database. **It still merges.** Three further findings, all verified against
`renzo.db`:

### `MihonProviderId` is not unique to a series

It is `provider|instance`, shared by every work from that provider instance:

```
Last Exile - Travelers from the Hourglass    mangafire|758400875161895515
The Villainess Turns the Hourglass           mangafire|758400875161895515
The Legend of Zelda: Phantom Hourglass       mangafire|758400875161895515
```

Only `MihonId` — which appends the URL path — identifies a series. Anything
matching on `MihonProviderId` alone treats three unrelated works as the same
source. (Confirmed the other way too: no two distinct titles share a `MihonId`.)

### Two provider-matching rules disagree, and the looser one omits the title

`StringExtensions.cs:241` `IsMatchingProvider` compares provider, **title**,
language and scanlator. Thirty lines later, `SeriesCommandService.cs:1639` does:

```csharp
var existingProvider = existingProviders.FirstOrDefault(sp =>
    sp.Provider.Equals(p.Provider, …) &&
    sp.Language.Equals(p.Language, …) &&
    (string.IsNullOrEmpty(p.Scanlator) || sp.Scanlator.Equals(p.Scanlator, …)));
```

**No title, no URL.** Any incoming English source from a given provider matches
any existing English source from that provider on the target series, whatever
work it actually is. That is sufficient on its own to fold a Hourglass source
into Backstabbed's Comic Asura entry. The two rules should be one rule.

### `AreLinkable` still bypasses the title test

```csharp
if (a.LinkedIds.Contains(b.MihonId!) || b.LinkedIds.Contains(a.MihonId!)) return true;
if (a.LinkedIds.Any(id => b.LinkedIds.Contains(id))) return true;
return a.Title.AreStringSimilar(b.Title, threshold);   // unreachable if ids overlap
```

Id overlap is trusted unconditionally, so the mutual-similarity guarantee only
holds for rows that have no ids in common yet. Ids assembled from an empty
provider or empty URL are not identities at all and must not satisfy this.

### Data note

One orphan was found and removed: a source titled *The Villainess Reverses The
Hourglass*, 133 chapters, **empty `MihonProviderId` and empty `MihonId`**,
attached to Backstabbed since 2025-02-02 — 1 of 570 source rows. Backed up
first (`Backups/renzo.db.pre-orphan-source-20260810-080901.bak`). It came back,
so something regenerates it; worth a `NOT NULL`/non-empty constraint on those
columns so the write fails loudly at the source rather than silently poisoning
matching later.

## 4. Suggested fixes, roughly in order of value

1. **Re-verify before consolidating transitively.** Only add `transitiveId` to a
   group if the two titles actually pass `AreStringSimilar`. Cheap, local, and
   turns unbounded chaining into a genuine similarity relation. Requiring the
   whole group to be mutually similar (a clique rather than a chain) is stricter
   still, and probably what was intended.

2. **Distrust a row whose metadata disagrees with its group.** A candidate whose
   chapter count differs from the group's by a large factor — 133 vs 228 here —
   or whose cover differs, should not be linked on title alone. Titles are the
   one field sources most often get wrong.

3. **Never let "add" silently become "update".** If the resolved group contains a
   series already in the library, that is a merge into an existing record and
   should be an explicit confirmation, not the default. This is what turned a
   matching mistake into data loss. If the API returned "this will be merged into
   <title>", every client could show it.

4. **Return link provenance in the augment response** — why each source is in the
   group, and what it matched against. The clients could then show it, and this
   would have been obvious rather than a database query.

## 5. Reproducing

Search "The Villainess Reverses the Hourglass" with several English sources
enabled. The result set includes a row carrying Backstabbed's cover and chapter
count under the Hourglass title; the confirm stage then lists sources from both
works. Adding merges into Backstabbed.

## 6. Client side — for completeness

One genuine client bug was found and fixed while chasing this: the add sheet did
not clear `augmented`, `confirmRows` or `stage` when a new search ran, so Add
could submit the *previous* series' payload. That made adds appear to do nothing
and masked this bug entirely. Fixed in
`hub/feature-shiori/.../ui/browse/AddSeriesSheet.kt`; worth porting to
`shiori/clients/android`, where the same code exists.

The client now also refuses to treat a 2xx-with-no-id as success, and verifies
the new id actually appears in the library before closing the sheet.
