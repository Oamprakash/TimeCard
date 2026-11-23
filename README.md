# Time Card

Offline-first Android time card. Kotlin, Jetpack Compose, Room, Hilt. Single module.

## Run

```bash
./gradlew testDebugUnitTest          # engine tests (JVM, no device)
./gradlew connectedDebugAndroidTest  # repository/Room tests (device or emulator)
./gradlew installDebug
```

Open in Android Studio, let it sync, and accept any suggested version bumps (AGP, Kotlin/KSP, Compose BOM). The versions in `gradle/libs.versions.toml` are a known-compatible set, not necessarily the newest.

## Layout

```
domain/model    RuleSet, RulePresets (US federal / California / Germany / Custom), WorkShift, summaries
domain/engine   HoursCalculator  – hours, daily + weekly OT, double time, gross pay, flags
                AutoBreakPolicy  – auto-deduct decided at clock-out
                PayPeriods       – weekly / biweekly / semi-monthly / monthly
data/db         Room entities, DAOs, converters, mappers (schema exported to app/schemas)
data/repository TimeCardRepository – clock in/out, breaks, job limit, period summary
data/settings   DataStore: week start, pay period, onboarding, remove-ads cache
clock           ClockController (single entry point for punches), ClockNotifier, receivers
ui/clock        Clock tab: job chips, big Clock in/out button, break button, live timer, totals
ui              Bottom-nav shell (Timesheet/Reports in Sprint 3, Settings in Sprint 5)
```

## Sprint status

- [x] Sprint 1: core engine (Room schema, presets, calculator, tests)
- [x] Sprint 2: clock flow (Clock tab, clock in/out, breaks, auto-deduct, ongoing notification, reboot recovery)
- [ ] Sprint 3: timesheet and reports
- [ ] Sprint 4: export and monetization
- [ ] Sprint 5: polish and ship

## Clock flow (Sprint 2)

- All state changes go through `ClockController`, whether they come from the Clock tab, the notification buttons or the restore receiver. It updates Room and then re-posts or removes the notification, so the two can't disagree.
- The ongoing notification uses the system chronometer and doesn't need a foreground service. While working, it counts worked time with breaks excluded. During a break, it counts the length of the current break.
- After a reboot, an app update, or a clock or time-zone change, `ClockRestoreReceiver` re-posts the notification from what's stored. On Android 14 and later, users can swipe the notification away; opening the app or the Clock tab brings it back.
- On Android 13 and later, the app asks for notification permission **after** the first clock-in, so the punch is never delayed.
- Punches are de-duplicated: the button is disabled while a write is in flight, and `clockIn` is idempotent.

### Manual test checklist (Galaxy M02s)

1. On first launch, add a job and clock in. The timer should start in under 1 s.
2. Use Break / End break from both the app and the notification. The timer pauses during a break.
3. Force-stop the app, reopen it, and check the timer is still correct.
4. Reboot the phone. The notification should come back without opening the app, though it can take a minute on Samsung.
5. Clock out from the notification. The notification disappears, and the Clock tab shows today's and this week's totals.
6. With the Germany preset, work past 6 h without a break. The "Break rule not met" warning should appear.

## Engine rules (as implemented)

- Times are stored as UTC instants plus a zone ID. Elapsed time is calculated from the instants, so shifts that cross a DST change get the correct number of hours.
- A shift counts toward the local date it **started** on.
- Daily OT/DT is applied first. Weekly OT then converts only the *regular* hours above the weekly threshold, going through the workweek day by day in order. No hour is counted twice.
- Sick/holiday/leave hours are paid at the regular rate and do **not** count toward OT, as under FLSA.
- Each job is calculated independently with its own rules.
- For a pay period that starts mid-week, the repository also loads shifts from the start of that workweek (`HoursCalculator.loadFrom`), so weekly OT is correct for semi-monthly and monthly periods.
- Germany: there's no OT pay rule. Warnings appear when work exceeds 6 h with under 30 min of breaks, exceeds 9 h with under 45 min, or exceeds 10 h in a day.
- Auto-deduct is worked out at clock-out and stored as a `Break` row with `isAutoDeducted = true`. Changing the rule later doesn't change past pay.
- Money uses `BigDecimal`, and rates are stored as TEXT. Gross pay is rounded half-up to cents.

## Deviations from the spec

- **Rules per job instead of a shared `RulePreset` row.** Built-in presets live in code. Creating a job copies one into its own `rule_sets` row, so editing one job's rules never changes another job's. `presetKind` records which preset it came from.
- **Rule thresholds are stored in minutes**, not hours, so there's no floating point.
- **Added fields:** two break-warning pairs on `RuleSetEntity`, and `Shift.workDate`, an indexed local start date used for range queries.

## Known limitations (documented, not bugs)

- California's 7th-consecutive-day rule is not implemented.
- The break-warning check uses recorded breaks only. Gaps between two shifts on the same day aren't counted as breaks.
- Auto-deduct is a single tier, e.g. 30 min after 6 h.
