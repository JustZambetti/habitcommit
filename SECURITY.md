# Security policy

## Supported versions

Only the latest published build is supported while the project is pre-release.

## Reporting a vulnerability

Please **do not open a public issue** for security problems. Report privately via GitHub Security Advisories ("Report a vulnerability" on the repository's Security tab) or by contacting the maintainer listed on the repository profile.

Include what you did, what happened, and the impact you believe it has. We aim to acknowledge reports within a few days and to ship a fix or mitigation before public disclosure.

## Scope and threat model (current smoke build)

The smoke build is **local-only**:

- No `INTERNET` permission.
- No authentication tokens or accounts.
- `android:allowBackup="false"`, so app data is not included in cloud backups or device transfer.
- No telemetry, no third-party SDKs.

Data lives in the app's private files directory as `habits.json`, `local_plan_overrides.json` and `journal/YYYY-MM/local.jsonl`. Anyone with root/adb access to the device can read it — treat it like any other private app data; do not store secrets there.

## Security constraints for future work (planned GitHub sync)

These are design requirements, not aspirations:

- Fine-grained PAT with only **Contents: Read and write** and **Metadata: Read**.
- Token encrypted with AES-256-GCM using a non-exportable Android Keystore key; ciphertext in DataStore; excluded from backup and device transfer.
- Token is never logged, never placed in intents, commit messages, screenshots or analytics.
- `FLAG_SECURE` on token-entry screens; cleartext traffic disabled; GitHub base URL hardcoded (no arbitrary hosts).
- Private repository by default; notes are not committed in plain text; disconnect wipes the token and explains how to revoke the PAT.
- Widget and app continue to work fully offline; a sync error is surfaced as state, never as data loss.

Reports that show any of these constraints being violated are in scope.
