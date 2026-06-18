import json
import math
import os
import random
import statistics
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta

from kivy.app import App
from kivy.clock import Clock
from kivy.core.window import Window
from kivy.graphics import Color, Ellipse, Line, Rectangle, RoundedRectangle
from kivy.metrics import dp
from kivy.properties import ListProperty, NumericProperty, ObjectProperty, StringProperty
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.floatlayout import FloatLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.label import Label
from kivy.uix.modalview import ModalView
from kivy.uix.scrollview import ScrollView
from kivy.uix.slider import Slider
from kivy.uix.textinput import TextInput
from kivy.uix.widget import Widget


Window.clearcolor = (0.025, 0.03, 0.055, 1)

COLORS = {
    "bg": (0.025, 0.03, 0.055, 1),
    "panel": (0.055, 0.07, 0.105, 0.9),
    "panel2": (0.08, 0.095, 0.14, 0.94),
    "line": (0.25, 0.75, 1.0, 1),
    "cyan": (0.0, 0.86, 1.0, 1),
    "blue": (0.05, 0.28, 1.0, 1),
    "gold": (1.0, 0.72, 0.12, 1),
    "green": (0.2, 0.95, 0.45, 1),
    "red": (1.0, 0.18, 0.18, 1),
    "violet": (0.68, 0.35, 1.0, 1),
    "text": (0.92, 0.96, 1.0, 1),
    "muted": (0.58, 0.66, 0.78, 1),
}


def now_ts():
    return int(time.time())


def clamp(value, low, high):
    return max(low, min(high, value))


def fmt_ms(value):
    return "-" if value is None else f"{int(round(value))} ms"


def session_time(session):
    return datetime.fromtimestamp(session.get("timestamp", now_ts()))


