# Root-Cause Validation

AutoDoctor AI Pro now includes a reproducible root-cause benchmark and an on-device neuro-symbolic model for gasoline driveability diagnosis.

Current measured benchmark:

- Target: gasoline driveability root cause.
- Test requirement: at least 50 graph cases.
- Current test set: 60 holdout graph cases.
- Measured accuracy: 0.9833.
- Measured macro F1: 0.9819.
- Validation kind: physics fault injection on real OBD driving templates.

The benchmark uses real OBD-II driving templates from the VehiclePM public dataset and controlled fault injections for root-cause labels. The Zenodo Automotive Faults Dataset contributes open structured fault categories and diagnostic knowledge. The diagnostic case-pattern corpus now adds 12,764 patterns: 11,256 standard DTC/OBD scenarios, 1,000 uncommon graph traps, 500 vehicle-specific prioritization patterns and 8 repair-confirmed seed cases from professional/open practical sources. This is a valid engineering benchmark for model regression testing, but it is not a claim of 98.33% field accuracy on repair-confirmed customer cars.

To claim field accuracy, the project must collect or license a larger independent fleet dataset containing:

- raw OBD graph sessions;
- supported PID map;
- DTC and freeze-frame data;
- vehicle profile;
- customer symptom;
- technician-confirmed root cause;
- completed repair;
- post-repair verification session.

The app should display high confidence only when enough live data exists. If PID coverage is low, the model reports insufficient data rather than forcing a diagnosis.
