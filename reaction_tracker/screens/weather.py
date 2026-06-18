from kivy.clock import Clock
from kivy.metrics import dp

from reaction_tracker.services.weather import WeatherService
from reaction_tracker.ui.widgets import FitLabel, ScrollScreen, SportButton


class WeatherScreen(ScrollScreen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.service = WeatherService()
        self.info = FitLabel(text="Weather is not loaded.", font_size="16sp", size_hint_y=None)
        button = SportButton(text="LOAD OPEN-METEO", accent="cyan")
        button.bind(on_release=self.load_weather)
        self.add_card(
            FitLabel(text="[b]Weather[/b]\n[color=8fdcff]Open-Meteo | Moscow by default[/color]", font_size="20sp", size_hint_y=None),
            self.info,
            button,
            height=dp(250),
        )

    def load_weather(self, *_):
        self.info.text = "Loading..."
        Clock.schedule_once(lambda __: self.fetch(), 0.1)

    def fetch(self):
        result = self.service.fetch_current()
        if result["ok"]:
            self.info.text = (
                "[b]Current[/b]\n"
                f"Temperature: {result['temperature']} C\n"
                f"Humidity: {result['humidity']}%\n"
                f"Wind: {result['wind']} km/h\n"
                "If internet is unavailable, the rest of the app stays local."
            )
        else:
            self.info.text = f"Weather failed: {result['error']}\nThe app continues offline."
