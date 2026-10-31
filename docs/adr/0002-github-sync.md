# ADR 0002 — Optional GitHub sync, one commit per value

- **Status:** accepted (experimental)
- **Date:** 2026-10-31
- **Context:** The product promise is "your data is yours". The target format is an append-only journal in a repository the user owns. Logging must never wait for the network, and a phone tap must work offline for days.

## Decision

Sync is **optional and off until configured**. When configured:

- The user connects a **private** repository with a **fine-grained PAT** that has only **Contents: Read and write** and **Metadata: Read**.
- The token is encrypted with **Android Keystore AES-256-GCM** and stored in `files/sync/token.bin`, excluded from backup and device transfer. It is never logged and never written to SharedPreferences.
- The client talks only to the hardcoded `https://api.github.com`; cleartext is disabled by the network security config.
- Every recorded value is appended **locally first**. The same event is queued in a durable outbox (`files/sync/outbox.jsonl`) and uploaded in the background by a WorkManager `CoroutineWorker`.
- Each value becomes **one commit** that appends one line to `journal/YYYY-MM/<deviceId>.jsonl`. The client does GET (sha) → append → PUT, with an event-id dedupe check and up to four conflict retries.
- Commit messages contain only the metric id and the date. Notes are never uploaded.
- The app can pull `habits.json` from the repository, validate it, and save it locally. It does not push `habits.json`.

## Why a file per device

Two devices must be able to write without rebase conflicts. A per-install, per-month JSONL file means writes touch different paths and stay append-only; a local read merges the journal by event id. Cross-device merging of overlapping days is a later milestone.

## Why not a token scoped to a folder

GitHub fine-grained PATs scope to **repositories and permissions**, not to a path inside a repository. The app can only promise to write to its configured journal path. Users who want a smaller blast radius should use a **dedicated data repository**, and can add a repository **push ruleset** that allows changes only under `journal/**`.

## Consequences

- The smoke APK declares `INTERNET`; earlier "no network permission" statements no longer apply.
- Taps stay local and fast: the outbox write is a few milliseconds, the network runs in a background worker with exponential backoff.
- Sync failures are visible in the app (pending count + last message) and never block recording.
- End-to-end testing requires a user-provided PAT and repository; the project never creates repositories or pushes to an account on its own.
