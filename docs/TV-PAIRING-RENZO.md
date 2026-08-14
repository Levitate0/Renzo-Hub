# TV pairing for Renzo — server spec

**For:** the Renzo server chat (`/opt/zurg-stack/fullstack-arr`, Node 20 / Express 4 / TS).
**Companion:** `TV-PAIRING-SHIORI.md` — same protocol, different credential.
**Date:** 2026-08-04.

The Hub's client half is built and in `:core`
(`core/.../tv/TvPairing.kt`). It requests a code, polls, and hides the option
entirely when the endpoints 404 — so shipping this is safe and unilateral.

---

## 1. Why

Typing a password on a TV remote is miserable. For the users who most need TV
access — someone whose only screen *is* the television — there is no phone to
fall back to, so "set it up on your phone" is not an answer.

The alternative we already have is running the server with **authentication
disabled** so users pick a profile (`X-Renzo-User`). That works, but it is
server-wide: nobody gets a password, including the account with admin rights.

**Pairing removes that trade.** Authentication stays fully on, and a TV still
signs in without anyone typing a password into it.

---

## 2. Protocol

Three endpoints. This is the OAuth device-authorisation shape deliberately —
well understood, known security properties.

### `POST /api/auth/tv/code`

Unauthenticated. Body: `{ "deviceName": "Living Room TV" }`

```json
{
  "userCode": "BKPT-4Q7M",
  "deviceCode": "<64+ bits of entropy, opaque>",
  "verificationUrl": "https://your-server/tv",
  "expiresIn": 600,
  "interval": 5
}
```

- `userCode` is **displayed** on the TV and never used to authenticate.
- `deviceCode` is **held secretly** by the TV and never displayed. Only the
  pairing request knows both — that separation is the whole security model.
- `verificationUrl` is served by this instance (§4). Absolute, so the TV can
  print it verbatim.

### `POST /api/auth/tv/poll`

Unauthenticated. Body: `{ "deviceCode": "…" }`

| State | Response |
|---|---|
| waiting | `200 {"status":"pending"}` or `428` |
| approved | `200 {"status":"approved", …credential}` + `Set-Cookie: fsa_session=…` |
| rejected | `200 {"status":"denied"}` |
| expired/unknown | `200 {"status":"expired"}`, or `404`/`410` |

The client treats a network error as `pending` — a TV on flaky wifi must not
lose an approval the user already granted. It gives up at `expiresIn`.

**On approval, issue exactly the session a normal login issues.** Set the
`fsa_session` cookie as `POST /api/auth/login` does; the Hub captures it from
`Set-Cookie` the same way. Nothing downstream should be able to tell a TV
session apart from a typed one — pairing replaces the typing, not the session
model.

**A `deviceCode` is single-use.** Once it returns `approved`, retire it.

### `POST /api/auth/tv/approve`

**Authenticated** (normal session). Body: `{ "userCode": "BKPT-4Q7M" }`.
Called by the approval page. Binds the pending request to `req.user` and marks
it approved. Also needs a deny path.

---

## 3. Security requirements

These are the parts that make it safe rather than a backdoor:

- **`userCode` must be rate-limited per IP and per code.** It is short enough to
  guess by brute force otherwise. Lock the pending request after ~5 failed
  approval attempts.
- **Alphabet without ambiguity** — no `0`/`O`, `1`/`I`/`l`. Someone is reading
  this off a TV across a room.
- **~10 minute expiry**, and sweep expired requests.
- **Approval requires an authenticated session.** The approver's identity is the
  identity granted; never accept a username in the approve body.
- **Show what is being approved**: the `deviceName` the TV supplied, and the
  requesting IP. A user approving "Living Room TV" from their own LAN is the
  normal case; anything else should look wrong.
- **`deviceCode` never appears in a URL** — POST body only, so it stays out of
  logs and referrers.
- **Rate-limit `/code`** so nobody can farm pending requests.

## 4. The approval page

Served by the instance itself at `verificationUrl` (suggest `/tv`), inside the
container, no external dependency — this has to work on a LAN-only install with
no internet.

Minimum: a code field, a submit, and the signed-in user's name so it is obvious
*which* account is being granted. If not signed in, send to the normal login
and return here afterwards. On success say which device was approved; on failure
distinguish "wrong code" from "expired".

It should work on a phone browser — that is where most people will use it.

## 5. Device list and revocation

"One-time per TV" means the credential persists, so users need to see and revoke
it. Add paired devices to the account settings surface: device name, when
paired, last seen, and a revoke that kills that session specifically.

This matters more than usual here: a TV in a shared space stays signed in
indefinitely by design.

## 6. What the client already does

`TvPairingClient` in `:core`:

- `requestCode(deviceName)` → `TvCode?`, **null on 404** so the UI hides the
  option on servers without support
- `awaitApproval(code)` → polls at the server's `interval`, honours `expiresIn`,
  treats transient failures as `pending`, and returns the raw approval body for
  the calling half to parse

The Hub sends a `deviceName` derived from the device model. Nothing else is
assumed about your implementation.

## 7. Not in scope

Renzo has no refresh-token concept — its session cookie lifetime is server-side,
so a paired TV stays signed in exactly as long as any other session. If TV
sessions should outlive browser ones, that is a cookie-lifetime decision on your
side, not a protocol change.
