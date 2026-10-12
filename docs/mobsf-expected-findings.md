# MNIT Vuln App — Expected Findings (facilitator reference)

The APK is deliberately insecure. When a student runs it through MobSF / jadx,
these are the planted issues and the lesson each maps to.

| # | Finding | Where | Lesson |
|---|---------|-------|--------|
| 1 | Hardcoded API Bearer token | `res/values/strings.xml` → `api_token` = `mnit_app_2026_bearer_9f3c1a` | Secrets in an APK are not secret. This token is the only gate to the API — extract it, reach the server. |
| 2 | Cleartext traffic permitted | `AndroidManifest.xml` `usesCleartextTraffic="true"` + `network_security_config.xml` | App allows plaintext HTTP. |
| 3 | Trusts user-installed CAs, no pinning | `res/xml/network_security_config.xml` (`<certificates src="user"/>`, no `<pin-set>`) | Burp/mitmproxy with a user CA can read & modify traffic. |
| 4 | Debuggable | `AndroidManifest.xml` `android:debuggable="true"` | App can be attached to a debugger; runtime inspection. |
| 5 | Backup allowed | `AndroidManifest.xml` `android:allowBackup="true"` | `adb backup` can pull app data off the device. |
| 6 | Exported activity, no guard | `AndroidManifest.xml` `MyTeaActivity exported="true"` | Other apps can launch it directly. |
| 7 | Plaintext credential storage | `Store.kt` — username/password/user_id in SharedPreferences | Credentials sit unencrypted in `/data/data/.../shared_prefs`. |
| 8 | Sensitive data in logs | `Api.kt`, `Store.kt` — `Log.d` prints token, creds, headers | logcat leaks secrets. |
| 9 | No obfuscation | `app/build.gradle` `minifyEnabled false` | Decompiled code reads like source; token trivial to find. |
| 10 | Weak crypto (MD5) | `Api.kt` `weakId()` uses MD5 | MD5 flagged as broken. |

## The intended attack chain (dynamic)

1. Decompile / MobSF the APK → read `api_token` (finding #1).
2. `GET /api/users` with `Authorization: Bearer <token>` → enumerate ids; admin = 1.
3. `GET /api/tea` with that token **and** `X-User-Id: 1` → the server trusts the
   header with no ownership check (broken object-level authorization).
4. Response contains `flag{This is often harder than real world NGL.}`.

Equivalent path: install the APK, proxy through Burp with a user CA (findings
#2/#3), and replay the request changing `X-User-Id` to `1`.
