---
name: habitcommit-data
description: Create, edit, validate and read a HabitCommit data repository (habits.json plus the append-only journal). Use when asked to change metrics, plans, weekday schedules or date overrides, to create a new HabitCommit repo, or to analyse recorded habit values.
---

# HabitCommit data repositories

A HabitCommit data repository is the git repo the HabitCommit Android app syncs
to. It holds exactly two things:

```
<repo>/
├── habits.json                       # the plan: metrics, plans, schedule (root, fixed name)
└── journal/
    └── YYYY-MM/
        └── <device-id>.jsonl        # append-only recorded values, one file per device per month
```

The journal directory defaults to `journal/` but can be different if the user
changed "HabitCommit path" in the app. Confirm it with the user or infer it from
the existing tree; do not create a second journal directory.

The app polls `habits.json` on every resume and at the start of each sync run,
validates it, replaces its local copy and repaints the home-screen widgets. So
**editing habits.json on the default branch is the way to change what the user
tracks** — there is no in-app plan editor. The journal is written by the app
only.

## Hard rules

1. **Never rewrite, reorder, truncate or delete journal lines.** The journal is
   append-only; the app dedupes by the `id` field and an agent edit can corrupt
   history. If the user asks to "delete a day", explain that the app can only
   append corrections and ask before doing anything.
2. **Never invent journal events.** Only the app records values.
3. **Metric ids are permanent.** Events reference them by id. To rename, change
   `label`; changing `id` orphans history unless you add the new metric and keep
   the old one (or ask the user how to map values).
4. **Keep habits.json valid.** Run the validator before committing (see below).
5. **Do not put secrets or notes in the repo.** No PATs, no personal notes.
6. **Commit only data changes** in a dedicated commit; do not mix unrelated code.

## Locate the repository

- If the current working directory contains `habits.json`, that is it.
- Otherwise ask the user for the local clone path or `owner/repo`. A repo name
  alone is not enough: never guess.
- There may be several repos; pick the one with `habits.json` at its root.
- Work on the branch the user names; default to the repo default branch.

## habits.json format (schema 1)

```json
{
  "schema": 1,
  "timezone": "Europe/Rome",
  "metrics": [
    {"id": "review-pr", "label": "Review PR"},
    {"id": "focus", "label": "Focus", "type": "rating", "max": 5}
  ],
  "plans": {
    "office": {"label": "Office", "items": ["review-pr", "focus"]},
    "weekend": {"label": "Weekend", "items": ["focus"]}
  },
  "schedule": {
    "byWeekday": {
      "mon": "office", "tue": "office", "wed": "office",
      "thu": "office", "fri": "office", "sat": "weekend", "sun": "weekend"
    },
    "overrides": {
      "2026-11-03": {"plan": "weekend"},
      "2026-11-10": {"add": ["review-pr"], "remove": ["focus"]}
    }
  }
}
```

### Fields

| Field | Rules |
|---|---|
| `schema` | Must be `1`. |
| `timezone` | Required IANA zone (e.g. `Europe/Rome`). Defines what "today" means for the app. Keep the user's existing zone unless asked. |
| `metrics[]` | Non-empty array. `status` is a measurement, not a task. |
| `metrics[].id` | Required, unique. Slug: `^[a-z0-9][a-z0-9-]{0,63}$` (lowercase, digits, hyphens). |
| `metrics[].label` | Free text; defaults to the id if empty. This is what the user sees. |
| `metrics[].type` | `binary` (default) or `rating`. |
| `metrics[].max` | Only on `rating`, integer 2–10, default 5. Forbidden on `binary`. |
| `plans` | Non-empty object. Every plan: `label` (optional) and `items` (array of existing metric ids, duplicates collapse). |
| `schedule.byWeekday` | Optional object; keys `mon tue wed thu fri sat sun`; values must be existing plan ids. |
| `schedule.overrides` | Optional object; keys are `YYYY-MM-DD`; each value is either `{"plan": <planId>}` or `{"add": [<metricIds>], "remove": [<metricIds>]}`. |

Unknown top-level keys are ignored by the app. The legacy top-level key
`checks` is accepted as an alias for `metrics`, but always write `metrics` in a
new file.

