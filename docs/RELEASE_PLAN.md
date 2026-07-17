# Release Plan

## Completed In This Pass

- OBD connection cockpit: Bluetooth, Wi-Fi and USB discovery with ELM initialization status.
- Live data gauges for RPM, speed, throttle, load, MAP and MAF.
- Guided safe engine tests with explicit speed, gear, RPM and pedal instructions.
- Test validity guard: detects missing pedal input, short captures, weak RPM coverage, missing speed targets and insufficient OBD samples.
- Reference-vs-actual graph display with out-of-band actual trace coloring.
- Root-cause model benchmark target raised to 98%; current controlled benchmark is 98.33% accuracy and 98.19% macro F1.
- Offline knowledge corpus expanded to 12,764 diagnostic case patterns and 18 encyclopedia articles.

## Required Before Public Release

- Real-device test matrix: at least Bluetooth ELM327 v1.5, Wi-Fi ELM327, USB adapter, Android 10-15.
- Runtime USB permission flow with user-facing adapter selection.
- Persistent session storage UI: saved tests, exports and before/after repair comparison.
- Vehicle profile selector and exact reference curve matching instead of default/fallback reference.
- Repair-confirmed fleet validation: raw OBD graphs, DTCs, freeze-frame, confirmed root cause, completed repair and post-repair verification.
- Safety screen before road tests with local legal/safety disclaimer and passenger/operator recommendation.
- App signing, versioning, crash logging policy and privacy policy.

## Accuracy Policy

The app can report the controlled benchmark score inside development artifacts, but production copy must not claim 98% field accuracy until validated on an independent repair-confirmed fleet holdout.