class DataStore:
    def __init__(self):
        app = App.get_running_app()
        base = app.user_data_dir if app else os.getcwd()
        self.path = os.path.join(base, "reaction_tracker_data.json")
        self.data = {"sessions": []}
        self.load()
        if not self.data["sessions"]:
            self.seed_demo_data()
            self.save()

    def load(self):
        try:
            with open(self.path, "r", encoding="utf-8") as f:
                raw = json.load(f)
            sessions = raw.get("sessions", []) if isinstance(raw, dict) else []
            self.data = {"sessions": [self.normalize_session(s) for s in sessions if isinstance(s, dict)]}
        except Exception:
            self.data = {"sessions": []}

    def save(self):
        os.makedirs(os.path.dirname(self.path), exist_ok=True)
        with open(self.path, "w", encoding="utf-8") as f:
            json.dump(self.data, f, ensure_ascii=False, indent=2)

    def normalize_session(self, s):
        reactions = [int(x) for x in s.get("reactions", []) if isinstance(x, (int, float)) and x >= 0]
        median = s.get("medianReactionMs")
        average = s.get("averageReactionMs")
        best = s.get("bestReactionMs")
        worst = s.get("worstReactionMs")
        if reactions:
            median = median if median is not None else statistics.median(reactions)
            average = average if average is not None else sum(reactions) / len(reactions)
            best = best if best is not None else min(reactions)
            worst = worst if worst is not None else max(reactions)
        return {
            "id": str(s.get("id") or f"s{now_ts()}{random.randint(100, 999)}"),
            "timestamp": int(s.get("timestamp") or now_ts()),
            "medianReactionMs": median,
            "averageReactionMs": average,
            "bestReactionMs": best,
            "worstReactionMs": worst,
            "lapsesCount": int(s.get("lapsesCount") or len([x for x in reactions if x >= 500])),
            "falseStartsCount": int(s.get("falseStartsCount") or 0),
            "attemptsCount": int(s.get("attemptsCount") or max(20, len(reactions))),
            "reactions": reactions,
            "noteText": str(s.get("noteText") or ""),
            "tags": list(s.get("tags") or []),
            "checkin": {
                "sleepHours": float(s.get("checkin", {}).get("sleepHours", 7)),
                "caffeine": str(s.get("checkin", {}).get("caffeine", "нет")),
                "stress": int(s.get("checkin", {}).get("stress", 3)),
                "energy": int(s.get("checkin", {}).get("energy", 3)),
            },
        }

    def add_session(self, reactions, false_starts, note, tags, checkin):
        reactions = [int(x) for x in reactions]
        session = self.normalize_session({
            "id": f"s{now_ts()}{random.randint(100, 999)}",
            "timestamp": now_ts(),
            "reactions": reactions,
            "medianReactionMs": statistics.median(reactions) if reactions else None,
            "averageReactionMs": sum(reactions) / len(reactions) if reactions else None,
            "bestReactionMs": min(reactions) if reactions else None,
            "worstReactionMs": max(reactions) if reactions else None,
            "lapsesCount": len([x for x in reactions if x >= 500]),
            "falseStartsCount": false_starts,
            "attemptsCount": 20,
            "noteText": note,
            "tags": [x.strip() for x in tags.split(",") if x.strip()],
            "checkin": checkin,
        })
        self.data["sessions"].append(session)
        self.data["sessions"].sort(key=lambda x: x["timestamp"])
        self.save()
        return session

    def seed_demo_data(self):
        base = datetime.now().replace(hour=8, minute=15, second=0, microsecond=0)
        tags = [["warmup"], ["coffee"], ["ranked"], ["tired"], ["focus"]]
        for day in range(5, -1, -1):
            for slot in (0, 5, 10):
                dt = base - timedelta(days=day) + timedelta(hours=slot, minutes=random.randint(-25, 25))
                center = 330 + day * 10 - slot * 8 + random.randint(-35, 45)
                reactions = [clamp(random.gauss(center, 55), 165, 720) for _ in range(20)]
                self.data["sessions"].append(self.normalize_session({
                    "id": f"demo{day}{slot}",
                    "timestamp": int(dt.timestamp()),
                    "reactions": reactions,
                    "falseStartsCount": random.choice([0, 0, 1, 2]),
                    "noteText": random.choice(["Чистая серия", "Пара поздних дуэлей", "После разминки", ""]),
                    "tags": random.choice(tags),
                    "checkin": {
                        "sleepHours": random.choice([6, 6.5, 7, 8]),
                        "caffeine": random.choice(["нет", "кофе", "энергетик"]),
                        "stress": random.randint(1, 5),
                        "energy": random.randint(2, 5),
                    },
                }))

    def sessions(self):
        return list(self.data.get("sessions", []))


