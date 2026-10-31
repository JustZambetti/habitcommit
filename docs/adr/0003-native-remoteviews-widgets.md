# ADR 0003 — Widgets are native RemoteViews, not Glance

Status: accepted (supersedes the Glance implementation detail of ADR 0001).

## Context

The first widget implementation used Glance 1.2. Tapping a row took 3.3–3.6 s to
repaint from a cold process, and ~0.6 s warm. Measured breakdown on the emulator:

- process start to `Application.onCreate`: ~1.2–1.8 s, including WorkManager's
  `androidx.startup` initializer and Glance class loading;
- Glance action dispatch: ~0.15 s;
- publish + Compose update: ~0.2–0.9 s;
- session compose flush via a WorkManager `SessionWorker`: ~0.8–1.5 s.

For a tap-only tracker, a multi-second delay after unlocking the phone is a
product bug: the whole point is recording a value in one second.

## Decision

Draw all four widget sizes with plain `android.widget.RemoteViews`.

- `WidgetActionReceiver` (a plain `BroadcastReceiver`) handles tap actions
  synchronously: append the journal event, then call `AppWidgetManager.updateAppWidget`
  for every pinned widget before `onReceive` returns.
- A `SCREEN_ON`/`USER_PRESENT` dynamic receiver warms the process before unlock
  where possible; implicit broadcasts cannot reach manifest receivers, so this
  covers all taps except the first after boot.
- WorkManager is no longer in the tap path. Its automatic startup initializer is
  removed from the manifest; sync initialises it lazily through `WorkBootstrap`.
- The midnight repaint uses an inexact `AlarmManager` alarm instead of WorkManager.

## Consequences

- Measured tap latency on the emulator: ~0.9 s for the first tap after the
  process was killed (mostly framework process start), ~35 ms when warm.
- No Compose/Glance runtime in the widget process path; the debug APK is smaller
  and the cold start lighter.
- Widget UI code is more manual: text styles live in layout XML, and dynamic
  lists are built with `addView`. Two pitfalls are documented in CONTRIBUTING:
  only `@RemoteView` classes may be inflated, and `removeAllViews` is required
  before adding children because launchers may reapply `RemoteViews`.
- Interactive state (hover, ripples) is limited to what `RemoteViews` supports;
  the app UI remains Compose/Material 3.
