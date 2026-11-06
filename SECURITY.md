# Security policy

## Supported versions

Only the latest published build is supported while the project is pre-1.0.

## Reporting a vulnerability

Please **do not open a public issue** for security problems. Report privately via GitHub Security Advisories ("Report a vulnerability" on the repository's Security tab) or by contacting the maintainer listed on the repository profile.

Include what you did, what happened, and the impact you believe it has. We aim to acknowledge reports within a few days and to ship a fix or mitigation before public disclosure.

## Scope and threat model

HabitCommit is **local-first with optional GitHub sync**:

- The only network traffic is GitHub sync, to `https://api.github.com`, using a token the user provides. There is no telemetry and no third-party SDK.
- The `INTERNET` permission exists solely for that sync. Cleartext HTTP is disabled (`usesCleartextTraffic="false"` plus a network security config), and the API host is hardcoded in the client.
- The sync client does not follow HTTP redirects, so the token is only ever sent to `api.github.com`.
- The PAT should be fine-grained, limited to **Contents: Read and write** + **Metadata: Read**, and scoped to a single data repository.
- The token is encrypted with AES-256-GCM using a non-exportable Android Keystore key and stored as ciphertext in the app's private `files/sync/` directory. It is excluded from backup and device transfer; `android:allowBackup="false"` is set as well.
- The token is never logged, never written to `SharedPreferences`, never placed in intents or commit messages, and never shown in the UI again after saving; entering a new token replaces it.
- `FLAG_SECURE` is enabled while the sync screen is open, so the token cannot appear in screenshots or the recents thumbnail.
- Notes are never included in commit messages. Commit messages contain only the date range and number of entries.

## Where data lives

Everything is stored in the app's private files directory:

- `habits.json` — the plan (normally a copy of the repository's file).
- `journal/YYYY-MM/<deviceId>.jsonl` — one append-only line per recorded value; device-scoped so two devices never rewrite the same file.
- `sync/outbox.jsonl` — values waiting to be uploaded.
- `sync/token.bin` — the encrypted PAT.

Anyone with root or `adb` access to the device can read app-private data, so treat it like any other private app data. Uninstalling the app deletes all local copies; the journal copies in the GitHub repository remain under the user's control.

## GitHub side

GitHub tokens cannot be restricted to a folder inside a repository, only to repositories and permissions. Use a **dedicated data repository** to keep the blast radius small. A repository push ruleset can additionally restrict which paths are writable. To revoke access, delete or expire the PAT in GitHub settings; disconnecting in the app only removes the local copy.

Reports that show any of these constraints being violated are in scope.
