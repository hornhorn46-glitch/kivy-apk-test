from kivy.graphics import Color, Ellipse, Line, Rectangle, RoundedRectangle
from kivy.metrics import dp
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.scrollview import ScrollView
from kivy.uix.widget import Widget

from reaction_tracker.core.config import COLORS


class SportBackground(Widget):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.bind(pos=self.redraw, size=self.redraw)

    def redraw(self, *_):
        self.canvas.clear()
        with self.canvas:
            Color(*COLORS["bg"])
            Rectangle(pos=self.pos, size=self.size)
            w, h = self.size
            x, y = self.pos
            for cx, cy, col, scale in (
                (x + w * 0.18, y + h * 0.82, COLORS["blue"], 0.34),
                (x + w * 0.86, y + h * 0.72, COLORS["gold"], 0.28),
                (x + w * 0.72, y + h * 0.18, COLORS["cyan"], 0.26),
            ):
                for i in range(7, 0, -1):
                    Color(col[0], col[1], col[2], 0.025 * i)
                    radius = min(w, h) * scale * i / 7
                    Ellipse(pos=(cx - radius, cy - radius), size=(radius * 2, radius * 2))
            Color(1, 1, 1, 0.06)
            for i in range(-4, 8):
                Line(points=[x + i * w * 0.22, y, x + w * (i * 0.22 + 0.55), y + h], width=1)


class GlassCard(BoxLayout):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.orientation = kwargs.get("orientation", "vertical")
        self.padding = dp(14)
        self.spacing = dp(10)
        self.bind(pos=self.redraw, size=self.redraw)

    def redraw(self, *_):
        self.canvas.before.clear()
        with self.canvas.before:
            Color(0, 0, 0, 0.18)
            RoundedRectangle(pos=(self.x + dp(3), self.y - dp(3)), size=self.size, radius=[dp(8)])
            Color(*COLORS["panel"])
            RoundedRectangle(pos=self.pos, size=self.size, radius=[dp(8)])
            Color(0.0, 0.86, 1.0, 0.16)
            Line(rounded_rectangle=(self.x, self.y, self.width, self.height, dp(8)), width=1.1)


class FitLabel(Label):
    def __init__(self, **kwargs):
        kwargs.setdefault("color", COLORS["text"])
        kwargs.setdefault("markup", True)
        kwargs.setdefault("halign", "left")
        kwargs.setdefault("valign", "middle")
        super().__init__(**kwargs)
        self.bind(width=self._fit, texture_size=self._resize)

    def _fit(self, *_):
        self.text_size = (self.width, None)

    def _resize(self, *_):
        if self.size_hint_y is None:
            self.height = max(dp(24), self.texture_size[1] + dp(4))


class SportButton(Button):
    def __init__(self, accent="cyan", **kwargs):
        super().__init__(**kwargs)
        self.accent = accent
        self.background_normal = ""
        self.background_down = ""
        self.background_color = (0, 0, 0, 0)
        self.color = COLORS["text"]
        self.bold = True
        self.size_hint_y = None
        self.height = kwargs.get("height", dp(48))
        self.bind(pos=self.redraw, size=self.redraw, state=lambda *_: self.redraw())

    def redraw(self, *_):
        color = COLORS.get(self.accent, COLORS["cyan"])
        alpha = 0.30 if self.state == "normal" else 0.55
        self.canvas.before.clear()
        with self.canvas.before:
            Color(color[0], color[1], color[2], alpha)
            RoundedRectangle(pos=self.pos, size=self.size, radius=[dp(7)])
            Color(color[0], color[1], color[2], 0.9)
            Line(rounded_rectangle=(self.x, self.y, self.width, self.height, dp(7)), width=1.2)


class ScrollScreen(ScrollView):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.do_scroll_x = False
        self.bar_width = dp(3)
        self.content = BoxLayout(
            orientation="vertical",
            padding=[dp(14), dp(14), dp(14), dp(96)],
            spacing=dp(12),
            size_hint_y=None,
        )
        self.content.bind(minimum_height=self.content.setter("height"))
        self.add_widget(self.content)

    def add_card(self, *children, height=None):
        card = GlassCard(size_hint_y=None)
        card.height = height or dp(140)
        for child in children:
            card.add_widget(child)
        self.content.add_widget(card)
        return card
