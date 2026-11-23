# Launch checklist (v1.0.0 → closed testing → production)

## 1. Name
- [x] Name: **Punchd** (decided 23 Nov 2026; see `store-listing.md`)
- [x] `app_name` and `widget_name` set to Punchd in English and German
- [ ] `applicationId` in `app/build.gradle.kts` (currently `com.oamsapps.punchd`; **can't be changed after the first upload**)
- [x] Name filled in `docs/store-listing.md`, `docs/privacy-policy.md`, `docs/privacy.html`
- [ ] Before the first upload: search Play for "Punchd" (US and German VPN), and USPTO for "PUNCHD" / "PUNCHH" plus DPMA / EUIPO for "Punchd"

## 2. Accounts and IDs (never commit these)
- [ ] AdMob: app + 4 ad units (banner, native, rewarded, interstitial) → `~/.gradle/gradle.properties` (keys in README)
- [ ] AdMob → Privacy & messaging: publish **GDPR** and **US state regulations** messages (UMP shows them)
- [ ] `app-ads.txt` on the developer site (`oamprakash.github.io/app-ads.txt`) contains your publisher line:
      `google.com, pub-XXXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0`, and the Play listing's website points at that domain
- [ ] Play Console → Monetise → In-app products: `remove_ads`, $2.99 / €2.99, active
- [ ] Play Console → License testing: add your Gmail accounts (test the purchase without being charged)

## 3. Policy pages
- [ ] Publish `docs/privacy.html` at `https://oamprakash.github.io/punchd/privacy.html` (the URL Settings opens: `PRIVACY_POLICY_URL` in `SettingsScreen.kt`)
- [ ] Fill in contact email and effective date; have it reviewed

## 4. Play Console forms
- **Data safety.**
  - The app itself collects nothing. Time data never leaves the device except in files the user shares.
  - The AdMob SDK collects data, so declare it following Google's AdMob data-disclosure guide (check the current version when filling the form). Typically: *Device or other IDs*, *Approximate location*, *App interactions* and *Diagnostics*. Purpose: advertising/marketing, analytics, fraud prevention. Shared with Google. Collection can't be turned off in the free version.
  - Data encrypted in transit: yes (SDK). Users can request deletion: data is on-device; uninstalling removes it.
- **Ads:** "Contains ads": yes.
- **Content rating questionnaire:** utility, no user-generated content shared with others. Expect Everyone / PEGI 3.
- **Target audience:** 18+ (workers). Not designed for children.
- **App access:** no login needed.
- **Foreground service / exact alarm declarations:** none are used. The timer uses a stored start time, the system chronometer and WorkManager.

## 5. Build
- [ ] `versionCode = 1`, `versionName = "1.0.0"` (set)
- [ ] `./gradlew testDebugUnitTest` green
- [ ] `./gradlew bundleRelease` with Play App Signing (upload key in `~/.gradle/gradle.properties` or Android Studio)
- [ ] The build log has **no** "ADMOB_… not set" warning (otherwise the release uses test ads)
- [ ] Commit `app/schemas/com.oamsapps.punchd.data.db.TimeCardDatabase/1.json` after the first build. From now on, every schema change needs a Room migration.

## 6. Device tests (Galaxy M02s, plus the Xiaomi when it arrives)
- [ ] Cold start → Clock tab shows the running state in under 1 s
- [ ] Clock in → reboot → notification and widget come back, the timer is correct, no missed punch
- [ ] Force-stop during a break → reopen → still on break, worked time frozen
- [ ] Night shift across midnight, plus a DST night if one is close (set the phone's time zone to test)
- [ ] Germany preset: >6 h without a break → "Time for a break" notification; >10 h → max warning
- [ ] "Still clocked in?" after the chosen hours, and its Clock out button works
- [ ] Widget: Clock in / out from the home screen, the live timer runs, no ad
- [ ] Exports: PDF, Excel and CSV open correctly (Excel in Google Sheets, German CSV in Excel)
- [ ] Backup export → uninstall → install → import → everything is back, including a running shift
- [ ] Consent form appears with a VPN to Germany. Settings → Privacy options reopens it
- [ ] Remove ads with a license tester: ads gone, 4th job allowed, PDF without the prompt, and "Restore" works after reinstall
- [ ] Language: switch to Deutsch in system per-app language settings (Android 13+). All screens are German
- [ ] Samsung battery: if reminders are late, test with "Unrestricted" battery for the app and note it for users

## 7. Store listing and testing
- [ ] Listing text EN + DE (`store-listing.md`), 512 px icon (`docs/store/icon-512.png`), feature graphic, 8 screenshots per language
- [ ] Closed testing track: 12+ testers opted in for 14 continuous days (if your developer account requires it)
- [ ] Watch the pre-launch report and Android vitals (crash-free target 99.5%+), then promote to production

## 8. After launch (spec success metrics, first 60 days)
Day-1 retention 40%+ · Day-30 15%+ · 4+ active days/week · rating 4.5+ · remove-ads conversion 2–3% · crash-free 99.5%+
