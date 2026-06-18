# Python APK

Minimal Python Android app scaffold using Kivy and Buildozer.

## Run on desktop

```powershell
python -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements.txt
.\.venv\Scripts\python main.py
```

## Build APK

Buildozer builds Android packages on Linux. From Windows, use WSL2, Docker, or the included GitHub Actions workflow.

### WSL2 / Linux

```bash
sudo apt update
sudo apt install -y git zip unzip openjdk-17-jdk python3-pip python3-venv
python3 -m pip install --user buildozer
buildozer android debug
```

The debug APK will be created under `bin/`.

