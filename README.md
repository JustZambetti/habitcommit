# HabitCommit

A home-screen widget for recording today's metrics — local-first, tap-only, open source. **A tracker, not a to-do list:** each item is a measurement you log with a tap, not a task waiting to be completed. The long-term goal is to keep every recorded value in a git repository you own.

> **Status: early smoke prototype.** Logging works fully offline; GitHub sync is optional and experimental. No account, no telemetry. The PAT is stored only on the phone, encrypted with the Android Keystore.

## What it does today

- **Four widget sizes** on the Android home screen: 4×4 (full plan), 4×2 (compact list), 2×4 (vertical, four metrics) and 2×2 (progress, streak and app shortcut). Controls sit on the right of each row, away from the screen edge.
- **Tap-only logging** — one tap records a value; no keyboard, no form. Android widgets cannot host text input, so the product is built around fast taps.
- **Ratings, not just yes/no.** A metric can be `"type": "rating"` with a `max` (default 5): the widget shows one tappable star per point, so "Focus 3/5" takes one tap. Ratings are stored as numbers in the same journal; any value above zero counts as recorded.
- **A different plan every day.** `habits.json` defines metrics, named plans and a weekly schedule; date overrides swap a plan or add/remove single metrics. The widget resolves *today's* plan at render time.
- **Streak + 14-day heatmap** so progress is visible without opening the app.
- **Material 3 visual language** in the app and the widget: pill-shaped filled/tonal buttons, standard 18dp checkboxes (outlined when empty, filled when done) and 8dp chips, drawn on Glance with the same palette.
- **A small companion app** to review today, edit `habits.json` with validation, clear data and seed demo data. Plans are changed only in `habits.json` — there is no per-day plan picker, because once sync lands the config lives in your GitHub repo.
- **Local first.** Events are appended to a local JSONL journal before anything else happens; widgets re-render immediately.
- **Optional GitHub sync.** Connect a private repo with a fine-grained PAT (only **Contents: Read and write** + **Metadata: Read**). Each recorded value becomes one commit that appends a line to `journal/YYYY-MM/<device>.jsonl`. A durable outbox means taps never wait for the network, and retries deduplicate by event id. The app can also pull `habits.json` from the repo after validating it.

Example config (shipped as `app/src/main/assets/sample_habits.json`):

```json
{
  "schema": 1,
  "timezone": "Europe/Rome",
  "metrics": [
    {"id": "review-pr", "label": "Review PR"},
    {"id": "deep-work", "label": "Deep work 2h"},
    {"id": "client-call", "label": "Chiama cliente"},
    {"id": "reading", "label": "Leggi 20 pagine"},
    {"id": "gym", "label": "Allenamento"},
    {"id": "focus", "label": "Focus", "type": "rating", "max": 5},
    {"id": "energy", "label": "Energia", "type": "rating", "max": 5}
  ],
  "plans": {
    "office": {"label": "Ufficio", "items": ["review-pr", "deep-work", "client-call", "reading", "focus"]},
    "weekend": {"label": "Weekend", "items": ["gym", "reading", "energy"]}
  },
  "schedule": {
    "byWeekday": {"mon": "office", "tue": "office", "wed": "office", "thu": "office", "fri": "office", "sat": "weekend", "sun": "weekend"},
    "overrides": {"2026-11-03": {"plan": "weekend"}, "2026-11-10": {"add": ["gym"], "remove": ["client-call"]}}
  }
}
```

Resolution order: date override (`plan` or `add`/`remove`) → weekday schedule → first plan as default. Metric IDs are stable slugs (`^[a-z0-9][a-z0-9-]{0,63}$`); labels are free text. Metric types are `binary` (default) and `rating`; `max` is allowed only on ratings and must be between 2 and 10. The pre-rating key `checks` is still accepted as a legacy alias for `metrics`.

## Why this exists

Habit trackers with widgets are not rare. This one is aimed at a specific gap:

