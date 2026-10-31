# ADR 0001 — Widget direction: combine A, C and H

- **Status:** accepted
- **Date:** 2026-10-25
- **Context:** Ten high-fidelity mockups (A–J) were produced from the product plan. The goal was to pick one direction for the tap-only widget before writing UI code.

## Decision

The widget follows the combined direction **A + C + H**:

- **A — clean metric list** for the body: dense, readable rows, whole row is the tap target, timestamps on the right.
- **C — streak and heatmap** for motivation: 14-day proportional heatmap plus streak count, computed per plan rather than per repo day.
- **H — dynamic plan and provenance** for identity: today's plan label, a "PIANO · habits.json" source pill, tomorrow's plan preview, and explicit *Configura* / *Apri app* actions. The plan is edited in `habits.json` only; the earlier per-day *Cambia piano* picker was removed so the schedule stays the single source of truth.

Adaptations per widget size:

| Size | Layout |
|---|---|
| 4×4 | Header + source pill, 5 metrics, 14-day heatmap + streak, tomorrow row, two actions |
| 4×2 | Compact header, 4 metrics, 12-day heatmap + streak, no buttons |
| 2×4 | Vertical header, 4 metrics, 9-day heatmap + streak, full-width tonal *Apri app* action |
| 2×2 | Header, circular progress, 9-day heatmap, streak, single filled *APRI APP* button |

Checkbox placement: rows put the checkbox on the right and the label on the left, so tap targets stay away from the screen edge and the system back-swipe zone. The whole row remains tappable.

Metrics are a set, not a queue: there is no "next item", so no *FATTO* button. The 2×2 and 2×4 sizes are status overviews with an app shortcut. **Rating** metrics (`type: "rating"`, `max` default 5) use the same row, with one tappable star per point; tapping the star equal to the current value clears the rating, so a mis-tap is one tap away from recovery. A rating counts as recorded from 1 upward for progress, streak and heatmap.

## Styling

The app and the widget follow the Material 3 component specs, with the project palette mapped onto M3 color roles:

- **Buttons**: 40dp filled and filled-tonal pills (fully rounded), medium-weight labels; the widget's *Configura* action is the tonal variant.
- **Checkboxes**: 18–20dp, 2dp corner radius, 2dp outline when unchecked, solid primary fill with a checkmark when checked. Glance 1.2 has no border modifier, so the outline is drawn as a rounded box behind an inset fill.
- **Chips**: 8dp corners for the *PIANO · habits.json* source pill.
- **Ratings**: filled ★ / outlined ☆ glyphs in the primary color and the outline color; each star is its own tap target (14–17dp cells in the widget, 32dp in the app).
- **App**: standard Material 3 `Checkbox`, `Button`, `OutlinedButton` and `TextButton`; shape tokens (4/8/12/16/28dp) via `MaterialTheme.shapes`.
- **Type weights**: titles and labels use medium weight; metric labels use regular body weight, carrying emphasis through the trailing checkbox.

## Consequences

- The widget communicates the product's differentiator (plans that change daily) without requiring the app.
- A tap repaints every pinned size with `GlanceAppWidget.update(context, id)` in-process. `updateAll` enqueues one expedited WorkManager `SessionWorker` per widget, which after an idle period took ~2.4s on the emulator; the direct call renders in-process in ~0.5s cold and ~50ms while a Glance session is alive.
- Glance containers cap at 10 direct children, so the heatmap is chunked into balanced rows and cells carry their own trailing gap. Any future addition of nodes must preserve that budget.
- A/C/H compete for vertical space in 4×4; the current balance accepts generous whitespace on tall launchers rather than shrinking typography.
- Mockup provenance: `design/mocks.html` (A–J), `design/mocks/combined-sizes.png` (combined), exported as widget preview images in `app/src/main/res/drawable-nodpi/`.
