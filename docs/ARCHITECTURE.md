# Architecture

## Principles

- No diagnostic rules hardcoded in app logic.
- All expert rules live in `knowledge/`.
- The diagnostic engine consumes facts, runs rules/models, and returns explainable hypotheses.
- Every hypothesis must include probability, evidence, missing data, physical explanation, and recommended next checks.
- If evidence is insufficient, the engine must explicitly report insufficient data.

## Android Packages

Planned package root: `com.autodoctor.aipro`

- `app`
  - app bootstrap and navigation.
- `core.model`
  - pure domain models.
- `core.obd`
  - ELM327 protocol, PID definitions, parser contracts.
- `core.diagnostics`
  - inference engine, probability combiner, physical models.
- `core.knowledge`
  - JSON rule loading, validation, encyclopedia loading.
- `data`
  - Room entities, repositories, local persistence.
- `transport`
  - Bluetooth, Wi-Fi, USB adapters.
- `feature.dashboard`
  - connection status, health summary.
- `feature.live`
  - live data and graphs.
- `feature.acceleration`
  - guided acceleration test and power estimation.
- `feature.diagnosis`
  - expert findings and explanations.
- `feature.garage`
  - vehicle profiles.
- `feature.encyclopedia`
  - learning content.
- `ui.design`
  - custom design system on top of Material 3.

## Data Flow

1. Transport connects to ELM327.
2. OBD service discovers protocol and supported PIDs.
3. Live sampler records timestamped PID samples.
4. Session repository stores data locally.
5. Diagnostic engine receives:
   - vehicle profile
   - DTCs
   - freeze-frame data
   - live samples
   - acceleration test data
   - reference values
6. Engine computes derived facts and model outputs.
7. Knowledge rules evaluate conditions.
8. Probability combiner ranks hypotheses.
9. UI displays explanations, confidence, severity, and next checks.

## Offline Requirement

The app is designed to work fully offline. No cloud inference is required for diagnostics.