1. **The metrics should change with real life.** Office, weekend and travel days track different things. A fixed list does not survive a normal week.
2. **Your data should be yours.** The target format is an append-only journal kept in *your* GitHub repo, so history is portable, greppable and backed up by git — not locked in a vendor database.
3. **Logging a value must take one second.** The widget, not the app, is the primary surface.

This is intentionally validated before more is built: see [the plan](.claude/plans) history and the M0 validation gate in the project notes. If people keep the widget but do not care about GitHub, the project pivots to local-only + git export.

## Design

The UI direction combines three of the original mockups:

- **A — clean metric list**: dense, readable, zero decoration.
- **C — streak/heatmap**: motivation visible at a glance.
- **H — dynamic plan + provenance**: the widget shows which plan today uses and what changes tomorrow.

The mockups that led here live in [`design/mocks/`](design/mocks) (open `design/mocks.html` in a browser).

## Privacy and security constraints

Even in smoke scope these are non-negotiable design rules:

- **No telemetry** of any kind. The only network permission is `INTERNET`, used for GitHub sync.
- **Network is append-only and hardcoded.** The client talks only to `https://api.github.com`; cleartext traffic is disabled by the network security config. A PAT can never be sent to another host.
- **Fine-grained PAT** with only **Contents: Read/write** + **Metadata: Read**, encrypted with **Android Keystore AES-256-GCM**. The ciphertext lives in `files/sync/`, excluded from backup/device transfer (`allowBackup="false"` is on too). The token is never logged or written to SharedPreferences.
- **`FLAG_SECURE`** on the sync screen, so the token cannot appear in screenshots or the recents thumbnail.
- **GitHub cannot scope a token to a folder**, only to repositories (and optional permissions). Use a **dedicated data repo** to keep the blast radius small; a repository push ruleset can additionally restrict allowed file paths server-side.
- Widget writes **locally first**; network happens in the background and failures never block a recorded value. Notes are never committed in clear text; commit messages contain only the metric id and date.

## Build and run

Requirements: JDK 17+ (tested on 21), Android SDK with platform 36.

```bash
./gradlew testDebugUnitTest     # unit tests: config validation, plan resolution
./gradlew assembleDebug         # debug APK
./gradlew installDebug          # to a running device/emulator
```

Debug builds use the application id `dev.habitcommit.debug` so a release build can live side by side.

Pin a widget from the app's home screen card, or through the launcher's widget picker (*HabitCommit · Piano / Compatto / Verticale / Focus*).

## Repository layout

```
app/src/main/java/dev/habitcommit/
├── MainActivity.kt            # activity shell
├── HabitCommitApp.kt          # manual DI container
├── data/                      # config parser, plan resolver, repository, event log, demo seeder
├── sync/                      # GitHub client, encrypted token, outbox, WorkManager sync
├── ui/                        # Compose app: today, config editor, sync setup, heatmap
└── widget/                    # Glance widgets (4×4, 4×2, 2×4, 2×2), actions, midnight refresh
app/src/test/                  # JVM unit tests
design/                        # mockups (A–J + combined) and screenshot scripts
```

## Roadmap

| Milestone | Scope |
|---|---|
| **Now** | Local smoke prototype + optional GitHub sync: widgets, dynamic plans, config editor, outbox, tests |
| Next | Validation with 8–12 developers (M0 gate); sync hardening (multi-device merge, retry UX) |
| Then | Repo template + dashboard Action, config round-trip in both directions |
| Later | F-Droid release, notes, history views, possible iOS |

Non-goals for now: text input inside the widget (impossible on both platforms), cloud accounts, telemetry, iOS.

## Contributing

Bug reports, mockup critiques and use-case stories are as useful as code right now. Please read [`CONTRIBUTING.md`](CONTRIBUTING.md). Security issues: see [`SECURITY.md`](SECURITY.md).

## License

Application code: [Apache-2.0](LICENSE). The user-facing repo template and schema will use permissive terms separately (MIT / CC0) so people can fork their own plans freely.
