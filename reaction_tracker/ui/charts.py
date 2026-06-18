import math

from kivy.app import App
from kivy.core.text import Label as CoreLabel
from kivy.graphics import Color, Ellipse, Line, Rectangle, RoundedRectangle
from kivy.metrics import dp
from kivy.properties import ListProperty, StringProperty
from kivy.uix.widget import Widget

from reaction_tracker.core.config import COLORS
from reaction_tracker.core.utils import clamp


class BaseChart(Widget):
    points = ListProperty([])
    selected_text = StringProperty("")

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.x_min_base, self.x_max_base = 0, 24
        self.y_min_base, self.y_max_base = 0, 800
        self.x_min, self.x_max = 0, 24
        self.y_min, self.y_max = 0, 800
        self.touches = {}
        self.last_touch = None
        self.moved = False
        self.start_span = None
        self.bind(pos=self.redraw, size=self.redraw, points=self.redraw)

    def plot_box(self):
        return self.x + dp(48), self.y + dp(42), self.right - dp(12), self.top - dp(22)

    def data_to_px(self, xval, yval):
        left, bottom, right, top = self.plot_box()
        px = left + (xval - self.x_min) / max(0.01, self.x_max - self.x_min) * (right - left)
        py = bottom + (yval - self.y_min) / max(0.01, self.y_max - self.y_min) * (top - bottom)
        return px, py

    def set_view(self, x_min, x_max, y_min, y_max):
        x_span = clamp(x_max - x_min, 2.0, 24)
        y_span = clamp(y_max - y_min, 80.0, self.y_max_base - self.y_min_base)
        x_mid = clamp((x_min + x_max) / 2, x_span / 2, 24 - x_span / 2)
        y_mid = clamp((y_min + y_max) / 2, self.y_min_base + y_span / 2, self.y_max_base - y_span / 2)
        self.x_min, self.x_max = x_mid - x_span / 2, x_mid + x_span / 2
        self.y_min, self.y_max = y_mid - y_span / 2, y_mid + y_span / 2
        self.redraw()

    def zoom(self, factor_x, factor_y):
        xm, ym = (self.x_min + self.x_max) / 2, (self.y_min + self.y_max) / 2
        xs = (self.x_max - self.x_min) / factor_x
        ys = (self.y_max - self.y_min) / factor_y
        self.set_view(xm - xs / 2, xm + xs / 2, ym - ys / 2, ym + ys / 2)

    def reset(self):
        self.set_view(self.x_min_base, self.x_max_base, self.y_min_base, self.y_max_base)

    def on_touch_down(self, touch):
        if not self.collide_point(*touch.pos):
            return False
        self.touches[touch.uid] = touch
        self.last_touch = touch.pos
        self.moved = False
        if len(self.touches) == 2:
            pts = [t.pos for t in self.touches.values()]
            self.start_span = (abs(pts[0][0] - pts[1][0]), abs(pts[0][1] - pts[1][1]), self.x_min, self.x_max, self.y_min, self.y_max)
        return True

    def on_touch_move(self, touch):
        if touch.uid not in self.touches:
            return False
        self.moved = True
        self.touches[touch.uid] = touch
        if len(self.touches) == 1 and self.last_touch:
            dx, dy = touch.x - self.last_touch[0], touch.y - self.last_touch[1]
            left, bottom, right, top = self.plot_box()
            x_delta = -dx / max(1, right - left) * (self.x_max - self.x_min)
            y_delta = -dy / max(1, top - bottom) * (self.y_max - self.y_min)
            self.set_view(self.x_min + x_delta, self.x_max + x_delta, self.y_min + y_delta, self.y_max + y_delta)
            self.last_touch = touch.pos
        elif len(self.touches) == 2 and self.start_span:
            pts = [t.pos for t in self.touches.values()]
            sx, sy, xmin, xmax, ymin, ymax = self.start_span
            nx, ny = max(20, abs(pts[0][0] - pts[1][0])), max(20, abs(pts[0][1] - pts[1][1]))
            fx, fy = clamp(nx / max(20, sx), 0.4, 3.0), clamp(ny / max(20, sy), 0.4, 3.0)
            xm, ym = (xmin + xmax) / 2, (ymin + ymax) / 2
            self.set_view(xm - (xmax - xmin) / fx / 2, xm + (xmax - xmin) / fx / 2, ym - (ymax - ymin) / fy / 2, ym + (ymax - ymin) / fy / 2)
        return True

    def on_touch_up(self, touch):
        had_touch = touch.uid in self.touches
        self.touches.pop(touch.uid, None)
        if had_touch and not self.moved:
            self.pick_point(touch.pos)
        if not self.touches:
            self.start_span = None
        return had_touch

    def pick_point(self, pos):
        nearest = None
        best = dp(28)
        for point in self.points:
            px, py = self.data_to_px(point["x"], point["y"])
            distance = math.hypot(px - pos[0], py - pos[1])
            if distance < best:
                best, nearest = distance, point
        if nearest:
            self.selected_text = nearest.get("label", "")
            self.redraw()

    def ticks(self, low, high, target=5):
        raw = (high - low) / target
        step = min([0.5, 1, 2, 3, 4, 6, 8, 12, 100, 200], key=lambda item: abs(item - raw))
        value = math.ceil(low / step) * step
        values = []
        while value <= high + 0.001:
            values.append(value)
            value += step
        return values

    def draw_zones(self, left, bottom, right, top):
        pass

    def redraw(self, *_):
        self.canvas.clear()
        left, bottom, right, top = self.plot_box()
        with self.canvas:
            Color(*COLORS["panel2"])
            RoundedRectangle(pos=self.pos, size=self.size, radius=[dp(8)])
            self.draw_zones(left, bottom, right, top)
            Color(1, 1, 1, 0.12)
            Line(rectangle=(left, bottom, right - left, top - bottom), width=1)
            for xv in self.ticks(self.x_min, self.x_max, 6):
                px, _ = self.data_to_px(xv, self.y_min)
                Color(1, 1, 1, 0.08)
                Line(points=[px, bottom, px, top], width=1)
                self.label(f"{xv:g}", px - dp(10), bottom - dp(24), COLORS["muted"], 11)
            for yv in self.ticks(self.y_min, self.y_max, 5):
                _, py = self.data_to_px(self.x_min, yv)
                Color(1, 1, 1, 0.08)
                Line(points=[left, py, right, py], width=1)
                self.label(f"{int(yv)}", self.x + dp(5), py - dp(8), COLORS["muted"], 11)
            visible = [p for p in self.points if self.x_min <= p["x"] <= self.x_max and self.y_min <= p["y"] <= self.y_max]
            visible.sort(key=lambda point: point["x"])
            if len(visible) > 1:
                line_points = []
                for point in visible:
                    line_points.extend(self.data_to_px(point["x"], point["y"]))
                Color(*COLORS["cyan"])
                Line(points=line_points, width=dp(2))
            for point in visible:
                px, py = self.data_to_px(point["x"], point["y"])
                Color(*point.get("color", COLORS["gold"]))
                Ellipse(pos=(px - dp(4), py - dp(4)), size=(dp(8), dp(8)))
            scale = f"X: {self.x_min:04.1f}-{self.x_max:04.1f} h | Y: {int(self.y_min)}-{int(self.y_max)} | {24/(self.x_max-self.x_min):.1f}x / {(self.y_max_base-self.y_min_base)/(self.y_max-self.y_min):.1f}x"
            self.label(scale, left, self.top - dp(18), COLORS["muted"], 11)
            if self.selected_text:
                self.label(self.selected_text, left + dp(6), top - dp(28), COLORS["text"], 12)

    def label(self, text, x, y, color, size):
        label = CoreLabel(text=text, font_size=dp(size), color=color)
        label.refresh()
        Rectangle(texture=label.texture, pos=(x, y), size=label.texture.size)


