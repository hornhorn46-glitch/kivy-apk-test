import json
import urllib.error
import urllib.request


class WeatherService:
    URL = (
        "https://api.open-meteo.com/v1/forecast"
        "?latitude=55.75&longitude=37.62"
        "&current=temperature_2m,relative_humidity_2m,wind_speed_10m"
    )

    def fetch_current(self):
        try:
            with urllib.request.urlopen(self.URL, timeout=8) as response:
                data = json.loads(response.read().decode("utf-8"))
            current = data.get("current", {})
            return {
                "ok": True,
                "temperature": current.get("temperature_2m", "-"),
                "humidity": current.get("relative_humidity_2m", "-"),
                "wind": current.get("wind_speed_10m", "-"),
            }
        except (urllib.error.URLError, TimeoutError, ValueError, OSError) as exc:
            return {"ok": False, "error": str(exc)}
