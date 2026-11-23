# Punchd

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
ui/timesheet    Week/month timesheet, day sheet (shifts, sick/holiday/leave per job)
ui/shift        Add/edit shift: job, date, start/end (night shifts), breaks, note, delete
ui/reports      Pay-period report per job, totals per currency, pay-period + week-start picker
ui/onboarding   Country -> rule preset, first job, pay period (first run only)
ui/settings     Jobs & rules, pay period, reminders, language, backup, Remove ads, privacy, about
ui/job          Job editor: rate, currency, colour, schedule, overtime rules, auto-break, archive
widget          Glance home-screen widget: live timer, today's hours, Clock in/out (ad-free)
backup          Backup model + tiny JSON reader/writer (pure Kotlin); data/backup maps Room <-> backup
export          Export model, CSV + XLSX writers (pure Kotlin), PDF renderer, exporter + share sheet
ads             UMP consent, ads gate, banner/native views, interstitial (capped) + rewarded
billing         Play Billing 8: one-time "remove_ads", acknowledge, restore, refunds
ui              Bottom-nav shell, shared formatting
```

## Sprint status

- [x] Sprint 1: core engine (Room schema, presets, calculator, tests)
- [x] Sprint 2: clock flow (Clock tab, clock in/out, breaks, auto-deduct, ongoing notification, reboot recovery)
- [x] Sprint 3: timesheet and reports (week/month timesheet, add/edit shifts, day marks, pay-period report per job)
- [x] Sprint 4: export and monetization (PDF/CSV/Excel, UMP consent, AdMob with caps, Remove ads)
- [x] Sprint 5: polish and ship (onboarding, Settings, reminders + German warnings, widget, backup, icon, store docs)

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
7. Timesheet: add a 22:00–06:00 shift with a 02:00–02:30 break. It should show 7h 30m on the start day.
8. Mark a day as sick and check the Reports tab: paid leave is added to gross pay, but not to overtime.
9. Reports → CSV opens the share sheet. PDF and Excel first show the "watch a video" prompt, then the test ad, then share. Open the .xlsx in Google Sheets.
10. Save a manual edit twice within 5 minutes. At most one interstitial should appear. Clocking in or out never shows an ad.
11. Settings → Remove ads with a license tester account. All ads disappear, a 4th job can be added, and PDF exports without a prompt.
12. Fresh install: onboarding → Germany → first job → finish. The Clock tab opens with the Germany rules.
13. Widget: add it, clock in from it, and check the timer ticks and the app's notification appears.
14. Backup: export, clear app data, import. All jobs and shifts come back.

The full pre-release list is in `docs/launch-checklist.md`.

## Timesheet and reports (Sprint 3)

- **Manual entry rules** (`ShiftDrafts`): times are typed as they read on the clock.
  - An end time at or before the start means the next day, so 22:00–06:00 is a night shift. A break time earlier than the start is also on the next day.
  - A shift is rejected if it's in the future, if a break is outside the shift or overlaps another break, if the breaks are as long as the shift, or if it overlaps another shift.
- **Editing the running shift:** only the start, job and note can be changed. Its end and breaks stay under the Clock tab. Saving or deleting re-syncs the notification.
- **Day marks:** sick, holiday or paid leave, per job and per day. Paid hours default to the weekly schedule ÷ 5, or 8 h if no schedule is set.
- **Scheduled hours** are an optional per-job weekly figure. Ranges that aren't a full week (months, pay periods) are pro-rated by the number of days.
- **Reports** show one card per job, with each job's own overtime rules. Totals are added up per currency.
- **Schema note:** `jobs.scheduledMinPerWeek` was added while the database is still at version 1, before release. Uninstall any earlier debug build before installing this one.

## Export and monetization (Sprint 4)

- **Exports** cover one pay period across all jobs, in two parts: a shifts sheet (one row per shift plus sick/holiday/leave rows) and a summary sheet (hours and gross pay per job, with totals per currency). Hours are decimal (7.50).
  - **CSV:** free. RFC 4180 with a UTF-8 BOM. On German-locale devices it uses `;` and decimal commas so Excel opens it correctly. Cells starting with `= + - @` are neutralised to prevent formula injection.
  - **Excel (.xlsx):** built by a small writer with no dependencies (`XlsxWriter`) instead of fastexcel. It writes real dates and numbers, a bold, frozen header row and two sheets, and has been checked with openpyxl.
  - **PDF:** Android `PdfDocument` on A4. The table header repeats on each page, and the pay disclaimer is at the end.
  - Files go to `cache/exports` (only the latest is kept) and are shared through a `FileProvider` with the Android share sheet.
- **Consent:** the Google UMP SDK runs on every start. Ads are requested and the Mobile Ads SDK is initialised only once `canRequestAds()` is true. If the region requires it, Settings shows "Privacy options".
- **Ads**, following the spec's placement table. They are all off when consent hasn't been given or Remove ads is owned.
  - Clock tab: an adaptive banner below everything, hidden while a dialog is open.
  - Timesheet: one native row after every 10th day with entries.
  - Reports: a banner at the bottom.
  - PDF/Excel export: one rewarded ad, which the user always opts into through a prompt. If no ad loads (offline, no fill), the export still happens.
  - After saving a manual edit: an interstitial, at most 1 per 5 minutes and 2 per day (`InterstitialPolicy`).
  - Never on clock in/out, the notification or its actions.
- **Remove ads:** a one-time product with the ID `remove_ads`. It's acknowledged, restored on every start (which also removes the unlock after a refund), and unlocks no ads, unlimited jobs and free PDF/Excel export.

### Before release

1. AdMob: create the app plus banner, native, rewarded and interstitial units. Put the IDs in `~/.gradle/gradle.properties`, not in the repo:
   ```
   ADMOB_APP_ID=ca-app-pub-xxxxxxxx~yyyyyyyy
   ADMOB_BANNER_ID=ca-app-pub-xxxxxxxx/1111111111
   ADMOB_NATIVE_ID=ca-app-pub-xxxxxxxx/2222222222
   ADMOB_REWARDED_ID=ca-app-pub-xxxxxxxx/3333333333
   ADMOB_INTERSTITIAL_ID=ca-app-pub-xxxxxxxx/4444444444
   ```
   Debug builds always use Google's test IDs. A release build without these properties falls back to the test IDs and prints a warning.
2. AdMob → Privacy & messaging: publish a GDPR message and a US state regulations message. UMP shows them.
3. Play Console: create the in-app product `remove_ads` at $2.99 / €2.99 and add license testers.
4. Data safety form: the app itself collects nothing, but the AdMob SDK collects device or other IDs for advertising.

## Polish and ship (Sprint 5)

- **Onboarding** (first run only). The user picks a country: US federal, California, Germany or Other. That sets the rule preset, currency, week start and usual pay period. Then they add a first job (rate, schedule, optional auto-break) and confirm the pay period. Anyone who already has jobs skips it.
- **Settings:**
  - Jobs and rules: a full editor for every threshold and multiplier, plus auto-break and archive.
  - Pay period and week start.
  - Reminders: "still clocked in?" after 6/8/10/12 h, and rule warnings on or off.
  - Language: Android 13+ per-app language for English and German (`locales_config.xml`).
  - Backup, Remove ads, privacy options, privacy policy, version.
- **Reminders** (`ReminderPlanner` + `Reminders`):
  - Alerts: "Still clocked in?" (with a Clock out button), plus the German "more than 6 h without 30 min of breaks", "more than 9 h without 45 min" and "over 10 h today". The 10 h limit counts all of the day's shifts.
  - Each alert fires once per shift. One WorkManager job sleeps until the next threshold, with no polling and no foreground service.
  - During a break, worked time is frozen, so ending the break re-plans.
- **Widget:** a live timer (a real `Chronometer` in RemoteViews, so it ticks without updates), today's hours, and a Clock in/out button for the selected or first job. `ClockController` refreshes it on every change.
- **Backup:** one JSON file with everything, shared through the share sheet.
  - Import uses the file picker and asks for confirmation, then replaces all data in one transaction. A damaged, foreign or newer-version file is rejected before anything changes.
  - Money is kept as exact decimals and instants as epoch milliseconds.
- **Icon:** an adaptive launcher icon with a monochrome layer for Android 13 themed icons. The 512 px store icon is in `docs/store/`.
- **Store and release docs:** `docs/store-listing.md` (EN/DE text with the spec keywords; the app name is still open), `docs/privacy-policy.md` plus `docs/privacy.html` to host, and `docs/launch-checklist.md` (Data safety answers, release steps, device tests).

## Loading, empty and error states

| Screen | Loading | Empty | Error / retry |
|---|---|---|---|
| Clock | Skeleton (chips, timer, button, totals) | First-job form | Full-screen "Couldn't load" + Retry; a failed punch shows a snackbar with Retry and never changes stored state |
| Timesheet | Skeleton (totals + day rows) | "No shifts this week/month" + Add shift | Full-screen Retry; a failed sick/holiday save shows a snackbar with Retry |
| Reports | Skeleton (header + job cards) | "No jobs yet" / "No hours in this pay period yet" | Full-screen Retry; a failed export shows Retry (no second ad) |
| Settings | Form skeleton | n/a | Full-screen Retry; backup export and file-read failures show Retry (an invalid backup file doesn't, since retrying can't help) |
| Shift and job editors, onboarding | Form skeleton | n/a | Inline "Couldn't save"; the typed values stay, and Save retries. Onboarding never creates the first job twice |
| Notification buttons, widget, boot | n/a | n/a | Failures are logged, never crash; the next app start re-syncs |

Database streams use `util/retryable`: an exception becomes an error state, and Retry re-subscribes. It's unit-tested.

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
