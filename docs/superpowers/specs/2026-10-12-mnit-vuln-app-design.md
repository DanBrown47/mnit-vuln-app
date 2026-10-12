# MNIT Vuln App — Design Spec

Date: 2026-10-12
Status: Approved for planning

## Purpose

A deliberately vulnerable training target for a hands-on security session at
MNIT. Students act as attackers. The exercise teaches two linked skills:

1. **Static analysis** of an Android `.apk` with MobSF / jadx — find a
   hardcoded API credential and a set of insecure configuration flags.
2. **Dynamic exploitation** of the backend — use the leaked credential to
   reach the API, then forge identity to read another user's secrets.

Success = a student installs/decompiles the APK, extracts the hardcoded
Bearer token, calls the API impersonating user 1 (admin), and reads the flag:

```
flag{This is often harder than real world NGL.}
```

The name "harder than real world NGL" is deliberate irony: the only hard step
is reading the APK; the privilege escalation itself is trivial.

## Non-goals

- Not production software. No real user data, no secrets of value.
- No defensive hardening — insecurity is the feature, scoped to this target.
- No changes to the pre-existing application already running on the VPS.

## Concept: "Tea" app

Social-confession clone. A user signs up, sees other usernames, and sends /
receives short private messages called "tea". Each user's received tea is
meant to be private to them. User 1 (admin) holds the flag as a piece of tea.

## Architecture

Two artifacts in one repo, plus deploy tooling.

```
vuln_app/
  app/        # Android app (Kotlin + Gradle) -> produces the .apk
  server/     # Flask + sqlite3 API and web landing page
  deploy/     # systemd unit, nginx server block, teardown cron, deploy.sh
  docs/       # this spec, and the generated implementation plan
  README.md   # build, run, deploy, exploit walkthrough, MobSF findings list
```

### Trust model (the intended vulnerability)

- Every `/api/*` request must carry `Authorization: Bearer <STATIC_TOKEN>`.
  The token is identical for every install and is **hardcoded in the APK**.
  This is the app-level gate — a student cannot talk to the API without first
  extracting it from the app. (MobSF finding + decompile step.)
- Identity is taken from the `X-User-Id` request header and **trusted without
  verification**. The server never checks that the caller owns that id.
  This is the privilege-escalation vulnerability: set `X-User-Id: 1` to read
  the admin's tea.

The two combine into one attack chain: decompile -> get token -> flip header.

## Component: Android app (`app/`)

Native Kotlin, built with the Gradle wrapper and the SDK at `~/Android/Sdk`.

### Screens (each shows the heading banner "for MNIT Use - Vuln App")

1. **Auth** — username + password; register or login.
2. **Users** — list of all usernames with their numeric ids; tap to send tea.
3. **My Tea** — tea addressed to the logged-in user (admin sees the flag here).
4. **Send Tea** — choose recipient, write a message, submit.

### Networking

- Base URL `https://android.danwand.in`.
- `Authorization: Bearer <STATIC_TOKEN>` on all calls.
- `X-User-Id: <my id>` sent for identity after login.
- HTTPS, **no certificate pinning**; `network_security_config` trusts
  user-installed CAs so Burp interception works.

### Deliberate static vulnerabilities (so MobSF reports findings)

- Hardcoded static Bearer token and other secret strings in source.
- `android:usesCleartextTraffic="true"`.
- `network_security_config.xml` trusting user CAs, no pins.
- `android:debuggable="true"`.
- `android:allowBackup="true"`.
- One **exported** activity with no permission guard.
- Credentials (username, password, token) stored in plaintext
  SharedPreferences.
- Sensitive values written to logcat via `Log.d`.
- `minifyEnabled false`, no ProGuard / obfuscation.
- MD5 used in one trivial spot (weak-crypto finding).

### Design

Neo-brutalist + modular typography. Thick `--ink` borders, hard offset
shadows (no blur), raw rectangular blocks, no rounded corners. Tall condensed
display face for the heading and section titles; grotesque / mono for body.

