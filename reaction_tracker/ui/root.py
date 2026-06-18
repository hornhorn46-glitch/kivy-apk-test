from kivy.metrics import dp
from kivy.uix.floatlayout import FloatLayout
from kivy.uix.gridlayout import GridLayout

from reaction_tracker.screens.charts import DayScreen, NormScreen
from reaction_tracker.screens.history import DaysScreen
from reaction_tracker.screens.test import TestScreen
from reaction_tracker.screens.weather import WeatherScreen
from reaction_tracker.ui.widgets import SportBackground, SportButton


class Root(FloatLayout):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.add_widget(SportBackground())
        self.body = FloatLayout(size_hint=(1, 1))
        self.add_widget(self.body)
        self.screens = {
            "Test": TestScreen(),
            "Day": DayScreen(),
            "Norm": NormScreen(),
            "History": DaysScreen(),
            "Weather": WeatherScreen(),
        }
        self.current = None
        self.buttons = {}
        self.nav = GridLayout(cols=5, spacing=dp(4), padding=dp(6), size_hint=(1, None), height=dp(70), pos_hint={"x": 0, "y": 0})
        for name in self.screens:
            button = SportButton(text=name, accent="gold" if name == "Test" else "cyan", height=dp(52))
            button.bind(on_release=lambda _, screen_name=name: self.show(screen_name))
            self.buttons[name] = button
            self.nav.add_widget(button)
        self.add_widget(self.nav)
        self.show("Test")

    def show(self, name):
        if self.current:
            self.body.remove_widget(self.screens[self.current])
        self.current = name
        screen = self.screens[name]
        screen.size_hint = (1, 1)
        screen.pos_hint = {"x": 0, "y": 0}
        self.body.add_widget(screen)
        if hasattr(screen, "refresh"):
            screen.refresh()
        for key, button in self.buttons.items():
            button.accent = "gold" if key == name and key == "Test" else ("cyan" if key == name else "blue")
            button.redraw()

    def refresh_all(self):
        for screen in self.screens.values():
            if hasattr(screen, "refresh"):
                screen.refresh()
