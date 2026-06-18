from datetime import datetime

from kivy.app import App
from kivy.metrics import dp
from kivy.uix.gridlayout import GridLayout

from reaction_tracker.core.config import COLORS
from reaction_tracker.core.utils import fmt_ms, session_time
from reaction_tracker.ui.charts import DayChart, NormChart
from reaction_tracker.ui.widgets import FitLabel, ScrollScreen, SportButton


class ChartScreen(ScrollScreen):
    chart_cls = DayChart
    title = ""
    legend = ""

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.chart = self.chart_cls(size_hint_y=None, height=dp(360))
        header = FitLabel(text=self.title, font_size="20sp", size_hint_y=None)
        self.add_card(header, self.chart, height=dp(430))
        controls = GridLayout(cols=3, spacing=dp(8), size_hint_y=None, height=dp(48))
        for text, action in (
            ("-", lambda *_: self.chart.zoom(0.75, 0.75)),
            ("Reset", lambda *_: self.chart.reset()),
            ("+", lambda *_: self.chart.zoom(1.35, 1.35)),
        ):
            button = SportButton(text=text, accent="cyan")
            button.bind(on_release=action)
            controls.add_widget(button)
        self.add_card(FitLabel(text=self.legend, font_size="13sp", size_hint_y=None), controls, height=dp(120))

    def refresh(self):
        app = App.get_running_app()
        norm = app.analytics.personal_norm()
        points = []
        today = datetime.now().date()
        for session in app.store.sessions():
            dt = session_time(session)
            if dt.date() != today:
                continue
            y = self.point_y(session, norm)
            if y is None:
                continue
            points.append({"x": dt.hour + dt.minute / 60, "y": y, "label": f"{dt:%H:%M} | {fmt_ms(session.get('medianReactionMs'))}", "color": self.point_color(y)})
        if not points:
            for session in app.store.sessions()[-12:]:
                dt = session_time(session)
                y = self.point_y(session, norm)
                if y is not None:
                    points.append({"x": dt.hour + dt.minute / 60, "y": y, "label": f"{dt:%d.%m %H:%M} | {fmt_ms(session.get('medianReactionMs'))}", "color": self.point_color(y)})
        self.chart.points = points
        self.chart.redraw()

    def point_y(self, session, norm):
        return session.get("medianReactionMs")

    def point_color(self, y):
        if y <= 300:
            return COLORS["green"]
        if y <= 500:
            return COLORS["gold"]
        return COLORS["red"]


class DayScreen(ChartScreen):
    chart_cls = DayChart
    title = "[b]Day[/b]\n[color=8fdcff]0-24 hours | reaction in milliseconds[/color]"
    legend = "0-300 fast | 300-500 medium | 500+ lapse | violet line = personal norm"


class NormScreen(ChartScreen):
    chart_cls = NormChart
    title = "[b]Norm[/b]\n[color=8fdcff]Deviation from personal norm[/color]"
    legend = "Green = better | cyan = near norm | red = worse | white line = zero"

    def point_y(self, session, norm):
        value = session.get("medianReactionMs")
        return None if value is None else value - norm

    def point_color(self, y):
        if y < -25:
            return COLORS["green"]
        if y <= 25:
            return COLORS["cyan"]
        return COLORS["red"]
