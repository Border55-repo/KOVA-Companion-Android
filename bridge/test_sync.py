import tempfile
import unittest
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from push import topic_for
from reliability import (
    already_sent_ids,
    change_id,
    change_records,
    enqueue_pending,
    load_push_state,
    mark_sent,
    pending_records,
    suspicious_snapshot,
)
from sync import compute_diff, parse_schedule


def event(
    event_id="e1",
    date="2026-09-22",
    time="18:30",
    type_name="Korpskveld",
    description="Øvelse",
):
    return {
        "id": event_id,
        "dateIso": date,
        "dateLabel": "tir. 22.9",
        "time": time,
        "type": type_name,
        "description": description,
        "sourceUrl": "https://example.invalid",
    }


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

    def test_preserves_untimed_and_end_time_rows(self):
        html = """
        <table>
          <tr><td>tir. 22.9</td><td>Aktivitet</td><td>Materiellkontroll</td></tr>
          <tr><td>ons. 23.9</td><td>-> 17:00</td><td>Aktivitet</td><td>ATV-kurs</td></tr>
        </table>
        """
        events = parse_schedule(
            html,
            "https://example.invalid",
            datetime(2026, 9, 22, tzinfo=ZoneInfo("Europe/Oslo")),
        )
        self.assertEqual(2, len(events))
        self.assertEqual("", events[0]["time"])
        self.assertEqual("-> 17:00", events[1]["time"])

    def test_time_marker_does_not_create_false_change(self):
        old = [event(time="18:30")]
        new = [event(event_id="e2", time="-> 18:30")]
        diff = compute_diff(old, new)
        self.assertEqual(0, len(diff["changed"]))

    def test_detects_time_change_without_new_removed_noise(self):
        old = [event(event_id="old")]
        new = [event(event_id="new", time="19:00")]
        diff = compute_diff(old, new)
        self.assertEqual(0, len(diff["added"]))
        self.assertEqual(0, len(diff["removed"]))
        self.assertEqual(1, len(diff["changed"]))

    def test_topic_names_match_android(self):
        self.assertEqual("kova_ullensakerrkh", topic_for("UllensakerRKH"))
        self.assertEqual("kova_nittedal_rkh", topic_for("Nittedal RKH"))
        self.assertEqual("kova_skedsmo_rkh", topic_for("Skedsmo RKH"))


class ReliabilityTests(unittest.TestCase):
    def test_change_id_is_stable(self):
        item = event()
        a = change_id("UllensakerRKH", "added", item)
        b = change_id("UllensakerRKH", "added", dict(item))
        self.assertEqual(a, b)
        self.assertEqual(24, len(a))

    def test_changed_fingerprint_changes_when_time_changes(self):
        old = event(time="18:30")
        new = event(event_id="e2", time="19:00")
        first = change_id("UllensakerRKH", "changed", new, old)
        second = change_id(
            "UllensakerRKH",
            "changed",
            event(event_id="e3", time="19:30"),
            old,
        )
        self.assertNotEqual(first, second)

    def test_pending_queue_deduplicates_and_marks_sent(self):
        org = {"name": "Ullensaker", "code": "UllensakerRKH"}
        diff = {"added": [event()], "changed": [], "removed": []}
        records = change_records(org, diff)

        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "push_state.json"
            state = load_push_state(path)

            state, changed = enqueue_pending(path, state, records)
            self.assertTrue(changed)
            self.assertEqual(1, len(pending_records(state)))

            state, changed_again = enqueue_pending(path, state, records)
            self.assertFalse(changed_again)
            self.assertEqual(1, len(pending_records(state)))

            item_id = records[0]["changeId"]
            self.assertTrue(mark_sent(path, state, [item_id]))
            final = load_push_state(path)
            self.assertEqual(0, len(pending_records(final)))
            self.assertIn(item_id, already_sent_ids(final))

    def test_rejects_empty_snapshot_when_old_snapshot_is_populated(self):
        warning = suspicious_snapshot(old_count=42, new_count=0, html_length=5000)
        self.assertIsNotNone(warning)

    def test_allows_normal_small_change(self):
        warning = suspicious_snapshot(old_count=42, new_count=43, html_length=5000)
        self.assertIsNone(warning)


if __name__ == "__main__":
    unittest.main()
