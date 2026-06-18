import random
import time

from kivy.clock import Clock
from kivy.graphics import Color, Ellipse, Line, Rectangle
from kivy.metrics import dp
from kivy.properties import ListProperty, NumericProperty, StringProperty
from kivy.uix.floatlayout import FloatLayout
from kivy.uix.modalview import ModalView
from kivy.uix.widget import Widget

from reaction_tracker.core.config import ATTEMPTS_PER_TEST, COLORS
from reaction_tracker.ui.widgets import FitLabel


class CombatScene(Widget):
    enemy_visible = NumericProperty(0)
    scope_mode = StringProperty("rifle")
    head = ListProperty([0, 0])

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.bind(pos=self.redraw, size=self.redraw, enemy_visible=self.redraw, scope_mode=self.redraw, head=self.redraw)

    def set_enemy(self, visible, variant=0):
        width, height = max(self.width, 1), max(self.height, 1)
        base_x = self.x + width * (0.67 + [0.0, 0.035, -0.025][variant % 3])
        base_y = self.y + height * (0.48 + [0.0, 0.025, -0.015][variant % 3])
        self.head = [base_x, base_y]
        self.enemy_visible = 1 if visible else 0

    def redraw(self, *_):
        self.canvas.clear()
        x, y, w, h = self.x, self.y, self.width, self.height
        if w <= 1 or h <= 1:
            return
        with self.canvas:
            Color(0.64, 0.55, 0.39, 1)
            Rectangle(pos=(x, y), size=(w, h))
            Color(0.98, 0.83, 0.52, 0.95)
            Rectangle(pos=(x, y + h * 0.54), size=(w, h * 0.46))
            Color(0.44, 0.36, 0.25, 1)
            Rectangle(pos=(x, y), size=(w, h * 0.54))
            Color(0.72, 0.62, 0.43, 1)
            Line(points=[x + w * 0.05, y + h * 0.18, x + w * 0.72, y + h * 0.43, x + w * 0.98, y + h * 0.42], width=dp(5))
            Color(0.84, 0.74, 0.55, 1)
            Rectangle(pos=(x + w * 0.58, y + h * 0.26), size=(w * 0.18, h * 0.47))
            Color(0.38, 0.29, 0.2, 1)
            Rectangle(pos=(x + w * 0.74, y + h * 0.25), size=(w * 0.12, h * 0.52))
            Color(0.16, 0.13, 0.1, 1)
            Rectangle(pos=(x + w * 0.82, y + h * 0.26), size=(w * 0.035, h * 0.48))
            Color(1, 1, 1, 0.13)
            for i in range(9):
                Line(points=[x + w * (0.08 + i * 0.11), y + h * 0.18, x + w * (0.48 + i * 0.06), y + h * 0.78], width=1)
            if self.enemy_visible:
                hx, hy = self.head
                Color(1, 0.1, 0.08, 0.22)
                Ellipse(pos=(hx - dp(58), hy - dp(58)), size=(dp(116), dp(116)))
                Color(1, 0.92, 0.78, 1)
                Ellipse(pos=(hx - dp(18), hy - dp(18)), size=(dp(36), dp(36)))
                Color(0.86, 0.25, 0.18, 1)
                Rectangle(pos=(hx - dp(21), hy - dp(82)), size=(dp(42), dp(65)))
                Color(1, 1, 1, 0.95)
                Line(circle=(hx, hy, dp(22)), width=dp(2))
                Color(1, 0.05, 0.05, 0.95)
                Line(circle=(hx, hy, dp(27)), width=dp(1.2))
            self.draw_scope()

    def draw_scope(self):
        hx, hy = self.head if self.head != [0, 0] else [self.center_x, self.center_y]
        if self.scope_mode == "awp":
            radius = min(self.width, self.height) * 0.46
            Color(0, 0, 0, 0.45)
            Rectangle(pos=self.pos, size=self.size)
            Color(0, 0, 0, 0.72)
            Line(circle=(hx, hy, radius), width=dp(4))
            Color(0.05, 0.08, 0.09, 0.64)
            Rectangle(pos=(self.x, self.y), size=(max(0, hx - radius - self.x), self.height))
            Rectangle(pos=(hx + radius, self.y), size=(max(0, self.right - hx - radius), self.height))
            Color(0.92, 0.98, 1.0, 0.92)
            Line(points=[self.x, hy, self.right, hy], width=1)
            Line(points=[hx, self.y, hx, self.top], width=1)
            Line(circle=(hx, hy, dp(3)), width=1)
        else:
            Color(*COLORS["cyan"])
            Line(points=[hx - dp(34), hy, hx - dp(8), hy], width=1.2)
            Line(points=[hx + dp(8), hy, hx + dp(34), hy], width=1.2)
            Line(points=[hx, hy - dp(34), hx, hy - dp(8)], width=1.2)
            Line(points=[hx, hy + dp(8), hx, hy + dp(34)], width=1.2)
            Color(*COLORS["gold"])
            Line(circle=(hx, hy, dp(4)), width=1)


