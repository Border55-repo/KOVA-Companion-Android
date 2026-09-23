import unittest
from datetime import datetime
from zoneinfo import ZoneInfo

from webpush import _semantic_key, reminder_due

OSLO = ZoneInfo("Europe/Oslo")


class ReminderTests(unittest.TestCase):
    def setUp(self):
        self.event = {
            "id": "event-1",
            "dateIso": "2026-09-24",
            "dateLabel": "24.09.2026",
            "time": "18:00",
            "type": "Sanitetsvakt",
            "description": "  Åpen   dag ",
        }

    def test_semantic_key_ignores_case_and_extra_space(self):
        self.assertEqual(
            _semantic_key(self.event),
            "sanitetsvakt|åpen dag",
        )

    def test_reminder_is_due_inside_bridge_grace_window(self):
        now = datetime(2026, 9, 24, 16, 0, tzinfo=OSLO)
        self.assertTrue(reminder_due(now, self.event, 120))

    def test_reminder_is_not_due_too_early(self):
        now = datetime(2026, 9, 24, 15, 39, tzinfo=OSLO)
        self.assertFalse(reminder_due(now, self.event, 120))

    def test_reminder_is_not_sent_late_after_grace_window(self):
        now = datetime(2026, 9, 24, 16, 21, tzinfo=OSLO)
        self.assertFalse(reminder_due(now, self.event, 120))

    def test_reminder_requires_event_time(self):
        event = dict(self.event)
        event["time"] = ""
        now = datetime(2026, 9, 24, 16, 0, tzinfo=OSLO)
        self.assertFalse(reminder_due(now, event, 120))


if __name__ == "__main__":
    unittest.main()
