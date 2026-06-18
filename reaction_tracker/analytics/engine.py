import statistics

from reaction_tracker.core.utils import clamp


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
        checkin = session.get("checkin", {})
        score = 82
        score -= max(0, median - norm) * 0.12
        score -= session.get("lapsesCount", 0) * 4
        score -= session.get("falseStartsCount", 0) * 5
        score += (checkin.get("energy", 3) - 3) * 3
        score -= max(0, checkin.get("stress", 3) - 3) * 4
        score += clamp(checkin.get("sleepHours", 7) - 7, -2, 2) * 3
        return int(clamp(score, 5, 98))

    def trend(self):
        sessions = [s for s in self.store.sessions() if s.get("medianReactionMs")]
        last = sessions[-5:]
        if len(last) < 3:
            return "not enough data"
        first = statistics.mean([s["medianReactionMs"] for s in last[:2]])
        tail = statistics.mean([s["medianReactionMs"] for s in last[-2:]])
        delta = tail - first
        spread = max(s["medianReactionMs"] for s in last) - min(s["medianReactionMs"] for s in last)
        if spread > 120:
            return "volatile"
        if delta < -25:
            return "improving"
        if delta > 25:
            return "worse"
        return "stable"

    def coach(self):
        session = self.latest()
        if not session:
            return ["Run the first 20-peek test to build a baseline."]
        norm = self.personal_norm()
        median = session.get("medianReactionMs") or norm
        avg = session.get("averageReactionMs") or median
        best = session.get("bestReactionMs") or median
        worst = session.get("worstReactionMs") or median
        lapses = session.get("lapsesCount", 0)
        false_starts = session.get("falseStartsCount", 0)
        checkin = session.get("checkin", {})
        tips = []
        if median <= norm - 20 and lapses <= 1:
            tips.append("Fast window: first-shot tasks are reasonable right now.")
        elif median > norm + 35:
            tips.append("Reaction is below baseline: warm up for 5 minutes before ranked pace.")
        else:
            tips.append("Reaction is near baseline: stability matters more than forcing speed.")
        if lapses >= 3:
            tips.append(f"{lapses} lapses at 500+ ms: keep the next set short.")
        if false_starts:
            tips.append(f"{false_starts} false starts: wait for the visual cue, do not prefire.")
        if avg - median > 60 or worst - best > 260:
            tips.append("High spread: repeat one control set before a long session.")
        if checkin.get("caffeine", "none") != "none" and lapses >= 2:
            tips.append("Caffeine may boost starts while hurting consistency; add pauses.")
        if checkin.get("sleepHours", 7) < 6.5:
            tips.append("Short sleep: avoid long focus blocks.")
        if self.trend() == "worse":
            tips.append("Recent tests are sliding down; treat it as fatigue, not noise.")
        return tips[:6]