class Analytics:
    def __init__(self, store):
        self.store = store

    def personal_norm(self):
        values = [s.get("medianReactionMs") for s in self.store.sessions() if s.get("medianReactionMs")]
        return statistics.median(values) if values else 330

    def latest(self):
        sessions = self.store.sessions()
        return sessions[-1] if sessions else None

    def readiness_score(self, session=None):
        session = session or self.latest()
        if not session:
            return 70
        norm = self.personal_norm()
        median = session.get("medianReactionMs") or norm
        lapses = session.get("lapsesCount", 0)
        false_starts = session.get("falseStartsCount", 0)
        checkin = session.get("checkin", {})
        score = 82 - max(0, median - norm) * 0.12 - lapses * 4 - false_starts * 5
        score += (checkin.get("energy", 3) - 3) * 3
        score -= max(0, checkin.get("stress", 3) - 3) * 4
        score += clamp(checkin.get("sleepHours", 7) - 7, -2, 2) * 3
        return int(clamp(score, 5, 98))

    def trend(self):
        sessions = [s for s in self.store.sessions() if s.get("medianReactionMs")]
        last = sessions[-5:]
        if len(last) < 3:
            return "мало данных"
        first = statistics.mean([s["medianReactionMs"] for s in last[:2]])
        tail = statistics.mean([s["medianReactionMs"] for s in last[-2:]])
        delta = tail - first
        spread = max(s["medianReactionMs"] for s in last) - min(s["medianReactionMs"] for s in last)
        if spread > 120:
            return "скачет"
        if delta < -25:
            return "лучше"
        if delta > 25:
            return "хуже"
        return "стабильно"

    def coach(self):
        s = self.latest()
        if not s:
            return ["Сделай первый тест: 20 пиков дадут базовую норму."]
        norm = self.personal_norm()
        median = s.get("medianReactionMs") or norm
        avg = s.get("averageReactionMs") or median
        best = s.get("bestReactionMs") or median
        worst = s.get("worstReactionMs") or median
        lapses = s.get("lapsesCount", 0)
        false_starts = s.get("falseStartsCount", 0)
        checkin = s.get("checkin", {})
        tips = []
        if median <= norm - 20 and lapses <= 1:
            tips.append("Окно быстрое: можно играть задачи на первый выстрел.")
        elif median > norm + 35:
            tips.append("Реакция ниже нормы: начни с 5 минут разминки, не заходи сразу в темп.")
        else:
            tips.append("Реакция около нормы: решает стабильность, не форсируй первые пики.")
        if lapses >= 3:
            tips.append(f"{lapses} провала 500+ ms: внимание просело, лучше короткие серии.")
        if false_starts:
            tips.append(f"Фальстарты: {false_starts}. Снизь предугадывание, играй по появлению.")
        if avg - median > 60 or worst - best > 260:
            tips.append("Разброс высокий: перед рейтингом сделай еще одну контрольную серию.")
        if checkin.get("caffeine", "нет") != "нет" and lapses >= 2:
            tips.append("После кофе старт быстрее, но стабильность может плавать: держи паузы.")
        if checkin.get("sleepHours", 7) < 6.5:
            tips.append("Сон короткий: не планируй длинную сессию на концентрацию.")
        if self.trend() == "хуже":
            tips.append("Последние тесты идут вниз: это уже похоже на усталость, а не случайность.")
        return tips[:6]


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
                    r = min(w, h) * scale * i / 7
                    Ellipse(pos=(cx - r, cy - r), size=(r * 2, r * 2))
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
        col = COLORS.get(self.accent, COLORS["cyan"])
        alpha = 0.30 if self.state == "normal" else 0.55
        self.canvas.before.clear()
        with self.canvas.before:
            Color(col[0], col[1], col[2], alpha)
            RoundedRectangle(pos=self.pos, size=self.size, radius=[dp(7)])
            Color(col[0], col[1], col[2], 0.9)
            Line(rounded_rectangle=(self.x, self.y, self.width, self.height, dp(7)), width=1.2)


class ScrollScreen(ScrollView):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.do_scroll_x = False
        self.bar_width = dp(3)
        self.content = BoxLayout(orientation="vertical", padding=[dp(14), dp(14), dp(14), dp(96)], spacing=dp(12), size_hint_y=None)
        self.content.bind(minimum_height=self.content.setter("height"))
        self.add_widget(self.content)

    def add_card(self, *children, height=None):
        card = GlassCard(size_hint_y=None)
        card.height = height or dp(140)
        for child in children:
            card.add_widget(child)
        self.content.add_widget(card)
        return card