Palette:

| Token | Hex |
|---|---|
| `--ink` | `#0B0B0C` |
| `--paper` | `#EDE7DC` |
| `--oxblood` | `#8A1C1C` |
| `--crimson` | `#C0271E` |
| `--blush` | `#E9D6D1` |
| `--electric` | `#2A2DF0` |

## Component: Backend (`server/`)

Flask + sqlite3. Single `app.py`, with `schema.sql` and `seed.py`.

### Endpoints

All require the static Bearer token. Identity from `X-User-Id` where noted.

| Method | Path | Body / params | Behavior |
|---|---|---|---|
| POST | `/api/register` | `{username, password}` | create user, return `{user_id}` |
| POST | `/api/login` | `{username, password}` | verify, return `{user_id}` |
| GET | `/api/users` | — | list `[{id, username}]` |
| GET | `/api/tea` | header `X-User-Id` | return tea whose recipient = `X-User-Id` (no ownership check — the vuln) |
| POST | `/api/tea` | `{to_user_id, body}` + `X-User-Id` | insert tea from caller to recipient |
| GET | `/` | — | neo-brutalist web landing / health page, same heading |

### Data

- `users(id, username, password)` — passwords stored **plaintext**
  (additional finding + realistic).
- `tea(id, from_user_id, to_user_id, body, created_at)`.

### Seed (`seed.py`)

- User id 1 = `admin`, seeded with a tea containing the flag.
- A handful of decoy users with filler tea so enumeration feels real.

### Exploit walkthrough (documented in README)

1. Install or decompile the APK; MobSF / jadx reveals `<STATIC_TOKEN>`.
2. `GET /api/users` with the token — enumerate ids, note admin = 1.
3. `GET /api/tea` with `Authorization: Bearer <token>` and `X-User-Id: 1`.
4. Response contains the flag. (Burp + user CA is an equivalent path.)

## Deployment (`deploy/`)

Target VPS: `android.danwand.in` (`31.42.125.200`), deployed over SSH as root.

### Isolation (hard requirement: do not disturb the existing app)

- The vuln app runs as its **own** systemd unit (e.g. `mnit-vulnapp.service`)
  under gunicorn, bound to a **distinct local port** chosen to avoid the
  existing service.
- Its **own** nginx server block answers `server_name android.danwand.in` and
  proxies to that port. Other server blocks / sites are left untouched.
- TLS via certbot for `android.danwand.in` only.
- sqlite db lives under the vuln app's own directory.

### DNS

Already pointed to the VPS. Verify resolution at deploy; do not modify.

### Self-teardown

A root cron entry (or systemd timer) stops **only** the vuln service at
**2026-10-14 23:00** server wall-clock time:

```
systemctl stop mnit-vulnapp.service
systemctl disable mnit-vulnapp.service
```

Confirm the server timezone at deploy so the trigger fires at the intended
local time; adjust the cron time accordingly. The existing app and nginx
itself are not stopped.

### Secrets handling

VPS root password and any tokens are used only in live SSH/deploy actions.
They are never written into the repo, committed config, `.env` files, or
logs. The APK's static Bearer token is intentionally in the app source — that
is the exercise — but it is a throwaway value with no real privilege.

## Verification

- Build `.apk` with the Gradle wrapper on this machine.
- Run the APK through dockerized MobSF; confirm the expected findings appear.
- Run the server locally; prove the chain with `curl`:
  - without token -> 401
  - with token, `X-User-Id: 1` -> response contains the flag.
- After deploy: confirm `https://android.danwand.in` serves the landing page,
  the API answers, the existing app still works, and the teardown cron is
  installed for 2026-10-14 23:00.

## Open items (resolved at deploy time, not blockers for planning)

- Exact distinct port for the vuln service (pick after inspecting the box).
- Server timezone, to set the cron wall-clock correctly.
- Confirm what the existing app/nginx config looks like before adding ours.
