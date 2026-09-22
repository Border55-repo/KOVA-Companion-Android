import unittest
from datetime import datetime
from zoneinfo import ZoneInfo

from push import topic_for
from sync import compute_diff, parse_schedule


class BridgeParserTests(unittest.TestCase):
    def test_parses_two_rows(self):
        html = """
        <table>
          <tr><td>sep</td><td>39</td><td>tir. 22.9</td><td>18:30</td><td>Korpskveld</td><td>Øvelse med EHRKH</td></tr>
          <tr><td></td><td></td><td>ons. 23.9</td><td>18:00</td><td>Rådsmøte</td><td>Lokalrådsmøte</td></tr>
        </table>
        """
        events = parse_schedule(
            html,
            "https://example.invalid",
            datetime(2026, 9, 22, tzinfo=ZoneInfo("Europe/Oslo")),
        )
        self.assertEqual(2, len(events))
        self.assertEqual("2026-09-22", events[0]["dateIso"])
        self.assertEqual("18:30", events[0]["time"])

    def test_detects_time_change_without_new_removed_noise(self):
        old = [{
            "id": "old",
            "dateIso": "2026-09-22",
            "dateLabel": "tir. 22.9",
            "time": "18:30",
            "type": "Korpskveld",
            "description": "Øvelse",
            "sourceUrl": "x",
        }]
        new = [{
            "id": "new",
            "dateIso": "2026-09-22",
            "dateLabel": "tir. 22.9",
            "time": "19:00",
            "type": "Korpskveld",
            "description": "Øvelse",
            "sourceUrl": "x",
        }]
        diff = compute_diff(old, new)
        self.assertEqual(0, len(diff["added"]))
        self.assertEqual(0, len(diff["removed"]))
        self.assertEqual(1, len(diff["changed"]))

    def test_topic_names_match_android(self):
        self.assertEqual("kova_ullensakerrkh", topic_for("UllensakerRKH"))
        self.assertEqual("kova_nittedal_rkh", topic_for("Nittedal RKH"))
        self.assertEqual("kova_skedsmo_rkh", topic_for("Skedsmo RKH"))


if __name__ == "__main__":
    unittest.main()
