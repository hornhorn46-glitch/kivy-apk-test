# AutoDoctor AI Pro

Professional offline Android diagnostic assistant for OBD-II data analysis.

The project is structured as an Android/Kotlin application with a separate, extensible expert knowledge base. The first milestone focuses on a production-grade architecture and a working diagnostic core that can explain probable faults from correlated live data, DTCs, freeze-frame data, vehicle profile, and reference values.

## Current Scope

Implemented in this repository:

- Kotlin/Jetpack Compose Android source tree.
- MVVM-style package layout.
- OBD domain model: PID samples, DTCs, freeze frames, sessions, adapters, vehicle profiles.
- Transport abstractions for Bluetooth, Wi-Fi, USB ELM327.
- Expert knowledge base in JSON, outside code.
- Rule loader and inference engine contracts.
- Mathematical/physical model interfaces for power, air mass, fuel trim, boost, misfire, and plausibility analysis.
- Initial open-licensed knowledge rules and encyclopedia entries.
- JVM/Python validation scripts for the knowledge base.

Not claimed as finished:

- Full OEM-level proprietary PID coverage. That requires licensed OEM data.
- Dealer-level bidirectional functions. Safety and legal validation are required before implementing actuator tests/coding.

## Commands

Validate the knowledge base:

```powershell
python tools\validate_kb.py
```

Run all local checks:

```powershell
python tools\run_checks.py
```

## Android Build

This workspace currently does not include Android SDK/Gradle tooling. The Android project files are laid out for a standard Gradle Android project. Once Android tooling is installed, add/refresh the Gradle wrapper and build with:

```powershell
.\gradlew assembleDebug
```

## Context

Start with:

- `docs/ARCHITECTURE.md`
- `docs/KNOWLEDGE_BASE.md`
- `docs/DIAGNOSTIC_MODEL.md`
- `docs/SOURCES.md`