### Plan resolution (what the app shows on a given day)

1. If there is an override for the date with `plan`, that plan wins.
2. Otherwise the weekday schedule (`byWeekday`) for that day of week.
3. Otherwise the **first plan in the `plans` object** (JSON object order).
4. Then `add`/`remove` from a `"add"/"remove"` override are applied on top;
   removing a metric that is not in the plan and adding one already present are
   no-ops. `plan` and `add`/`remove` cannot be combined in one override.

The app shows a check as recorded when its value is > 0: binary values are 0/1,
rating values 0..max (tapping the current star clears it to 0).

## Reading the journal

Each line is one recorded value (JSONL, one object per line):

```json
{"v":1,"type":"checkin","id":"<uuid>","check":"focus","date":"2026-11-02","value":4,"at":"2026-11-02T20:15:03Z","tz":"Europe/Rome","device":"<uuid>"}
```

- `check` is a metric id; `date` is the local date in `tz`; `value` is the
  value the user chose.
- Multiple events for the same `date` + `check` can exist (the user re-tapped).
  **The event with the latest `at` wins** for display; earlier ones are history.
- `v` is the format version (currently 1); skip unknown lines rather than
  failing. Ignore lines that do not parse.
- Files are per device and per month: two phones never write the same file, so
  reading "the user's day" means merging all files.

Prefer the bundled tool over ad-hoc parsing. It ships in the HabitCommit repo
at `agent/habitcommit.py`; if the skill was installed elsewhere, use the full
path to that checkout (or a copy next to the data repo):

```bash
python <habitcommit>/agent/habitcommit.py validate <repo>                  # schema + reference checks
python <habitcommit>/agent/habitcommit.py today <repo> [--date YYYY-MM-DD] # resolved plan for a date
python <habitcommit>/agent/habitcommit.py stats <repo> [--days 30]         # per-metric activity
```

It uses only the Python standard library and exits non-zero on invalid input.
`validate` must pass before you commit a change to `habits.json`. If Python or
the tool is unavailable, validate by hand against the rules above.

## Common edits

**Add a metric.** Append to `metrics` with a new unique slug; add it to the
plans that should track it.

**Remove a metric.** Remove it from every plan item list and from `byWeekday`
overrides. If you also delete it from `metrics`, its journal history stays but
can no longer be displayed; prefer keeping the metric and removing it from the
plans if history matters.

**Rename.** Change `label` only. Never change `id`.

**Change the weekday schedule.** Edit `schedule.byWeekday` values; keys must be
existing plans.

**One-off day.** Add `schedule.overrides["YYYY-MM-DD"]`. Use `{"plan": ...}` to
swap the whole plan for that date, or `{"add": [...], "remove": [...]}` for a
small deviation. Overrides can stay in the file after the date passes; the app
only reads the matching date. Clean them up when they are old if the user asks.

**Change a rating scale.** Changing `max` on an existing rating does not rewrite
past values; historical values above the new max are clamped by the app. Mention
this to the user.

**Create a repository from scratch.** Create `habits.json` with `schema: 1`, a
timezone, at least one metric and one plan; create an empty `journal/` folder
(it can stay empty until the app writes). Do not invent journal data. Then tell
the user to connect the repo in the app with a fine-grained PAT that has
**Contents: Read and write** + **Metadata: Read**.

## Committing

- Commit message for data edits: `habitcommit: <what changed> <YYYY-MM-DD>`.
  Examples: `habitcommit: add reading metric 2026-11-02`,
  `habitcommit: office plan for December 2026-12-01`,
  `habitcommit: date override 2026-12-24 2026-12-01`.
- Do not amend or force-push unless the user explicitly asks.
- After editing, tell the user the change reaches the phone on the next app
  resume or sync run (throttled to once every 5 minutes); "Sync now" forces it.

## Safety

- Do not touch app source code when asked to change plans.
- Do not create or modify repositories on the user's account unless explicitly
  authorized.
- If the user's request conflicts with the hard rules (e.g. "rewrite the
  journal", "change the metric id"), stop and explain the consequence instead
  of guessing.
