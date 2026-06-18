import json
import os
import random
import statistics
from datetime import datetime, timedelta

from reaction_tracker.core.config import ATTEMPTS_PER_TEST, DATA_FILE
from reaction_tracker.core.utils import clamp, now_ts


class DataStore:
    def __init__(self, user_data_dir):
        self.path = os.path.join(user_data_dir, DATA_FILE)
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

    def normalize_session(self, session):
        reactions = [
            int(x)
            for x in session.get("reactions", [])
            if isinstance(x, (int, float)) and x >= 0
        ]
        median = session.get("medianReactionMs")
        average = session.get("averageReactionMs")
        best = session.get("bestReactionMs")
        worst = session.get("worstReactionMs")
        if reactions:
            median = median if median is not None else statistics.median(reactions)
            average = average if average is not None else sum(reactions) / len(reactions)
            best = best if best is not None else min(reactions)
            worst = worst if worst is not None else max(reactions)
        checkin = session.get("checkin", {})
        return {
            "id": str(session.get("id") or f"s{now_ts()}{random.randint(100, 999)}"),
            "timestamp": int(session.get("timestamp") or now_ts()),
            "medianReactionMs": median,
            "averageReactionMs": average,
            "bestReactionMs": best,
            "worstReactionMs": worst,
            "lapsesCount": int(session.get("lapsesCount") or len([x for x in reactions if x >= 500])),
            "falseStartsCount": int(session.get("falseStartsCount") or 0),
            "attemptsCount": int(session.get("attemptsCount") or max(ATTEMPTS_PER_TEST, len(reactions))),
            "reactions": reactions,
            "noteText": str(session.get("noteText") or ""),
            "tags": list(session.get("tags") or []),
            "checkin": {
                "sleepHours": float(checkin.get("sleepHours", 7)),
                "caffeine": str(checkin.get("caffeine", "none")),
                "stress": int(checkin.get("stress", 3)),
                "energy": int(checkin.get("energy", 3)),
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
            "attemptsCount": ATTEMPTS_PER_TEST,
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
        tag_sets = [["warmup"], ["coffee"], ["ranked"], ["tired"], ["focus"]]
        notes = ["Clean series", "Late duels", "After warmup", ""]
        for day in range(5, -1, -1):
            for slot in (0, 5, 10):
                dt = base - timedelta(days=day) + timedelta(hours=slot, minutes=random.randint(-25, 25))
                center = 330 + day * 10 - slot * 8 + random.randint(-35, 45)
                reactions = [clamp(random.gauss(center, 55), 165, 720) for _ in range(ATTEMPTS_PER_TEST)]
                self.data["sessions"].append(self.normalize_session({
                    "id": f"demo{day}{slot}",
                    "timestamp": int(dt.timestamp()),
                    "reactions": reactions,
                    "falseStartsCount": random.choice([0, 0, 1, 2]),
                    "noteText": random.choice(notes),
                    "tags": random.choice(tag_sets),
                    "checkin": {
                        "sleepHours": random.choice([6, 6.5, 7, 8]),
                        "caffeine": random.choice(["none", "coffee", "energy"]),
                        "stress": random.randint(1, 5),
                        "energy": random.randint(2, 5),
                    },
                }))

    def sessions(self):
        return list(self.data.get("sessions", []))