class CombatScene(Widget):
    enemy_visible = NumericProperty(0)
    scope_mode = StringProperty("rifle")
    head = ListProperty([0, 0])

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.bind(pos=self.redraw, size=self.redraw, enemy_visible=self.redraw, scope_mode=self.redraw, head=self.redraw)

    def set_enemy(self, visible, variant=0):
        w, h = max(self.width, 1), max(self.height, 1)
        base_x = self.x + w * (0.67 + [0.0, 0.035, -0.025][variant % 3])
        base_y = self.y + h * (0.48 + [0.0, 0.025, -0.015][variant % 3])
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
            Color(0.9, 0.78, 0.55, 0.22)
            for i in range(16):
                Ellipse(pos=(x + random.random() * w, y + random.random() * h), size=(dp(2), dp(2)))
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
            r = min(self.width, self.height) * 0.46
            Color(0, 0, 0, 0.45)
            Rectangle(pos=self.pos, size=self.size)
            Color(0, 0, 0, 0.72)
            Line(circle=(hx, hy, r), width=dp(4))
            Color(0.05, 0.08, 0.09, 0.64)
            Rectangle(pos=(self.x, self.y), size=(max(0, hx - r - self.x), self.height))
            Rectangle(pos=(hx + r, self.y), size=(max(0, self.right - hx - r), self.height))
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
        self.add_widget(root)
        root.bind(on_touch_down=self.handle_touch)

    def on_open(self):
        self.count_left = 10
        self._tick_countdown(0)

    def _tick_countdown(self, _):
        if self.count_left <= 0:
            self.next_attempt()
            return
        self.status.text = f"[color=ffd35a]Подготовка[/color]  {self.count_left}"
        self.count_left -= 1
        Clock.schedule_once(self._tick_countdown, 1)

    def next_attempt(self, *_):
        if self.attempt >= 20:
            self.finish()
            return
        self.state = "waiting"
        self.scene.scope_mode = "awp" if self.attempt % 3 == 2 else "rifle"
        self.scene.set_enemy(False, self.attempt)
        self.status.text = f"Попытка {self.attempt + 1}/20 · жди пик"
        self.wait_event = Clock.schedule_once(self.show_enemy, random.uniform(0.75, 2.4))

    def show_enemy(self, *_):
        self.state = "visible"
        self.peek_time = time.perf_counter()
        self.scene.set_enemy(True, self.attempt)
        self.status.text = "[color=ff4040]ПИК![/color]"

    def handle_touch(self, _, touch):
        if self.state == "countdown":
            return True
        if self.state == "waiting":
            if self.wait_event:
                self.wait_event.cancel()
            self.false_starts += 1
            self.status.text = "[color=ff4040]Фальстарт[/color]"
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

    def data_to_px(self, xval, yval):
        left, bottom, right, top = self.plot_box()
        px = left + (xval - self.x_min) / max(0.01, self.x_max - self.x_min) * (right - left)
        py = bottom + (yval - self.y_min) / max(0.01, self.y_max - self.y_min) * (top - bottom)
        return px, py

    def plot_box(self):
        return self.x + dp(48), self.y + dp(42), self.right - dp(12), self.top - dp(22)

    def set_view(self, x_min, x_max, y_min, y_max):
        min_x_span = 2.0
        min_y_span = 80.0
        x_span = clamp(x_max - x_min, min_x_span, 24)
        y_span = clamp(y_max - y_min, min_y_span, self.y_max_base - self.y_min_base)
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
        had = touch.uid in self.touches
        self.touches.pop(touch.uid, None)
        if had and not self.moved:
            self.pick_point(touch.pos)
        if not self.touches:
            self.start_span = None
        return had

    def pick_point(self, pos):
        nearest = None
        best = dp(28)
        for p in self.points:
            px, py = self.data_to_px(p["x"], p["y"])
            d = math.hypot(px - pos[0], py - pos[1])
            if d < best:
                best, nearest = d, p
        if nearest:
            self.selected_text = nearest.get("label", "")
            self.redraw()

    def ticks(self, lo, hi, target=5):
        span = hi - lo
        raw = span / target
        step = min([0.5, 1, 2, 3, 4, 6, 8, 12, 100, 200], key=lambda x: abs(x - raw))
        start = math.ceil(lo / step) * step
        vals = []
        v = start
        while v <= hi + 0.001:
            vals.append(v)
            v += step
        return vals

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
            visible.sort(key=lambda p: p["x"])
            if len(visible) > 1:
                pts = []
                for p in visible:
                    pts.extend(self.data_to_px(p["x"], p["y"]))
                Color(*COLORS["cyan"])
                Line(points=pts, width=dp(2))
            for p in visible:
                px, py = self.data_to_px(p["x"], p["y"])
                Color(*p.get("color", COLORS["gold"]))
                Ellipse(pos=(px - dp(4), py - dp(4)), size=(dp(8), dp(8)))
            Color(*COLORS["text"])
            self.label(f"X: {self.x_min:04.1f}-{self.x_max:04.1f} ч · Y: {int(self.y_min)}-{int(self.y_max)} · {24/(self.x_max-self.x_min):.1f}x / {(self.y_max_base-self.y_min_base)/(self.y_max-self.y_min):.1f}x", left, self.top - dp(18), COLORS["muted"], 11)
            if self.selected_text:
                self.label(self.selected_text, left + dp(6), top - dp(28), COLORS["text"], 12)

    def label(self, text, x, y, color, size):
        from kivy.core.text import Label as CoreLabel
        lab = CoreLabel(text=text, font_size=dp(size), color=color)
        lab.refresh()
        Rectangle(texture=lab.texture, pos=(x, y), size=lab.texture.size)


