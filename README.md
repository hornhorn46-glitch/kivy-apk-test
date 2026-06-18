# Reaction Tracker

Python/Kivy mobile app for reaction testing, daily reaction charts, readiness scoring, history, check-ins, coaching tips, and weather.

The project is now structured as a normal app package instead of a single `main.py`. The root `main.py` is only the entry point.

## Project Layout

```text
main.py                         # app entry point
reaction_tracker/
  app.py                        # Kivy App class
  core/                         # constants and small utilities
  data/                         # JSON persistence
  analytics/                    # readiness, norm, trend, coach logic
  services/                     # integrations such as weather
  ui/                           # reusable Kivy widgets, charts, combat overlay
  screens/                      # app screens
  models/                       # future domain/ML model files
  ml/                           # future local ML pipelines or adapters
  assets/                       # future images/fonts/sounds if we choose to use them
codex-work/                     # local prompts and scratch notes, ignored by Git
```

## Run In Pydroid

Install Kivy in Pydroid, then run:

```bash
python main.py
```

## Run On Desktop

```powershell
python -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements.txt
.\.venv\Scripts\python main.py
```

## Build APK Later

Buildozer builds Android packages on Linux. From Windows, use WSL2, Docker, or GitHub Actions.

```bash
sudo apt update
sudo apt install -y git zip unzip openjdk-17-jdk python3-pip python3-venv
python3 -m pip install --user buildozer
buildozer android debug
```

The debug APK will be created under `bin/`.
