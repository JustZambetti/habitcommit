#!/usr/bin/env python3
"""Read and validate a HabitCommit data repository.

Standard library only. Three commands:

    validate <repo> [--journal DIR]     check habits.json against the app rules
    today <repo> [--date YYYY-MM-DD]    resolve the plan for a date
    stats <repo> [--days N]             summarise journal activity

<repo> is a local checkout of the data repo: a directory containing habits.json
and (usually) a journal/ folder. The journal directory can also be a sibling of
the checkout when the app uses a custom HabitCommit path whose parent is the
repo root; pass --journal to point at it explicitly.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import defaultdict
from datetime import date, datetime, timedelta
from pathlib import Path

ID_RE = re.compile(r"^[a-z0-9][a-z0-9-]{0,63}$")
WEEKDAYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]
MIN_RATING_MAX = 2
MAX_RATING_MAX = 10
DEFAULT_RATING_MAX = 5


def fail(message: str) -> None:
    print(f"error: {message}", file=sys.stderr)
    raise SystemExit(2)


def load_config(repo: Path) -> dict:
    path = repo / "habits.json"
    if not path.is_file():
        fail(f"{path} not found — is this a HabitCommit data repo?")
    try:
        with path.open(encoding="utf-8") as handle:
            data = json.load(handle)
    except json.JSONDecodeError as exc:
        fail(f"habits.json is not valid JSON: {exc}")
    if not isinstance(data, dict):
        fail("habits.json must contain a JSON object")
    return data


def metric_type(metric: dict) -> str:
    return metric.get("type", "binary")


def validate(config: dict) -> tuple[list[str], dict]:
    """Return (errors, normalised config). Mirrors HabitsConfig.kt."""
    errors: list[str] = []

    if config.get("schema") != 1:
        errors.append(f"schema must be 1 (found {config.get('schema')!r})")

    timezone_name = str(config.get("timezone", "")).strip()
    if not timezone_name:
        errors.append("timezone is required")
    else:
        try:
            from zoneinfo import ZoneInfo

            ZoneInfo(timezone_name)
        except Exception:
            # Missing tzdata on some Windows installs is not proof of a bad zone.
            if "/" not in timezone_name:
                errors.append(f"invalid timezone: {timezone_name}")

    metrics: dict[str, dict] = {}
    seen_ids: set[str] = set()
    raw_metrics = config.get("metrics", config.get("checks"))
    if not isinstance(raw_metrics, list):
        errors.append("metrics must be a list of objects")
        raw_metrics = []
    for index, raw in enumerate(raw_metrics):
        where = f"metrics[{index}]"
        if not isinstance(raw, dict):
            errors.append(f"{where} must be an object")
            continue
        metric_id = str(raw.get("id", "")).strip()
        if not ID_RE.match(metric_id):
            errors.append(f"{where}.id is not a valid slug: {metric_id!r}")
            continue
        if metric_id in seen_ids:
            errors.append(f"duplicate metric id: {metric_id}")
            continue
        seen_ids.add(metric_id)
        label = str(raw.get("label", metric_id)).strip() or metric_id
        kind = metric_type(raw)
        if kind not in ("binary", "rating"):
            errors.append(f"{where}.type must be binary or rating (found {kind!r})")
            continue
        if kind == "binary" and "max" in raw:
            errors.append(f'{metric_id}: "max" is only allowed on rating metrics')
            continue
        maximum = DEFAULT_RATING_MAX
        if kind == "rating" and "max" in raw:
            maximum = raw.get("max")
            if not isinstance(maximum, int) or isinstance(maximum, bool):
                errors.append(f"{metric_id}: max must be an integer 2..10")
                continue
            if not MIN_RATING_MAX <= maximum <= MAX_RATING_MAX:
                errors.append(f"{metric_id}: max must be between 2 and 10 (found {maximum})")
                continue
        metrics[metric_id] = {"id": metric_id, "label": label, "type": kind, "max": maximum}
    if not metrics:
        errors.append("at least one metric is required")

    plans: dict[str, dict] = {}
    raw_plans = config.get("plans")
    if not isinstance(raw_plans, dict):
        errors.append("plans must be an object")
        raw_plans = {}
    for plan_id, raw in raw_plans.items():
        if not ID_RE.match(plan_id):
            errors.append(f"plans: invalid plan id {plan_id!r}")
            continue
        if not isinstance(raw, dict):
            errors.append(f"plans.{plan_id} must be an object")
            continue
        items = raw.get("items", [])
        if not isinstance(items, list) or not all(isinstance(item, str) for item in items):
            errors.append(f"plans.{plan_id}.items must be a list of metric ids")
            items = []
        deduped: list[str] = []
        for item in items:
            if item not in metrics:
                errors.append(f'plans.{plan_id} uses unknown metric "{item}"')
            elif item not in deduped:
                deduped.append(item)
        label = str(raw.get("label", plan_id)).strip() or plan_id
        plans[plan_id] = {"id": plan_id, "label": label, "items": deduped}
    if not plans:
        errors.append("at least one plan is required")

    schedule = config.get("schedule") or {}
    by_weekday: dict[str, str] = {}
    if not isinstance(schedule, dict):
        errors.append("schedule must be an object")
        schedule = {}
    raw_weekdays = schedule.get("byWeekday") or {}
    if not isinstance(raw_weekdays, dict):
        errors.append("schedule.byWeekday must be an object")
        raw_weekdays = {}
    for key, plan_id in raw_weekdays.items():
        if key not in WEEKDAYS:
            errors.append(f'schedule.byWeekday: invalid day "{key}"')
        elif plan_id not in plans:
            errors.append(f'schedule.byWeekday.{key} uses unknown plan "{plan_id}"')
        else:
            by_weekday[key] = plan_id

    overrides: dict[str, dict] = {}
    raw_overrides = schedule.get("overrides") or {}
    if not isinstance(raw_overrides, dict):
        errors.append("schedule.overrides must be an object")
        raw_overrides = {}
    for date_text, raw in raw_overrides.items():
        try:
            date.fromisoformat(date_text)
        except ValueError:
            errors.append(f'invalid override date "{date_text}"')
            continue
        if not isinstance(raw, dict):
            errors.append(f"override {date_text} must be an object")
            continue
        if "plan" in raw:
            # Like the app: "plan" wins and any add/remove keys are ignored.
            plan_id = raw["plan"]
            if plan_id not in plans:
                errors.append(f'override {date_text} uses unknown plan "{plan_id}"')
                continue
            overrides[date_text] = {"plan": plan_id}
        else:
            add = raw.get("add") or []
            remove = raw.get("remove") or []
            if not isinstance(add, list) or not isinstance(remove, list):
                errors.append(f"override {date_text}: add/remove must be lists")
                continue
            for metric_id in add:
                if metric_id not in metrics:
                    errors.append(f'override {date_text} adds unknown metric "{metric_id}"')
            for metric_id in remove:
                if metric_id not in metrics:
                    errors.append(f'override {date_text} removes unknown metric "{metric_id}"')
            overrides[date_text] = {"add": list(add), "remove": list(remove)}

    return errors, {
        "timezone": timezone_name,
        "metrics": metrics,
        "plans": plans,
        "byWeekday": by_weekday,
        "overrides": overrides,
    }


def resolve(normalised: dict, day: date) -> tuple[dict, str, list[str]] | None:
    plans = normalised["plans"]
    override = normalised["overrides"].get(day.isoformat())
    plan = None
    source = "default"
    if override and "plan" in override:
        plan = plans.get(override["plan"])
        source = "date override"
    if plan is None:
        plan_id = normalised["byWeekday"].get(WEEKDAYS[day.weekday()])
        if plan_id in plans:
            plan = plans[plan_id]
            source = "weekday"
    if plan is None and plans:
        plan = next(iter(plans.values()))
        source = "default plan"
    if plan is None:
        return None
    items = list(plan["items"])
    # Like the app: an override with "plan" ignores any add/remove keys.
    if override and "plan" not in override:
        removed = set(override.get("remove", []))
        items = [item for item in items if item not in removed]
        for metric_id in override.get("add", []):
            if metric_id in normalised["metrics"] and metric_id not in items:
                items.append(metric_id)
        source += " (modified)"
    return plan, source, items


def parse_local_date(tz_name: str) -> date:
    try:
        from zoneinfo import ZoneInfo

        return datetime.now(ZoneInfo(tz_name)).date()
    except Exception:
        return date.today()


def journal_events(journal_dir: Path) -> list[dict]:
    events: list[dict] = []
    if not journal_dir.is_dir():
        return events
    for path in sorted(journal_dir.rglob("*.jsonl")):
        with path.open(encoding="utf-8") as handle:
            for line in handle:
                line = line.strip()
                if not line:
                    continue
                try:
                    event = json.loads(line)
                except json.JSONDecodeError:
                    continue
                if not isinstance(event, dict) or event.get("v", 1) != 1:
                    continue
                if event.get("type", "checkin") != "checkin":
                    continue
                try:
                    event["_date"] = date.fromisoformat(event["date"])
                    event["_at"] = datetime.fromisoformat(
                        event["at"].replace("Z", "+00:00")
                    )
                except (KeyError, TypeError, ValueError):
                    continue
                events.append(event)
    return events


def cmd_validate(args: argparse.Namespace) -> int:
    config = load_config(args.repo)
    errors, normalised = validate(config)
    if errors:
        print("INVALID habits.json")
        for error in errors:
            print(f"  - {error}")
        return 1
    print(
        "OK: {metrics} metrics, {plans} plans, {weekdays} weekday slots, "
        "{overrides} overrides".format(
            metrics=len(normalised["metrics"]),
            plans=len(normalised["plans"]),
            weekdays=len(normalised["byWeekday"]),
            overrides=len(normalised["overrides"]),
        )
    )
    return 0


def cmd_today(args: argparse.Namespace) -> int:
    config = load_config(args.repo)
    errors, normalised = validate(config)
    if errors:
        for error in errors:
            print(f"error: {error}", file=sys.stderr)
        return 1
    day = date.fromisoformat(args.date) if args.date else parse_local_date(normalised["timezone"])
    resolved = resolve(normalised, day)
    if resolved is None:
        print("error: no plan resolved", file=sys.stderr)
        return 1
    plan, source, items = resolved
    print(f"{day.isoformat()} - {plan['label']} ({source})")
    for metric_id in items:
        metric = normalised["metrics"][metric_id]
        scale = f"0..{metric['max']}" if metric["type"] == "rating" else "0/1"
        print(f"  {metric['label']} [{metric_id}] {metric['type']} {scale}")
    return 0


def cmd_stats(args: argparse.Namespace) -> int:
    config = load_config(args.repo)
    errors, normalised = validate(config)
    if errors:
        for error in errors:
            print(f"error: {error}", file=sys.stderr)
        return 1
    journal_dir = Path(args.journal) if args.journal else args.repo / "journal"
    if not journal_dir.is_dir():
        fail(f"journal directory not found: {journal_dir}")

    today = parse_local_date(normalised["timezone"])
    since = today - timedelta(days=args.days - 1)
    latest: dict[tuple[date, str], dict] = {}
    for event in journal_events(journal_dir):
        if event["_date"] < since or event["_date"] > today:
            continue
        key = (event["_date"], str(event.get("check", "")))
        previous = latest.get(key)
        if previous is None or event["_at"] >= previous["_at"]:
            latest[key] = event

    per_metric: dict[str, dict] = defaultdict(lambda: {"days": 0, "values": []})
    days_with_data: set[date] = set()
    for (event_date, metric_id), event in latest.items():
        value = event.get("value", 0)
        if not isinstance(value, int):
            continue
        entry = per_metric[metric_id]
        entry["days"] += 1
        entry["values"].append(value)
        if value > 0:
            days_with_data.add(event_date)

    print(f"Last {args.days} days: {since.isoformat()} .. {today.isoformat()}")
    print(f"Days with at least one recorded value: {len(days_with_data)}")
    if not per_metric:
        print("No journal events in range.")
        return 0
    for metric_id in normalised["metrics"]:
        metric = normalised["metrics"][metric_id]
        entry = per_metric.get(metric_id)
        if not entry:
            print(f"  {metric['label']}: no events")
            continue
        active = sum(1 for value in entry["values"] if value > 0)
        line = f"  {metric['label']}: {active}/{entry['days']} days recorded"
        if metric["type"] == "rating":
            rated = [value for value in entry["values"] if value > 0]
            if rated:
                line += f", average {sum(rated) / len(rated):.1f}/{metric['max']}"
        print(line)
    unknown = sorted(set(per_metric) - set(normalised["metrics"]))
    for metric_id in unknown:
        print(f"  (unknown metric in journal: {metric_id})")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="HabitCommit data repo helper")
    subparsers = parser.add_subparsers(dest="command", required=True)

    validate_parser = subparsers.add_parser("validate", help="validate habits.json")
    validate_parser.add_argument("repo", type=Path)
    validate_parser.set_defaults(func=cmd_validate)

    today_parser = subparsers.add_parser("today", help="resolve the plan for a date")
    today_parser.add_argument("repo", type=Path)
    today_parser.add_argument("--date", help="YYYY-MM-DD (default: today in the config timezone)")
    today_parser.set_defaults(func=cmd_today)

    stats_parser = subparsers.add_parser("stats", help="summarise the journal")
    stats_parser.add_argument("repo", type=Path)
    stats_parser.add_argument("--days", type=int, default=30)
    stats_parser.add_argument("--journal", help="journal directory, if not <repo>/journal")
    stats_parser.set_defaults(func=cmd_stats)

    args = parser.parse_args()
    if getattr(args, "days", 1) < 1:
        parser.error("--days must be >= 1")
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
