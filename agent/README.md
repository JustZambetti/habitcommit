# Agent support

Everything an AI agent needs to create, edit and read a HabitCommit data
repository (`habits.json` + the append-only journal).

| File | Purpose |
|---|---|
| [`skill/SKILL.md`](skill/SKILL.md) | The skill: format rules, journal semantics, edit recipes, safety rules. |
| [`habitcommit.py`](habitcommit.py) | Standard-library CLI: `validate`, `today`, `stats`. |
| [`habits.schema.json`](habits.schema.json) | JSON Schema for editors (the app stays the source of truth). |
| [`template/habits.json`](template/habits.json) | Minimal starter config for a new data repo. |

## Use it from a Claude session

Pick whichever fits the session:

**1. Clone or open this repo — just point the agent at the skill.** Paste:

> Read `agent/skill/SKILL.md` and use it for any HabitCommit data-repo task.
> Validate with `python agent/habitcommit.py validate <repo>` before committing.

**2. Install it as a personal skill (recommended, once).** Copy the folder:

```bash
mkdir -p ~/.claude/skills/habitcommit-data
cp agent/skill/SKILL.md ~/.claude/skills/habitcommit-data/SKILL.md
```

On Windows that is `C:\Users\<you>\.claude\skills\habitcommit-data\SKILL.md`.
New Claude Code sessions discover it automatically; the agent then triggers on
requests like "add a reading metric to my HabitCommit repo". Keep a copy of
`habitcommit.py` somewhere reachable (or in the data repo) — the skill refers
to `agent/habitcommit.py`, so adjust the path if needed.

**3. Attach it to the data repo itself.** Copy `SKILL.md` into the data repo as
`CLAUDE.md` (or `AGENTS.md`), or keep `agent/` in that repo. Any Claude session
opened on the data repo then has the rules in context. Do not commit secrets
or phone tokens there.

**4. No installation.** Paste the contents of `SKILL.md` directly into the
conversation.

## The tool

```bash
python agent/habitcommit.py validate <repo>                  # exit 1 + every error if invalid
python agent/habitcommit.py today <repo> [--date YYYY-MM-DD] # resolved plan and its source
python agent/habitcommit.py stats <repo> [--days 30] [--journal DIR]
```

`<repo>` is a local checkout containing `habits.json`. `--journal` is only
needed when the journal lives outside the checkout (a custom "HabitCommit
path"). Python 3.9+; no dependencies.

`stats` merges every device file, keeps the latest event per metric and day
(after the latest `at`), and ignores unparseable journal lines the same way the
app does.

## Create a new data repo

```bash
mkdir habitcommit-data && cd habitcommit-data && git init
mkdir journal
cp ../habitcommit/agent/template/habits.json habits.json
python ../habitcommit/agent/habitcommit.py validate .
```

Push it, then in the Android app open **Settings → Sync GitHub**, paste a
fine-grained PAT with **Contents: Read and write** + **Metadata: Read** and
pick the repo. The app downloads `habits.json` and starts writing the journal
on its daily sync.

## For agents

- `habits.json` is the only supported way to change the plan: the app has no
  editor and overwrites its local copy from the default branch on every resume.
- The journal is append-only and written only by the app. Never edit, reorder,
  truncate or delete lines, and never invent events.
- Metric ids are permanent; journal events reference them.
- Validate before committing and use commit messages like
  `habitcommit: add reading metric 2026-11-02`.