class DayChart(BaseChart):
    def draw_zones(self, left, bottom, right, top):
        for lo, hi, col, alpha in ((0, 300, COLORS["green"], 0.16), (300, 500, COLORS["gold"], 0.14), (500, 800, COLORS["red"], 0.14)):
            y1 = clamp(self.data_to_px(0, lo)[1], bottom, top)
            y2 = clamp(self.data_to_px(0, hi)[1], bottom, top)
            if y2 > y1:
                Color(col[0], col[1], col[2], alpha)
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
        mid = self.data_to_px(0, 0)[1]
        Color(*COLORS["green"][:3], 0.14)
        Rectangle(pos=(left, bottom), size=(right - left, max(0, mid - bottom)))
        Color(*COLORS["red"][:3], 0.14)
        Rectangle(pos=(left, mid), size=(right - left, max(0, top - mid)))
        if bottom <= mid <= top:
            Color(1, 1, 1, 0.72)
            Line(points=[left, mid, right, mid], width=1.3)


class TestScreen(ScrollScreen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.result_label = FitLabel(text="", font_size="15sp", size_hint_y=None)
        self.note = TextInput(hint_text="Заметка после теста", size_hint_y=None, height=dp(80), multiline=True, background_color=(0.08, 0.1, 0.14, 1), foreground_color=COLORS["text"])
        self.tags = TextInput(hint_text="Теги через запятую", size_hint_y=None, height=dp(44), multiline=False, background_color=(0.08, 0.1, 0.14, 1), foreground_color=COLORS["text"])
        self.sleep = Slider(min=3, max=10, value=7, step=0.5, size_hint_y=None, height=dp(36))
        self.stress = Slider(min=1, max=5, value=3, step=1, size_hint_y=None, height=dp(36))
        self.energy = Slider(min=1, max=5, value=3, step=1, size_hint_y=None, height=dp(36))
        self.caffeine = TextInput(text="нет", hint_text="кофеин: нет / кофе / энергетик", size_hint_y=None, height=dp(44), multiline=False, background_color=(0.08, 0.1, 0.14, 1), foreground_color=COLORS["text"])
        self.last_reactions = []
        self.last_false = 0
        self.build()

    def build(self):
        title = FitLabel(text="[b]Reaction Tracker[/b]\n[color=8fdcff]Dust2 Long · 20 пиков · readiness[/color]", font_size="24sp", size_hint_y=None)
        start = SportButton(text="СТАРТ ТЕСТА", accent="gold")
        start.bind(on_release=lambda *_: CombatOverlay(self.on_test_finish).open())
        self.add_card(title, start, height=dp(150))
        self.add_card(FitLabel(text="[b]Чек-ин[/b]", font_size="18sp", size_hint_y=None),
                      FitLabel(text="Сон", color=COLORS["muted"], size_hint_y=None), self.sleep,
                      FitLabel(text="Стресс", color=COLORS["muted"], size_hint_y=None), self.stress,
                      FitLabel(text="Энергия", color=COLORS["muted"], size_hint_y=None), self.energy,
                      self.caffeine, height=dp(270))
        save = SportButton(text="СОХРАНИТЬ РЕЗУЛЬТАТ", accent="cyan")
        save.bind(on_release=self.save_result)
        self.add_card(self.result_label, self.note, self.tags, save, height=dp(270))
        self.refresh()

    def refresh(self):
        app = App.get_running_app()
        latest = app.analytics.latest()
        if latest:
            self.result_label.text = (
                f"[b]Последний тест[/b]\n"
                f"Median {fmt_ms(latest.get('medianReactionMs'))} · Avg {fmt_ms(latest.get('averageReactionMs'))} · "
                f"Best {fmt_ms(latest.get('bestReactionMs'))}\n"
                f"Readiness: [color=ffd35a]{app.analytics.readiness_score(latest)}[/color] · Тренд: {app.analytics.trend()}\n"
                + "\n".join([f"• {t}" for t in app.analytics.coach()])
            )
        else:
            self.result_label.text = "Результатов пока нет."

    def on_test_finish(self, reactions, false_starts):
        self.last_reactions = reactions
        self.last_false = false_starts
        if reactions:
            self.result_label.text = f"[b]Новый результат[/b]\nMedian {fmt_ms(statistics.median(reactions))} · Avg {fmt_ms(sum(reactions)/len(reactions))} · Best {fmt_ms(min(reactions))}\nФальстарты: {false_starts}\nДобавь заметку и сохрани."
        else:
            self.result_label.text = f"[b]Тест без засчитанных пиков[/b]\nФальстарты: {false_starts}. Повтори серию."

    def save_result(self, *_):
        if not self.last_reactions:
            self.result_label.text = "Сначала пройди тест: нужны засчитанные пики."
            return
        app = App.get_running_app()
        app.store.add_session(self.last_reactions, self.last_false, self.note.text, self.tags.text, {
            "sleepHours": self.sleep.value,
            "caffeine": self.caffeine.text.strip() or "нет",
            "stress": int(self.stress.value),
            "energy": int(self.energy.value),
        })
        self.last_reactions = []
        self.note.text = ""
        self.tags.text = ""
        app.refresh_all()
        self.refresh()


class ChartScreen(ScrollScreen):
    chart_cls = DayChart
    title = ""
    legend = ""

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.chart = self.chart_cls(size_hint_y=None, height=dp(360))
        header = FitLabel(text=self.title, font_size="20sp", size_hint_y=None)
        self.add_card(header, self.chart, height=dp(430))
        row = GridLayout(cols=3, spacing=dp(8), size_hint_y=None, height=dp(48))
        for text, action in (("-", lambda *_: self.chart.zoom(0.75, 0.75)), ("Сброс", lambda *_: self.chart.reset()), ("+", lambda *_: self.chart.zoom(1.35, 1.35))):
            btn = SportButton(text=text, accent="cyan")
            btn.bind(on_release=action)
            row.add_widget(btn)
        self.add_card(FitLabel(text=self.legend, font_size="13sp", size_hint_y=None), row, height=dp(120))

    def refresh(self):
        app = App.get_running_app()
        norm = app.analytics.personal_norm()
        pts = []
        today = datetime.now().date()
        for s in app.store.sessions():
            dt = session_time(s)
            if dt.date() != today:
                continue
            y = self.point_y(s, norm)
            if y is None:
                continue
            pts.append({"x": dt.hour + dt.minute / 60, "y": y, "label": f"{dt:%H:%M} · {fmt_ms(s.get('medianReactionMs'))}", "color": self.point_color(y)})
        if not pts:
            for s in app.store.sessions()[-12:]:
                dt = session_time(s)
                y = self.point_y(s, norm)
                if y is not None:
                    pts.append({"x": dt.hour + dt.minute / 60, "y": y, "label": f"{dt:%d.%m %H:%M} · {fmt_ms(s.get('medianReactionMs'))}", "color": self.point_color(y)})
        self.chart.points = pts
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
    title = "[b]День[/b]\n[color=8fdcff]0-24 часа · реакция в миллисекундах[/color]"
    legend = "0-300 быстро · 300-500 средне · 500+ провал · фиолетовая линия = личная норма"


class NormScreen(ChartScreen):
    chart_cls = NormChart
    title = "[b]Норма[/b]\n[color=8fdcff]Отклонение от личной нормы[/color]"
    legend = "Зеленый = лучше нормы · синий = около нормы · красный = хуже · белая линия = 0"

    def point_y(self, session, norm):
        val = session.get("medianReactionMs")
        return None if val is None else val - norm

    def point_color(self, y):
        if y < -25:
            return COLORS["green"]
        if y <= 25:
            return COLORS["cyan"]
        return COLORS["red"]


class DaysScreen(ScrollScreen):
    def refresh(self):
        self.content.clear_widgets()
        app = App.get_running_app()
        for s in reversed(app.store.sessions()[-30:]):
            dt = session_time(s)
            txt = (
                f"[b]{dt:%d.%m.%Y %H:%M}[/b]   Readiness [color=ffd35a]{app.analytics.readiness_score(s)}[/color]\n"
                f"Median {fmt_ms(s.get('medianReactionMs'))} · Avg {fmt_ms(s.get('averageReactionMs'))} · "
                f"Best {fmt_ms(s.get('bestReactionMs'))} · Lapses {s.get('lapsesCount', 0)} · FS {s.get('falseStartsCount', 0)}\n"
                f"Теги: {', '.join(s.get('tags') or ['-'])}\n{s.get('noteText') or ''}"
            )
            self.add_card(FitLabel(text=txt, font_size="14sp", size_hint_y=None), height=dp(132))


class WeatherScreen(ScrollScreen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.info = FitLabel(text="Погода не загружена.", font_size="16sp", size_hint_y=None)
        btn = SportButton(text="ЗАГРУЗИТЬ OPEN-METEO", accent="cyan")
        btn.bind(on_release=self.load_weather)
        self.add_card(FitLabel(text="[b]Погода[/b]\n[color=8fdcff]Open-Meteo · Москва по умолчанию[/color]", font_size="20sp", size_hint_y=None), self.info, btn, height=dp(250))

    def load_weather(self, *_):
        self.info.text = "Загрузка..."
        Clock.schedule_once(lambda __: self.fetch(), 0.1)

    def fetch(self):
        url = "https://api.open-meteo.com/v1/forecast?latitude=55.75&longitude=37.62&current=temperature_2m,relative_humidity_2m,wind_speed_10m"
        try:
            with urllib.request.urlopen(url, timeout=8) as r:
                data = json.loads(r.read().decode("utf-8"))
            cur = data.get("current", {})
            self.info.text = (
                f"[b]Сейчас[/b]\n"
                f"Температура: {cur.get('temperature_2m', '-')} C\n"
                f"Влажность: {cur.get('relative_humidity_2m', '-')}%\n"
                f"Ветер: {cur.get('wind_speed_10m', '-')} км/ч\n"
                "Если интернет недоступен, остальные экраны работают локально."
            )
        except (urllib.error.URLError, TimeoutError, ValueError, OSError) as e:
            self.info.text = f"Погода не загрузилась: {e}\nПриложение продолжает работать офлайн."


class Root(FloatLayout):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.bg = SportBackground()
        self.add_widget(self.bg)
        self.body = FloatLayout(size_hint=(1, 1))
        self.add_widget(self.body)
        self.screens = {
            "Тест": TestScreen(),
            "День": DayScreen(),
            "Норма": NormScreen(),
            "История": DaysScreen(),
            "Погода": WeatherScreen(),
        }
        self.current = None
        self.nav = GridLayout(cols=5, spacing=dp(4), padding=dp(6), size_hint=(1, None), height=dp(70), pos_hint={"x": 0, "y": 0})
        self.buttons = {}
        for name in self.screens:
            btn = SportButton(text=name, accent="gold" if name == "Тест" else "cyan", height=dp(52))
            btn.bind(on_release=lambda _, n=name: self.show(n))
            self.buttons[name] = btn
            self.nav.add_widget(btn)
        self.add_widget(self.nav)
        self.show("Тест")

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
        for key, btn in self.buttons.items():
            btn.accent = "gold" if key == name and key == "Тест" else ("cyan" if key == name else "blue")
            btn.redraw()

    def refresh_all(self):
        for screen in self.screens.values():
            if hasattr(screen, "refresh"):
                screen.refresh()


class ReactionTrackerApp(App):
    def build(self):
        self.title = "Reaction Tracker"
        self.store = DataStore()
        self.analytics = Analytics(self.store)
        self.root_widget = Root()
        return self.root_widget

    def refresh_all(self):
        self.root_widget.refresh_all()


if __name__ == "__main__":
    ReactionTrackerApp().run()
