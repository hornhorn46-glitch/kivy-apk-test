import statistics

from kivy.app import App
from kivy.metrics import dp
from kivy.uix.slider import Slider
from kivy.uix.textinput import TextInput

from reaction_tracker.core.utils import fmt_ms
from reaction_tracker.ui.combat import CombatOverlay
from reaction_tracker.ui.widgets import FitLabel, ScrollScreen, SportButton
from reaction_tracker.core.config import COLORS


class TestScreen(ScrollScreen):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.result_label = FitLabel(text="", font_size="15sp", size_hint_y=None)
        self.note = TextInput(hint_text="Post-test note", size_hint_y=None, height=dp(80), multiline=True, background_color=(0.08, 0.1, 0.14, 1), foreground_color=COLORS["text"])
        self.tags = TextInput(hint_text="Tags, comma separated", size_hint_y=None, height=dp(44), multiline=False, background_color=(0.08, 0.1, 0.14, 1), foreground_color=COLORS["text"])
        self.sleep = Slider(min=3, max=10, value=7, step=0.5, size_hint_y=None, height=dp(36))
        self.stress = Slider(min=1, max=5, value=3, step=1, size_hint_y=None, height=dp(36))
        self.energy = Slider(min=1, max=5, value=3, step=1, size_hint_y=None, height=dp(36))
        self.caffeine = TextInput(text="none", hint_text="caffeine: none / coffee / energy", size_hint_y=None, height=dp(44), multiline=False, background_color=(0.08, 0.1, 0.14, 1), foreground_color=COLORS["text"])
        self.last_reactions = []
        self.last_false = 0
        self.build_screen()

    def build_screen(self):
        title = FitLabel(text="[b]Reaction Tracker[/b]\n[color=8fdcff]Dust2 Long | 20 peeks | readiness[/color]", font_size="24sp", size_hint_y=None)
        start = SportButton(text="START TEST", accent="gold")
        start.bind(on_release=lambda *_: CombatOverlay(self.on_test_finish).open())
        self.add_card(title, start, height=dp(150))
        self.add_card(
            FitLabel(text="[b]Check-in[/b]", font_size="18sp", size_hint_y=None),
            FitLabel(text="Sleep", color=COLORS["muted"], size_hint_y=None),
            self.sleep,
            FitLabel(text="Stress", color=COLORS["muted"], size_hint_y=None),
            self.stress,
            FitLabel(text="Energy", color=COLORS["muted"], size_hint_y=None),
            self.energy,
            self.caffeine,
            height=dp(270),
        )
        save = SportButton(text="SAVE RESULT", accent="cyan")
        save.bind(on_release=self.save_result)
        self.add_card(self.result_label, self.note, self.tags, save, height=dp(270))
        self.refresh()

    def refresh(self):
        app = App.get_running_app()
        latest = app.analytics.latest()
        if latest:
            tips = "\n".join([f"* {tip}" for tip in app.analytics.coach()])
            self.result_label.text = (
                f"[b]Latest test[/b]\n"
                f"Median {fmt_ms(latest.get('medianReactionMs'))} | Avg {fmt_ms(latest.get('averageReactionMs'))} | "
                f"Best {fmt_ms(latest.get('bestReactionMs'))}\n"
                f"Readiness: [color=ffd35a]{app.analytics.readiness_score(latest)}[/color] | Trend: {app.analytics.trend()}\n"
                f"{tips}"
            )
        else:
            self.result_label.text = "No results yet."

    def on_test_finish(self, reactions, false_starts):
        self.last_reactions = reactions
        self.last_false = false_starts
        if reactions:
            self.result_label.text = (
                f"[b]New result[/b]\n"
                f"Median {fmt_ms(statistics.median(reactions))} | Avg {fmt_ms(sum(reactions) / len(reactions))} | "
                f"Best {fmt_ms(min(reactions))}\n"
                f"False starts: {false_starts}\nAdd a note and save."
            )
        else:
            self.result_label.text = f"[b]No valid peeks[/b]\nFalse starts: {false_starts}. Repeat the set."

    def save_result(self, *_):
        if not self.last_reactions:
            self.result_label.text = "Run a test first; valid peeks are required."
            return
        app = App.get_running_app()
        app.store.add_session(self.last_reactions, self.last_false, self.note.text, self.tags.text, {
            "sleepHours": self.sleep.value,
            "caffeine": self.caffeine.text.strip() or "none",
            "stress": int(self.stress.value),
            "energy": int(self.energy.value),
        })
        self.last_reactions = []
        self.note.text = ""
        self.tags.text = ""
        app.refresh_all()
        self.refresh()
