from kivy.app import App
from kivy.core.window import Window

from reaction_tracker.analytics.engine import Analytics
from reaction_tracker.core.config import COLORS
from reaction_tracker.data.store import DataStore
from reaction_tracker.ui.root import Root


class ReactionTrackerApp(App):
    def build(self):
        Window.clearcolor = COLORS["bg"]
        self.title = "Reaction Tracker"
        self.store = DataStore(self.user_data_dir)
        self.analytics = Analytics(self.store)
        self.root_widget = Root()
        return self.root_widget

    def refresh_all(self):
        self.root_widget.refresh_all()