class CombatOverlay(ModalView):
    def __init__(self, on_finish, **kwargs):
        super().__init__(**kwargs)
        self.auto_dismiss = False
        self.background_color = (0, 0, 0, 0)
        self.on_finish = on_finish
        self.reactions = []
        self.false_starts = 0
        self.attempt = 0
        self.wait_event = None
        self.peek_time = 0
        self.state = "countdown"
        root = FloatLayout()
        self.scene = CombatScene()
        root.add_widget(self.scene)
        self.status = FitLabel(text="", font_size="22sp", bold=True, size_hint=(1, None), height=dp(70), pos_hint={"top": 0.98}, halign="center")
        root.add_widget(self.status)
        root.bind(on_touch_down=self.handle_touch)
        self.add_widget(root)

    def on_open(self):
        self.count_left = 10
        self._tick_countdown(0)

    def _tick_countdown(self, _):
        if self.count_left <= 0:
            self.next_attempt()
            return
        self.status.text = f"[color=ffd35a]Ready[/color]  {self.count_left}"
        self.count_left -= 1
        Clock.schedule_once(self._tick_countdown, 1)

    def next_attempt(self, *_):
        if self.attempt >= ATTEMPTS_PER_TEST:
            self.finish()
            return
        self.state = "waiting"
        self.scene.scope_mode = "awp" if self.attempt % 3 == 2 else "rifle"
        self.scene.set_enemy(False, self.attempt)
        self.status.text = f"Attempt {self.attempt + 1}/{ATTEMPTS_PER_TEST} - wait for peek"
        self.wait_event = Clock.schedule_once(self.show_enemy, random.uniform(0.75, 2.4))

    def show_enemy(self, *_):
        self.state = "visible"
        self.peek_time = time.perf_counter()
        self.scene.set_enemy(True, self.attempt)
        self.status.text = "[color=ff4040]PEEK[/color]"

    def handle_touch(self, _, touch):
        if self.state == "countdown":
            return True
        if self.state == "waiting":
            if self.wait_event:
                self.wait_event.cancel()
            self.false_starts += 1
            self.status.text = "[color=ff4040]False start[/color]"
            self.state = "pause"
            Clock.schedule_once(self.next_attempt, 0.7)
            return True
        if self.state == "visible":
            reaction = int((time.perf_counter() - self.peek_time) * 1000)
            self.reactions.append(reaction)
            self.attempt += 1
            self.scene.set_enemy(False, self.attempt)
            self.status.text = f"[color=00e5ff]{reaction} ms[/color]"
            self.state = "pause"
            Clock.schedule_once(self.next_attempt, 0.55)
            return True
        return True

    def finish(self):
        self.dismiss()
        self.on_finish(self.reactions, self.false_starts)