class DayChart(BaseChart):
    def draw_zones(self, left, bottom, right, top):
        for low, high, color, alpha in (
            (0, 300, COLORS["green"], 0.16),
            (300, 500, COLORS["gold"], 0.14),
            (500, 800, COLORS["red"], 0.14),
        ):
            y1 = clamp(self.data_to_px(0, low)[1], bottom, top)
            y2 = clamp(self.data_to_px(0, high)[1], bottom, top)
            if y2 > y1:
                Color(color[0], color[1], color[2], alpha)
                Rectangle(pos=(left, y1), size=(right - left, y2 - y1))
        app = App.get_running_app()
        if app:
            norm = app.analytics.personal_norm()
            _, py = self.data_to_px(0, norm)
            if bottom <= py <= top:
                Color(*COLORS["violet"])
                Line(points=[left, py, right, py], width=1.4)


class NormChart(BaseChart):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.y_min_base, self.y_max_base = -300, 300
        self.y_min, self.y_max = -300, 300

    def draw_zones(self, left, bottom, right, top):
        middle = self.data_to_px(0, 0)[1]
        Color(*COLORS["green"][:3], 0.14)
        Rectangle(pos=(left, bottom), size=(right - left, max(0, middle - bottom)))
        Color(*COLORS["red"][:3], 0.14)
        Rectangle(pos=(left, middle), size=(right - left, max(0, top - middle)))
        if bottom <= middle <= top:
            Color(1, 1, 1, 0.72)
            Line(points=[left, middle, right, middle], width=1.3)
