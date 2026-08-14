# TV pairing for Renzo Shiori — server spec

**For:** the Shiori server chat (`/opt/zurg-stack/Rensaio`, .NET 8 / ASP.NET, SQLite).
**Companion:** `TV-PAIRING-RENZO.md` — same protocol, different credential.
**Date:** 2026-08-04.

Read §2 of the Renzo document for the protocol; it is identical and not repeated
here. This covers what differs on the .NET side, which is mostly the credential
and the refresh token.

---

## 1. Why this matters more on this side

The manga reader is the half most likely to be used by someone **without a
phone** — a child whose only screen is the television. That user cannot "set it
up on their phone", and cannot reasonably type a password with a D-pad.

Today the only no-typing option is running with authentication disabled so
profiles are picked from a list (`X-Renzo-User`). That is server-wide: enabling
it for a child removes passwords for every account on the instance.

**Pairing removes that trade** — `RuntimeSecuritySettings` stays fully on.

---

## 2. What differs from the Renzo spec

### The credential is a token pair, not a cookie

On approval, `POST /api/auth/tv/poll` must return **exactly what
`POST /api/auth/login` with `rememberMe: true` returns**:

```json
{
  "status": "approved",
  "token": "<JWT>",
  "user": { … }
}
```

**plus the `refresh_token` cookie**, set the same way login sets it.

That cookie is the point. The access token lives `SessionExpirationHours`
(24 by default); the refresh cookie lives `RememberMeExpirationDays` (90). A TV
that only got a 24-hour token would need re-pairing every day, which defeats the
feature entirely.

The Hub already persists the refresh cookie in its encrypted store and trades it
for a fresh access token on any non-auth 401 — that landed on 2026-08-04. So a
paired TV stays signed in for the full remember-me window with no further
interaction, provided the cookie is issued.

**Treat pairing as `rememberMe: true` unconditionally.** A TV is the one place
where "don't remember me" makes no sense.

### Refresh rotation still applies

The refresh endpoint rotates the token. A paired TV is a normal client
afterwards and goes through the same single-flight refresh path as any other —
nothing special is needed, but do not exempt TV sessions from rotation.

### Approval endpoint auth

`POST /api/auth/tv/approve` sits behind the usual `[Authorize]`. The approving
user's claims are the identity granted; never read a username from the body.

---

## 3. Storage

A pending request needs: `userCode`, `deviceCode` (hashed at rest, like a
password — it is a bearer secret), `deviceName`, requesting IP, created/expires
timestamps, status, and the approving user id once set.

SQLite is fine; these are short-lived rows. Sweep expired ones — a table of dead
pending codes is a slow leak and a brute-force surface.

For the persistent side, a paired device is just a refresh-token row with a
device label attached. If refresh tokens are already tracked per session, reuse
that rather than inventing a parallel concept.

## 4. The approval page

Served by the instance at `verificationUrl` (suggest `/tv`), inside the
container, no CDN or external font — LAN-only installs are the common case here.

`RenzoFrontend` is a Next.js static export served from `wwwroot.zip`, so this is
a new route in that app rather than a server-rendered page. It needs to work on a
phone browser; that is where it will mostly be used.

Show the `deviceName` and requesting IP before approving, and name the account
being granted.

## 5. Device list and revocation

Surface paired devices in account settings — name, paired-at, last-seen, revoke.
Revoking must invalidate that device's refresh token specifically, not all of
them: signing every device out because one TV was retired is its own bug.

This is more important on this side than on Renzo's, because a 90-day refresh
cookie on a TV in a shared room is a long-lived credential by design.

## 6. Client status, and one honest gap

`TvPairingClient` in the Hub's `:core` is shared by both halves and already
handles request/poll/expiry/transient-failure. The credential parsing is
per-half, so this side reads `token` + `user` and stores them exactly as the
normal login path does.

**However:** the Hub does not currently reach Shiori on a TV at all. The picker
never appears on leanback and there is no route into the manga half — because
Shiori's UI has **zero** D-pad-focusable call sites against ~356 touch-only
ones, and 51 text fields.

So building this endpoint does not by itself put manga on a television. It
removes the *authentication* blocker, which is the part that cannot be solved
client-side. The remaining work is making Connect → user-select → library →
reader focusable, which is roughly 15% of that UI and is a separate decision.

Worth building anyway: the approval page and endpoints are device-agnostic and
are the long-pole item, and the same flow is useful for any keyboard-hostile
client later.
