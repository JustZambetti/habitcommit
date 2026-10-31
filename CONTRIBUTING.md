# Contributing to HabitCommit

Thanks for looking. This project is at an early, deliberately narrow stage: the goal is to validate that a tap-only, plan-aware home-screen checklist is worth building before adding sync complexity. Feedback about whether you would actually use it is as valuable as a pull request.

## Ways to help right now

- **Try the smoke APK** and tell us what breaks on your launcher/OEM (Pixel, Samsung, Xiaomi, etc.).
- **Report a use case**: what checks change from day to day for you? What would today's plan look like on a workday, a weekend, a trip?
- **Critique the mockups** in [`design/mocks/`](design/mocks) — readability, contrast, tap targets, text scaling.
- **File issues** with: device, Android version, launcher, steps to reproduce, screenshot, and relevant `adb logcat` lines. Never paste tokens or personal notes.

## Development setup

- JDK 17 or newer (JDK 21 is what CI will use).
- Android SDK with platform 36 and build-tools matching AGP 8.10.x.
- No Google Play Services required; the app must keep working without GMS.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Unit tests cover the config parser, validation messages and plan resolution (weekday schedule, date overrides, local overrides, DST boundaries). Add a test for every behavior change in `data/`.

## Ground rules

1. **No new network calls without a design issue.** The smoke build has no `INTERNET` permission on purpose.
2. **No analytics, crash reporting SDKs or proprietary dependencies.** F-Droid compatibility is a hard requirement.
3. **Check-ins write locally first.** A slow or failing network must never block a tap.
4. **Secrets never enter the UI tree or logs.** Future PAT handling goes through the Keystore module only.
5. **Widget layouts stay cheap.** Use text/vector; avoid bitmaps and keep Glance containers under the 10-children limit.
6. **Keep user-facing strings in Italian for now**, via `strings.xml` where practical. Localization comes with the first release.

## Commits and pull requests

- Small, focused PRs with a clear "why" in the description.
- Include a screenshot or short screen recording for UI changes.
- Run `./gradlew testDebugUnitTest` before opening the PR.
- Sign off your commits (`git commit -s`) to certify the Developer Certificate of Origin. No CLA.
- By contributing you agree your changes are licensed under Apache-2.0.

## Code of conduct

Be kind, assume good intent, critique the work not the person. Harassment or exclusionary behavior is not welcome; maintainers may remove comments and block accounts.
