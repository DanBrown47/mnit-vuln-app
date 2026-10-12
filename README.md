# MNIT Vuln App — "Tea"

> ⚠️ **Intentionally vulnerable software.** This application was built as a
> deliberately insecure training target for an **Android Security Session at
> MNIT (Malaviya National Institute of Technology), Jaipur, Rajasthan**.
> It exists so students can practice finding and exploiting common mobile and
> API flaws in an authorized setting. **Do not deploy it as real software, do
> not host it on a network you do not own, and do not reuse any of its
> patterns in production.** Every "vulnerability" here is on purpose.

A small "Tea" social app: sign up, see other users, and send/read short private
messages ("tea"). It ships as two parts:

- **`app/`** — a native Android app (Kotlin). The learning target for *static*
  analysis (MobSF / jadx): hardcoded credentials and insecure configuration.
- **`server/`** — a Flask + sqlite3 backend. The learning target for *dynamic*
  exploitation: broken access control.

## The intended exercise

1. **Decompile the APK** (or run it through MobSF). The app talks to the API
   using a **hardcoded static Bearer token** — find it in the app's resources.
2. **Enumerate users** — `GET /api/users` with that token. Admin is user `1`.
3. **Forge identity** — the server trusts the `X-User-Id` request header with no
   ownership check (an IDOR / broken object-level authorization). Send
   `GET /api/tea` with `X-User-Id: 1` and read the **admin's** private tea.
4. The admin's tea contains the flag.

Equivalent path: install the app, proxy it through Burp with a user-installed
CA (the app disables cert pinning and trusts user CAs), and replay the request
with a changed `X-User-Id`.

See [`docs/mobsf-expected-findings.md`](docs/mobsf-expected-findings.md) for the
full list of planted static findings and the lessons they map to.

## Layout

```
app/        Android app (Kotlin, Gradle)  -> produces the APK
server/     Flask + sqlite3 API + landing page
deploy/     systemd unit, nginx block, teardown cron, deploy script
docs/       design spec, implementation plan, expected-findings reference
```

## Run the backend

```bash
cd server
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python seed.py tea.db          # create + seed the database
.venv/bin/python app.py                  # dev server on :8099
.venv/bin/python -m pytest               # 19 tests
```

Quick proof once it's running:

```bash
TOKEN=mnit_app_2026_bearer_9f3c1a
curl -s localhost:8099/api/users -H "Authorization: Bearer $TOKEN"
curl -s localhost:8099/api/tea   -H "Authorization: Bearer $TOKEN" -H "X-User-Id: 1"
```

## Build the APK

```bash
cd app
./gradlew assembleDebug          # needs the Android SDK; set sdk.dir in local.properties
# output: app/app/build/outputs/apk/debug/app-debug.apk
```

## Deploy

See [`deploy/README.md`](deploy/README.md). The deploy is intentionally isolated
(its own service + nginx server block) so it can run beside an unrelated app on
the same host without disturbing it, and it installs a cron that stops the
service at the end of the session window.

## For MNIT Use — Vuln App

Every screen and page carries this heading. The app is for the MNIT session and
authorized practice only.
