from kivy.app import App
from kivy.metrics import dp

from reaction_tracker.core.utils import fmt_ms, session_time
from reaction_tracker.ui.widgets import FitLabel, ScrollScreen


class DaysScreen(ScrollScreen):
    def refresh(self):
        self.content.clear_widgets()
        app = App.get_running_app()
        for session in reversed(app.store.sessions()[-30:]):
            dt = session_time(session)
            text = (
                f"[b]{dt:%d.%m.%Y %H:%M}[/b]   Readiness [color=ffd35a]{app.analytics.readiness_score(session)}[/color]\n"
                f"Median {fmt_ms(session.get('medianReactionMs'))} | Avg {fmt_ms(session.get('averageReactionMs'))} | "
                f"Best {fmt_ms(session.get('bestReactionMs'))} | Lapses {session.get('lapsesCount', 0)} | FS {session.get('falseStartsCount', 0)}\n"
                f"Tags: {', '.join(session.get('tags') or ['-'])}\n{session.get('noteText') or ''}"
            )
            self.add_card(FitLabel(text=text, font_size="14sp", size_hint_y=None), height=dp(132))
